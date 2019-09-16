package no.sb1.troxy.record.v3;

import java.util.regex.Pattern;
import no.sb1.troxy.http.common.Request;

/**
 * A Request suited for pattern matching.
 * This class also got a list of ResponseTemplates, so this class is the class that is actually serialized.
 * The data in this class is also used to build up the Cache.

 */
public class RequestPattern extends Request {
    /**
     * A regular expression used to escape characters that can be mistaken as a regular expression.
     * These characters are escaped: \*+[](){}$.?^|
     */
    private static final Pattern ESCAPE_REGEXP_CHARACTERS_PATTERN = Pattern.compile("([\\\\*+\\[\\](){}\\$.?\\^|])");
    /**
     * The original request for this RequestPattern.
     * This is the request to the server when first creating a recording.
     * This value should under normal circumstances never be modified.
     */
    private Request originalRequest;

    /**
     * Empty constructor.
     */
    public RequestPattern() {
    }

    /**
     * Constructor that creates a RequestPattern from a Request by escaping regular expression characters.
     * @param request The Request to create this RequestPattern from.
     */
    public RequestPattern(Request request) {
        setOriginalRequest(request);
        setProtocol("^" + escape(request.getProtocol()) + "$");
        setHost("^" + escape(request.getHost()) + "$");
        setPort("^" + escape(request.getPort()) + "$");
        setPath("^" + escape(request.getPath()) + "$");
        setQuery("^" + escape(request.getQuery()) + "$");
        setMethod("^" + escape(request.getMethod()) + "$");
        setHeader("^" + escape(request.getHeader()) + "$");
        setContent("^" + escape(request.getContent()) + "$");
    }

    /**
     * Get the original, unmodified request for this Recording.
     * @return The original, unmodified request for this Recording.
     */
    public Request getOriginalRequest() {
        return originalRequest;
    }

    /**
     * Set the original, unmodified request for this Recording.
     * @param originalRequest The original, unmodified request for this Recording.
     */
    public void setOriginalRequest(Request originalRequest) {
        this.originalRequest = originalRequest;
    }

    /**
     * Escape characters that may be mistaken as a regular expression.
     * @param text The text to be escaped.
     * @return An escaped version of the given text.
     */
    public static String escape(String text) {
        if (text == null)
            return null;
        return ESCAPE_REGEXP_CHARACTERS_PATTERN.matcher(text).replaceAll("\\\\$1");
    }
}
