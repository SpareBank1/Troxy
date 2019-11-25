package no.sb1.troxy.http.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlTransient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Response is a simple bean with all the data we transmit to a client.

 */
public class Response extends Packet {
    /**
     * Logger for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(Response.class);
    /**
     * Status code for this response.
     */
    private String code = "";

    private String reason = null;
    /**
     * Header for response.
     */
    private String header = "";
    /**
     * Content for response.
     */
    private String content = "";
    /**
     * How long the response should be delayed in milliseconds before it's sent to the client.
     * Used by filters delaying the response.
     */
    private transient long delay;

    /**
     * Empty constructor, needed to create a Response object from a serialized (XML) state.
     */
    public Response() {
    }

    /**
     * Constructor for creating a Response object from a HttpURLConnection.
     * @param remoteConnection A connection to the remote host.
     */
    public Response(HttpURLConnection remoteConnection) {
        /* Extract status code and status reason */
        int responseCode = HttpURLConnection.HTTP_INTERNAL_ERROR;
        String responseReason = null;
        try {
            responseCode = remoteConnection.getResponseCode();
            responseReason = remoteConnection.getResponseMessage();
        } catch (Exception e) {
            log.info("Failed reading response code, setting it to {}", HttpURLConnection.HTTP_INTERNAL_ERROR, e);
            responseReason = "TROXY: Failed to extract responsecode from remoteConnection";
        }
        code = "" + responseCode;
        reason = responseReason;

        /* Extract headers */
        StringBuilder sb = new StringBuilder();
        Map<String, List<String>> headerFields = remoteConnection.getHeaderFields();
        boolean firstEntry = true;
        for (String key : headerFields.keySet()) {
            if (key == null) {
                /* apparently it sets status code & protocol as a header with key <null> */
                continue;
            }
            String value = remoteConnection.getHeaderField(key);
            if (!firstEntry)
                sb.append('\n');
            sb.append(key).append(": ").append(value);
            firstEntry = false;
        }
        header = sb.toString();

        /* Extract content */
        sb = new StringBuilder();
        try {
            InputStream contentStream = null;
            if (responseCode < 400) contentStream = remoteConnection.getInputStream();
            else contentStream = remoteConnection.getErrorStream();
            if (contentStream != null) {
                /* detect character set */
                String charset = discoverCharset(remoteConnection.getContentType());
                try (BufferedReader br = new BufferedReader(new InputStreamReader(contentStream, charset))) {
                    char[] buffer = new char[32768];
                    int read;
                    while ((read = br.read(buffer)) != -1)
                        sb.append(buffer, 0, read);
                }
            }
        } catch (Exception e) {
            log.info("Failed reading response content with responsecode: {}", responseCode, e);
        }
        content = sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "[CODE: " + getCode() + "] [REASON: " + getReason() +"] [HEADER: " + getHeader().length() + " characters] [CONTENT: " + getContent().length() + " characters]";
    }

    /**
     * Set code. Null value is set to "".
     * @param code The code.
     */
    public void setCode(String code) {
        this.code = code == null ? "" : code;
    }

    /**
     * Get code.
     * @return The code.
     */
    public String getCode() {
        return code;
    }

    /**
     * Get the HTTP status reason phrase (if any).
     * @return The reason.
     */
    public String getReason() {
        return reason;
    }

    /**
     * Set reason. Null value is set to "".
     * @param reason The code.
     */
    public void setReason(String reason) {
        this.reason = reason == null ? "" : reason;
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
     * Get how long the response should be delayed in milliseconds before it's sent to client.
     * @return How long the response should be delayed in milliseconds before it's sent to client.
     */
    @XmlTransient
    public long getDelay() {
        return delay;
    }

    /**
     * Set how long the response should be delayed in milliseconds before it's sent to client.
     * @param delay How long the response should be delayed in milliseconds before it's sent to client.
     */
    public void setDelay(long delay) {
        this.delay = delay;
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
        Response r = obj instanceof Response ? (Response) obj : null;
        return r != null && code.equals(r.code) && header.equals(r.header) && content.equals(r.content);
    }
}
