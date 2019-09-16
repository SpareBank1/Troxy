package no.sb1.troxy.record.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import no.sb1.troxy.http.common.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A templated Response, used for creating a Response to the client.

 * @deprecated Use v3 instead
 */
@Deprecated
public class ResponseTemplate extends Response {
    /**
     * Logger for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(ResponseTemplate.class);
    /**
     * A regular expression used to escape characters that can be mistaken as the character used to denote a variable.
     */
    private static final Pattern ESCAPE_VARIABLE_PREFIX_PATTERN = Pattern.compile("(\\$)");
    /**
     * The original response for this ResponseTemplate.
     * This is the response from the server when first creating a recording.
     * This value should under normal circumstances never be modified.
     */
    private Response originalResponse;
    /**
     * A list of static text and variables used for the code field.
     */
    private List<Entry> code;
    /**
     * A list of static text and variables used for the header field.
     */
    private List<Entry> header;
    /**
     * A list of static text and variables used for the content field.
     */
    private List<Entry> content;

    /**
     * Empty constructor, needed to create a ResponseTemplate object from a serialized (XML) state.
     */
    public ResponseTemplate() {
    }

    /**
     * Constructor that creates a ResponseTemplate from a Response by escaping characters used to denote a variable.
     * @param response The Response to create this ResponseTemplate from.
     */
    public ResponseTemplate(Response response) {
        setOriginalResponse(response);
        setCode(escape(response.getCode()));
        setHeader(escape(response.getHeader()));
        setContent(escape(response.getContent()));
    }

    /**
     * Create a Response from this template.
     * @param variables Map of keys and values to be inserted into the response.
     * @return A Response from this template.
     */
    public Response createResponse(Map<String, Matcher> variables) {
        if (code == null) {
            code = createArray(getCode());
            header = createArray(getHeader());
            content = createArray(getContent());
        }
        Response response = new Response();
        response.setCode(createString(code, variables));
        response.setHeader(createString(header, variables));
        response.setContent(createString(content, variables));
        return response;
    }

    /**
     * Get the original response.
     * @return The original response.
     */
    public Response getOriginalResponse() {
        return originalResponse;
    }

    /**
     * Set the original response.
     * @param originalResponse The original response.
     */
    public void setOriginalResponse(Response originalResponse) {
        this.originalResponse = originalResponse;
    }

    /**
     * Escape characters that may be mistaken as a variable.
     * @param text The text to be escaped.
     * @return An escaped version of the given text.
     */
    public static String escape(String text) {
        if (text == null)
            return null;
        return ESCAPE_VARIABLE_PREFIX_PATTERN.matcher(text).replaceAll("$1$1");
    }

    /**
     * Helper method to split up a templated text into a list of static text and variables.
     * @param text The templated text.
     * @return An array of static text and variables.
     */
    private List<Entry> createArray(String text) {
        List<Entry> entries = new ArrayList<>();
        int index = 0;
        int previousIndex = 0;
        boolean detectVariable = false;
        log.debug("Creating Entry array from text: {}", text);
        while ((index = text.indexOf('$', index)) >= 0) {
            int charCount = 1;
            while (++index < text.length() && text.charAt(index) == '$')
                ++charCount;
            if (charCount % 2 == 0) {
                /* escaped character */
                continue;
            }
            index -= detectVariable ? charCount : 1;
            Entry entry = new Entry(detectVariable, text.substring(previousIndex, index));
            log.debug("Adding {} to array: {}", entry.isVariable() ? "variable" : "static text", entry.getText());
            entries.add(entry);
            detectVariable = !detectVariable;
            previousIndex = ++index;
        }
        Entry entry = new Entry(false, text.substring(previousIndex));
        log.debug("Adding {} to array: {}", entry.isVariable() ? "variable" : "static text", entry.getText());
        entries.add(entry);
        return entries;
    }

    /**
     * Helper method to build up code, header or content.
     * @param entries The entries to build this String from.
     * @param variables The variables to insert into the String.
     * @return A String for code, header or content where variables are replaced by values.
     */
    private String createString(List<Entry> entries, Map<String, Matcher> variables) {
        StringBuilder sb = new StringBuilder();
        log.debug("Building String from Entry array");
        for (Entry entry : entries) {
            String text = null;
            if (entry.isVariable()) {
                String variable = entry.getText();
                int colonIndex = variable.indexOf(':');
                if (colonIndex > 0) {
                    /* field specified */
                    text = getVariable(variables.get(variable.substring(0, colonIndex).toUpperCase()), variable.substring(colonIndex + 1));
                } else {
                    /* field not specified, have to go through all */
                    for (String key : variables.keySet()) {
                        text = getVariable(variables.get(key.toUpperCase()), variable);
                        if (text != null)
                            break;
                    }
                }
                log.info("Inserting value for variable \"{}\": {}", variable, text);
            } else {
                text = entry.getText();
                log.debug("Inserting static text: {}", text);
            }
            sb.append(text);
        }
        return sb.toString();
    }

    /**
     * Get value of variable from matcher.
     * @param matcher Matcher to retrieve variable value from.
     * @param variable Name (or index) of variable value to retrieve.
     * @return Value of variable, or null if not found.
     */
    private String getVariable(Matcher matcher, String variable) {
        if (matcher == null)
            return null;
        try {
            return matcher.group(variable);
        } catch (Exception e) {
            /* may be integer instead of named group */
            try {
                return matcher.group(Integer.parseInt(variable));
            } catch (Exception e2) {
                /* apparently not, fall though and return null */
            }
        }
        return null;
    }

    /**
     * An Entry is either static text or a variable.
     * When the variable is set then the text is the key used to find the value of the variable.
     */
    private class Entry {
        /**
         * Whether this Entry is a variable.
         */
        private boolean variable;
        /**
         * Either static text or the key for the variable.
         */
        private String text;

        /**
         * Default constructor, sets both values.
         * @param variable Whether the entry is a variable.
         * @param text Static text of key for variable.
         */
        public Entry(boolean variable, String text) {
            this.variable = variable;
            this.text = variable ? text : text.replace("$$", "$");
        }

        /**
         * Getter for variable.
         * @return Returns true if Entry is variable, false otherwise.
         */
        public boolean isVariable() {
            return variable;
        }

        /**
         * Getter for text.
         * @return The static text or the key for the variable.
         */
        public String getText() {
            return text;
        }
    }
}
