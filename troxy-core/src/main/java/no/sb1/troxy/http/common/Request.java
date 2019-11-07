package no.sb1.troxy.http.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.xml.bind.annotation.XmlTransient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Request is a simple bean with all the data we receive from a client.
 */
public class Request extends Packet {
    /**
     * Logger for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(Request.class);
    /**
     * Which protocol is used. This is usually "http" or "https".
     */
    private String protocol = "";
    /**
     * Host we're connecting to.
     */
    private String host = "";
    /**
     * Port number we're connecting to.
     */
    private String port = "";
    /**
     * Path on remote server.
     */
    private String path = "";
    /**
     * Query parameters.
     */
    private String query = "";
    /**
     * Method, most commonly GET or POST.
     */
    private String method = "";
    /**
     * Header for request.
     */
    private String header = "";
    /**
     * Content for request.
     */
    private String content = "";
    /**
     * The time the request was received in milliseconds since epoch.
     */
    private transient long received;
    private byte[] rawByteContent;

    /**
     * Empty constructor, needed to create a Request object from a serialized state.
     */
    public Request() {
    }

    /**
     * Constructor for creating a Request object from a HttpServletRequest.
     * @param request The incoming HttpServletRequest.
     * @param received The time the request was received (milliseconds since epoch).
     */
    public Request(HttpServletRequest request, long received) {
        /* path is "/<protocol>://<host>[:port][/path]" or just "/path" */
        String pathInfo = request.getPathInfo();
        pathInfo = restoreNormalizedURL(pathInfo);
        URL url = null;
        try {
            if (pathInfo.contains("://")) {
                url = new URL(pathInfo.substring(1));
            } else {
                // protocol, host & port isn't defined in path, may be some proxy rewriting the Host header
                // assume http(s) and attempt to guess these values
                String host = request.getHeader("Host");
                if (host != null && !host.isEmpty()) {
                    try {
                        InetAddress addr = InetAddress.getByName(host.replaceAll(":\\d+$", ""));
                        if (!addr.isAnyLocalAddress() && !addr.isLoopbackAddress() && NetworkInterface.getByInetAddress(addr) == null)
                            url = new URL("http" + (request.isSecure() ? "s" : "") + "://" + host + pathInfo);
                    } catch (Exception e) {
                        log.warn("Unable to determine if host '{}' is a local address", host, e);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Couldn't parse URL: " + pathInfo.substring(1), e);
        }
        setProtocol(url != null ? url.getProtocol() : "");
        setHost(url != null ? url.getHost() : "");
        determinePort(url);
        setPath(url != null ? url.getPath() : pathInfo);
        setQuery(request.getQueryString());
        setMethod(request.getMethod());
        sortAndSetHeader(request);
        copyContent(request);
        this.received = received;
    }

    /**
     * Try to restore normalized URLs, i.e. restore http:/ to http://
     *
     * Finds the first occurrence of "http:/" and inserts a slash, producing "http://", while taking care to not
     * insert a slash if we already have "://".
     */
    private String restoreNormalizedURL(String pathInfo) {
        Pattern pattern = Pattern.compile("http[s]?:/[^/]");
        Matcher matcher = pattern.matcher(pathInfo);
        if (matcher.find()) {
            int slashIndex = matcher.end() - 1;
            return pathInfo.substring(0, slashIndex) + "/" + pathInfo.substring(slashIndex);
        } else {
            return pathInfo;
        }
    }

    /**
     * Sort and set headers
     *
     * The reason we sort header is that certain clients thinks it's amusing to randomize the order of the header
     * elements, which is perfectly legal, but it messes up our pattern matching
     */
    private void sortAndSetHeader(HttpServletRequest request) {
        Enumeration enumeration = request.getHeaderNames();
        SortedSet<String> headerSet = new TreeSet<>();
        while (enumeration.hasMoreElements()) {
            String key = (String) enumeration.nextElement();
            if ("Accept-Encoding".equalsIgnoreCase(key))
                continue;
            String value = request.getHeader(key);
            headerSet.add(key + ": " + value);
        }
        setHeader(String.join("\n", headerSet));
    }

    private void determinePort(URL url) {
        if (url != null) {
            if (url.getPort() > 0)
                setPort("" + url.getPort());
            else if ("https".equalsIgnoreCase(protocol))
                setPort("443");
            else
                setPort("80");
        } else {
            setPort("");
        }
    }

    /**
     * Copies the content of the original request.
     *
     * The contents of the original request is stored in two different formats:
     *  1. As a byte array consisting of the unmodified bytes from the original request.
     *  2. As a String created from the bytes read.
     *
     * The unmodified byte array is used when we pass on the request to remote systems. We do this to ensure that we do
     * not modify the contents, since we in some cases do not know the encoding of the contents.
     *
     * The String representation is used for request matching. If the underlying request implementation cannot determine
     * the encoding we fallback to "iso-8859-1". We also replace all "\r\n" with "\n". This behaviour is kept around to
     * avoid breaking existing recordings.
     *
     * Of course, in the case where troxy does not determine the correct encoding for contents, troxy and the remote
     * system may possibly see two different versions of the contents. We assume that this okay since troxy is not
     * concerned with the actual content of requests, but rather if it matches previous requests, and that should not
     * change even if troxy is seeing a payload that slightly incorrectly encoded.
     */
    private void copyContent(HttpServletRequest request) {
        byte[] buffer = new byte[32_768];
        int totalRead = 0;
        try (InputStream is = request.getInputStream()) {
            int read;
            while ((read = is.read(buffer, totalRead, buffer.length - totalRead)) != -1) {
                totalRead += read;
                if (totalRead == buffer.length) {
                    buffer = Arrays.copyOf(buffer, buffer.length * 4);
                }
            }
        } catch (IOException e) {
            log.warn("Failed reading content from request", e);
        }
        // Arrays.copyOf(buffer, totalRead) will truncate the buffer down to actual number of bytes read
        setRawByteContent(Arrays.copyOf(buffer, totalRead));

        try {
            String encoding = Optional.ofNullable(request.getCharacterEncoding()).orElse("iso-8859-1");
            String contentAsString = new String(rawByteContent, encoding).replace("\r\n", "\n");
            setContent(contentAsString);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private void setRawByteContent(byte[] buffer) {
       this.rawByteContent = buffer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return getProtocol() + "://" + getHost() + ":" + getPort() + getPath() + "?" + getQuery() + " [" + getMethod() + "] [HEADER: " + getHeader().length() + " characters] [CONTENT: " + getContent().length() + " characters]";
    }

    /**
     * Set protocol. Null value is set to "".
     * @param protocol The protocol.
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol == null ? "" : protocol;
    }

    /**
     * Get protocol.
     * @return The protocol.
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * Set host. Null value is set to "".
     * @param host The host.
     */
    public void setHost(String host) {
        this.host = host == null ? "" : host;
    }

    /**
     * Get host.
     * @return The host.
     */
    public String getHost() {
        return host;
    }

    /**
     * Set port. Null value is set to "".
     * @param port The port.
     */
    public void setPort(String port) {
        this.port = port == null ? "" : port;
    }

    /**
     * Get port.
     * @return port The port.
     */
    public String getPort() {
        return port;
    }

    /**
     * Set path. Null value is set to "".
     * @param path The path.
     */
    public void setPath(String path) {
        this.path = path == null ? "" : path;
    }

    /**
     * Get path.
     * @return The path.
     */
    public String getPath() {
        return path;
    }

    /**
     * Set query. Null value is set to "".
     * @param query The query.
     */
    public void setQuery(String query) {
        this.query = query == null ? "" : query;
    }

    /**
     * Get query.
     * @return The query.
     */
    public String getQuery() {
        return query;
    }

    /**
     * Set method. Null value is set to "".
     * @param method The method.
     */
    public void setMethod(String method) {
        this.method = method == null ? "" : method;
    }

    /**
     * Get method.
     * @return The method.
     */
    public String getMethod() {
        return method;
    }

    /**
     * Set header. Null value is set to "".
     * @param header The header.
     */
    public void setHeader(String header) {
        this.header = header == null ? "" : header;
    }

    /**
     * Get header.
     * @return The header.
     */
    public String getHeader() {
        return header;
    }

    /**
     * Set content. Null value is set to "".
     * @param content The content.
     */
    public void setContent(String content) {
        this.content = content == null ? "" : content;
    }

    /**
     * Get content.
     * @return The content.
     */
    public String getContent() {
        return content;
    }

    /**
     * Get time when request was received.
     * @return When the request was received, given in milliseconds since epoch.
     */
    @XmlTransient
    public long getReceived() {
        return received;
    }

    @Override
    public boolean equals(Object obj) {
        Request r = obj instanceof Request ? (Request) obj : null;
        return r != null && protocol.equals(r.protocol) && host.equals(r.host) && port.equals(r.port) && path.equals(r.path) && query.equals(r.query) && method.equals(r.method) && header.equals(r.header) && content.equals(r.content);
    }

    public byte[] getRawByteContent() {
        return rawByteContent;
    }
}
