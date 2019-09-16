package no.sb1.troxy.util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import no.sb1.troxy.Troxy;
import no.sb1.troxy.common.Config;
import no.sb1.troxy.common.Mode;
import no.sb1.troxy.http.common.Filter;
import no.sb1.troxy.http.common.Request;
import no.sb1.troxy.http.common.Response;
import no.sb1.troxy.record.v3.Recording;
import no.sb1.troxy.record.v3.RequestPattern;
import no.sb1.troxy.record.v3.ResponseTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 * A handler for incoming requests.
 */
public class SimulatorHandler extends AbstractHandler {
    private static final Logger log = LoggerFactory.getLogger(SimulatorHandler.class);
    private static final Logger simLog = LoggerFactory.getLogger("simulator");

    private final Troxy troxy;
    private final Config config;
    private final TroxyFileHandler troxyFileHandler;
    private final Cache cache;

    /*
     * Set up ignoring certificates and not verifying hostnames.
     */
    static {
        /* ignore verifying hostname */
        HttpsURLConnection.setDefaultHostnameVerifier((urlHostName, session) -> true);

        /* set up a trust manager that validates any certificate */
        TrustManager[] trustAllCerts = new TrustManager[1];
        trustAllCerts[0] = new NoTrustManager();
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.warn("Unable to set up SSLContext, HTTPS will not work", e);
        }
    }

    public SimulatorHandler(final Troxy troxy, final Config config, final TroxyFileHandler troxyFileHandler, Cache cache) {
        this.troxy = troxy;
        this.config = config;
        this.troxyFileHandler = troxyFileHandler;
        this.cache = cache;
    }

    /**
     * Handle an incoming request.
     *
     * @param target {@inheritDoc}
     * @param jettyRequest {@inheritDoc}
     * @param servletRequest {@inheritDoc}
     * @param servletResponse {@inheritDoc}
     * @throws IOException {@inheritDoc}
     * @throws ServletException {@inheritDoc}
     */
    @Override
    public void handle(String target, org.eclipse.jetty.server.Request jettyRequest, HttpServletRequest servletRequest, HttpServletResponse servletResponse) throws IOException, ServletException {
        simLog.info("Received request: {}", servletRequest);
        /* handle request */
        Request request = new Request(servletRequest, System.currentTimeMillis());
        Response remoteResponse = null;

        /* instantiate filters */
        List<Filter> filters = new ArrayList<>();
        for (Class<Filter> filterClass : troxy.getFilterClasses()) {
            try {
                filters.add(filterClass.newInstance());
            } catch (InstantiationException e) {
                log.warn("Unable to instantiate filter \"{}\"", filterClass.getName(), e);
            } catch (IllegalAccessException e) {
                log.warn("Unable to access filter \"{}\"", filterClass.getName(), e);
            }
        }

        /* run filters "filterClientRequest()" on the request */
        for (Filter filter : filters)
            filter.doFilterRequest(request, false);

        /* find response in cache */
        Mode mode = troxy.getMode();
        List<Cache.Result> cacheResults = new ArrayList<>();
        if (mode == Mode.PLAYBACK || mode == Mode.PLAYBACK_OR_RECORD || mode == Mode.PLAYBACK_OR_PASSTHROUGH)
            cacheResults = cache.searchCache(request);

        /* connect to remote server */
        boolean unableToReachHost = false;
        if (mode == Mode.PASSTHROUGH || mode == Mode.RECORD || ((mode == Mode.PLAYBACK_OR_RECORD || mode == Mode.PLAYBACK_OR_PASSTHROUGH) && cacheResults.isEmpty())) {
            /* run filters "filterServerRequest()" on the request */
            for (Filter filter : filters)
                filter.doFilterRequest(request, true);

            HttpURLConnection con = null;
            Response response = null;
            try {
                /* set up connection to host */
                con = connectToHost(request);

                /* handle response */
                response = new Response(con);
                if (mode == Mode.RECORD || mode == Mode.PLAYBACK_OR_RECORD) {
                    /* save respons and return the original during RECORD */
                    remoteResponse = response;
                }
                simLog.info("Response received from remote host: {}", response);
                simLog.debug("Response header: {}", response.getHeader());
                simLog.debug("Response content: {}", response.getContent());
            } catch (Exception e) {
                simLog.warn("Unable to connect to host", e);
                unableToReachHost = true;
            } finally {
                /* disconnect from host */
                if (con != null)
                    con.disconnect();
            }

            if (response != null) {
                /* run filters "filterServerResponse()" on the response before saving to cache */
                for (Filter filter : filters)
                    filter.doFilterResponse(response, true);

                RequestPattern requestPattern = new RequestPattern(request);
                ResponseTemplate responseTemplate = new ResponseTemplate(response);
                Recording recording = new Recording(requestPattern, responseTemplate);
                cacheResults.add(new Cache.Result(recording, new HashMap<>()));

                /* save recording if in a record mode */
                if (mode == Mode.RECORD || mode == Mode.PLAYBACK_OR_RECORD) {
                    /* filter new recording */
                    for (Filter filter : filters)
                        filter.doFilterRecording(recording);
                    /* if in record mode, see if we already have an identical request, if so, add response (unless it's identical to last response in recording) */
                    if (mode == Mode.RECORD) {
                        for (Cache.Result cacheResult : cache.searchCache(request)) {
                            if (cacheResult.getRecording().getRequestPattern().equals(requestPattern)) {
                                // existing recording match request, add response
                                recording = cacheResult.getRecording();
                                List<ResponseTemplate> responseTemplates = recording.getResponseTemplates();
                                ResponseTemplate lastResponseTemplate = responseTemplates.isEmpty() ? null : responseTemplates.get(responseTemplates.size() - 1);
                                if (lastResponseTemplate == null || !lastResponseTemplate.equals(responseTemplate)) {
                                    simLog.info("Adding new response to existing recording");
                                    recording.addResponse(responseTemplate);
                                } else {
                                    simLog.info("Increasing weight of identical response in existing recording");
                                    lastResponseTemplate.setWeight(lastResponseTemplate.getWeight() + 1);
                                }
                                break;
                            }
                        }
                    }

                    /* find filename for Recording */
                    if (recording.getFilename() == null) {
                        Request originalRequest = requestPattern.getOriginalRequest();
                        String directory = originalRequest.getHost().replaceAll("[^\\w.-]", "_").trim().replaceAll("^_+", "");
                        String filename = originalRequest.getPath().replaceAll("[^\\w.-]", "_").trim().replaceAll("^_+", "");
                        if (!"".equals(filename))
                            filename = filename + '.'; // add '.' before counter only when a path was given
                        for (int count = 0; ; ++count) {
                            Path path = Paths.get(directory, filename + (count < 100 ? count < 10 ? "00" : "0" : "") + count + ".troxy");
                            if (!troxyFileHandler.fileExists(path.toString())) {
                                recording.setFilename(path.toString().replace("\\","/"));
                                break;
                            }
                        }
                    }

                    troxyFileHandler.saveRecording(recording);
                    cache.addRecoding(recording);
                }
            }
        }

        Response response;
        if (unableToReachHost) {
            response = createTroxyErrorResponse("Unable to connect to host");
        } else if (remoteResponse != null) {
            response = remoteResponse;
        } else if (cacheResults.isEmpty()) {
            String msg = "No recording matching request found in cache for request: {}";
            simLog.info(msg);
            response = createTroxyErrorResponse(msg);
        } else if (cacheResults.size() > 1 && !Boolean.parseBoolean(config.getValue("troxy.allow_multiple_matching_recordings", "false"))) {
            String msg = String.format("Multiple recordings match request: %s", cacheResults.stream().map(result -> result.getRecording().getFilename()).collect(Collectors.joining(", ")));
            simLog.warn(msg);
            response = createTroxyErrorResponse(msg);
        } else {
            Cache.Result result;
            if (cacheResults.size() > 1) {
                simLog.info("Returning the assumed most unique recording of multiple matching recordings: {}", cacheResults.stream().map(tmpResult -> tmpResult.getRecording().getFilename()).collect(Collectors.joining(", ")));
                result = cacheResults.stream().max((o1, o2) -> {
                    RequestPattern r1 = o1.getRecording().getRequestPattern();
                    RequestPattern r2 = o2.getRecording().getRequestPattern();
                    int r1Length = r1.getProtocol().length() + r1.getHost().length() + r1.getPort().length() + r1.getPath().length() + r1.getQuery().length() + r1.getMethod().length() + r1.getHeader().length() + r1.getContent().length();
                    int r2Length = r2.getProtocol().length() + r2.getHost().length() + r2.getPort().length() + r2.getPath().length() + r2.getQuery().length() + r2.getMethod().length() + r2.getHeader().length() + r2.getContent().length();
                    return r1Length - r2Length;
                }).get();
            } else {
                result = cacheResults.get(0);
            }
            response = result.getRecording().getNextResponseTemplate().createResponse(result.getVariables());
            if (response == null) {
                String msg = "No response returned from matching recording (" + result.getRecording().getFilename() + ") , either all responses in recording have weight set to 0 or there are no responses in the recording";
                simLog.warn(msg);
                response = createTroxyErrorResponse(msg);
            }
            /* run filters "filterClientResponse()" on the response before returning it to client */
            for (Filter filter : filters)
                filter.doFilterResponse(response, false);
        }

        /* send response to client */
        byte[] contentBytes = response.getContent().getBytes(response.discoverCharset());
        /* status */
        try {
            servletResponse.setStatus(Integer.parseInt(response.getCode()));
        } catch (NumberFormatException e) {
            simLog.info("Unable to parse Response code as an Integer, setting Response code to {}", HttpURLConnection.HTTP_OK);
            servletResponse.setStatus(HttpURLConnection.HTTP_OK);
        }
        /* headers */
        StringTokenizer st = new StringTokenizer(response.getHeader(), "\n");
        while (st.hasMoreTokens()) {
            String both = st.nextToken();
            int pos = both.indexOf(": ");
            String key = both.substring(0, pos);
            /* skip Content-Length, we'll set that manually */
            if ("Content-Length".equals(key))
                continue;
            servletResponse.setHeader(key, both.substring(pos + 2));
        }
        servletResponse.setContentLength(contentBytes.length);

        /* delay the response if there's a delay */
        try {
            if (response.getDelay() > 0) {
                long timeSpent = System.currentTimeMillis() - request.getReceived();
                long delay = response.getDelay() - timeSpent;
                if (delay >= 0) {
                    simLog.info("Delaying response {}ms", delay);
                    Thread.sleep(delay);
                } else {
                    simLog.info("Response was to be delayed {}ms, but Troxy already spent {}ms so far handling the request", response.getDelay(), timeSpent);
                }
            }
        } catch (InterruptedException e) {
            simLog.warn("Failed delaying response to client", e);
        }

        /* then finally write content */
        simLog.debug("Response header: {}", response.getHeader());
        simLog.debug("Response content: {}", response.getContent());
        servletResponse.getOutputStream().write(contentBytes);

        /* let jetty know we've handled the request */
        jettyRequest.setHandled(true);
        simLog.info("Response sent {}ms after receiving request: {}", System.currentTimeMillis() - request.getReceived(), response);
    }

    /**
     * Create a Troxy error response to the client.
     * In case we don't have a response to the client, we'll create an "error" response.
     * Using HTTP status code 418, which really is an April Fools' joke, but it's unlikely to be confused with a "real" status code like 500 or 404.
     * @return A Troxy error response.
     */
    private Response createTroxyErrorResponse(String reason) {
        Response response = new Response();
        response.setCode("418");
        response.setHeader("Content-Type: text/plain; charset=UTF-8\nServer: Troxy");
        response.setContent("Troxy was unable to find a response to your request or an internal error occurred.\n\nReason: " + reason);
        return response;
    }

    /**
     * Connect to the remote host specified by the client.
     * @param request The Request from the client.
     * @return A connection to the remote host.
     * @throws IOException If unable to connect to the remote host.
     */
    private HttpURLConnection connectToHost(Request request) throws IOException {
        String pathAndQuery = request.getPath() + "?" + request.getQuery();
        int port;
        try {
            port = Integer.parseInt(request.getPort());
        } catch (NumberFormatException e) {
            simLog.debug("Unable to parse Request port as an Integer, setting port to 80");
            port = 80;
        }
        URL url = new URL(request.getProtocol(), request.getHost(), port, pathAndQuery);
        simLog.info("Connecting to host: {}", url);
        simLog.debug("Request header: {}", request.getHeader());
        simLog.debug("Request content: {}", request.getContent());
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        /* set method */
        con.setRequestMethod(request.getMethod());
        /* set headers */
        StringTokenizer st = new StringTokenizer(request.getHeader(), "\n");
        while (st.hasMoreTokens()) {
            String both = st.nextToken();
            int pos = both.indexOf(": ");
            String key = both.substring(0, pos);
            String value = both.substring(pos + 2);
            if ("Host".equals(key)) {
                simLog.debug("Setting host to: {} (was: {})", request.getHost(), value);
                value = request.getHost();
            }
            con.setRequestProperty(key, value);
        }
        /* set content if we're not GETing.
         * if we do, then java automagically set method to POST, we may not want that.
         */
        if (!"GET".equalsIgnoreCase(request.getMethod())
                && !"HEAD".equalsIgnoreCase(request.getMethod())
                && !"DELETE".equalsIgnoreCase(request.getMethod())
        ) {
            con.setDoOutput(true);
            con.getOutputStream().write(request.getContent().getBytes(request.discoverCharset()));
            con.getOutputStream().close();
        }
        /* connect to webservice */
        con.connect();

        return con;
    }

    /**
     * A TrustManager that can't be trusted. It accepts any certificate!
     */
    private static class NoTrustManager implements TrustManager, X509TrustManager {
        /**
         * {@inheritDoc}
         */
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {
        }
    }
}
