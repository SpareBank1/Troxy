package no.sb1.troxy.http.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Enumeration;
import java.util.SortedSet;
import java.util.TreeSet;
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
        /* set protocol */
        setProtocol(url != null ? url.getProtocol() : "");

        /* set host */
        setHost(url != null ? url.getHost() : "");

        /* set port */
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

        /* set path */
        setPath(url != null ? url.getPath() : pathInfo);

        /* set query string */
        setQuery(request.getQueryString());

        /* set method */
        setMethod(request.getMethod());

        /* sort and set header and detect character set for content field */
        /* the reason we sort header is that certain clients thinks it's amusing to randomize the order of the header elements,
         * which is perfectly legal, but it messes up our pattern matching */
        StringBuilder sb = new StringBuilder();
        Enumeration enumeration = request.getHeaderNames();
        SortedSet<String> headerSet = new TreeSet<>();
        while (enumeration.hasMoreElements()) {
            String key = (String) enumeration.nextElement();
            if ("Accept-Encoding".equalsIgnoreCase(key))
                continue;
            String value = request.getHeader(key);
            sb.append(key).append(": ").append(value);
            headerSet.add(sb.toString());
            sb.setLength(0);
        }
        int entries = headerSet.size();
        for (String h : headerSet) {
            sb.append(h);
            --entries;
            if (entries > 0)
                sb.append('\n');
        }
        setHeader(sb.toString());

        /* set content */
        sb.setLength(0);
        try (BufferedReader br = request.getReader()) {
            char[] buffer = new char[32768];
            int read;
            while ((read = br.read(buffer)) != -1)
                sb.append(buffer, 0, read);
        } catch (IOException e) {
            log.warn("Failed reading content from request", e);
        }
        /* replace windows newline ("\r\n") with unix newline ("\n").
         * this will break any recordings that rely on a specific newline, but such recordings are unlikely to exist.
         */
        setContent(sb.toString().replace("\r\n", "\n"));

        this.received = received;
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

    /**
     * Discover the character set of this request.
     * @return The character set name or "iso-8859-1" if character set name wasn't found.
     */
    public String discoverCharset() {
        return discoverCharset(header);
    }
    
    @Override
    public boolean equals(Object obj) {
        Request r = obj instanceof Request ? (Request) obj : null;
        return r != null && protocol.equals(r.protocol) && host.equals(r.host) && port.equals(r.port) && path.equals(r.path) && query.equals(r.query) && method.equals(r.method) && header.equals(r.header) && content.equals(r.content);
    }
}
