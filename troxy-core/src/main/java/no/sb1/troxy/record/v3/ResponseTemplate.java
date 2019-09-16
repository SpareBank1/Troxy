package no.sb1.troxy.record.v3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import no.sb1.troxy.http.common.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A templated Response, used for creating a Response to the client.

 */
public class ResponseTemplate extends Response {
    /**
     * Enum for various delay strategies.
     */
    public enum DelayStrategy {
        /**
         * Response will not be delayed.
         */
        NONE {
            @Override
            public long calculateDelay(ResponseTemplate responseTemplate) {
                return 0;
            }
        },
        /**
         * Response will be delayed the configured amount of time.
         */
        FIXED {
            @Override
            public long calculateDelay(ResponseTemplate responseTemplate) {
                if (responseTemplate.delayMean < 0) {
                    log.warn("Misconfigured value for delay strategy FIXED, mean value must be >= 0");
                    return 0;
                }
                return responseTemplate.delayMean;
            }
        },
        /**
         * Response will be delayed by a random amount of time within the configured range.
         */
        RANDOM {
            @Override
            public long calculateDelay(ResponseTemplate responseTemplate) {
                if (responseTemplate.delayMin < 0 || responseTemplate.delayMin > responseTemplate.delayMax) {
                    log.warn("Misconfigured values for delay strategy RANDOM, min value must be >= 0 and max value must be >= min value");
                    return 0;
                }
                return random.nextInt((int) (responseTemplate.delayMax - responseTemplate.delayMin)) + responseTemplate.delayMin;
            }
        },
        /**
         * Response will be delayed by a random time from a normal (also called "Gaussian") distribution.
         */
        NORMAL {
            @Override
            public long calculateDelay(ResponseTemplate responseTemplate) {
                if (responseTemplate.delayMin < 0 || responseTemplate.delayMin > responseTemplate.delayMax) {
                    log.warn("Misconfigured values for delay strategy NORMAL, min value must be >= 0 and max value must be >= min value");
                    return 0;
                }
                double range = (double) (responseTemplate.delayMax - responseTemplate.delayMin) / 2.0;
                long sleepTime;
                do {
                    /* ensure that the sleep time is within min/max (Random.nextGaussian() won't guarantee this) */
                    /* why not just "if (sleepTime < min) sleepTime = min; else if (sleepTime > max) sleepTime = max;"?
                     * because that would create spikes at min and max and not be a normal distribution.
                     * With the loop (which potentially may loop many times, but it's unlikely to happen often enough
                     * to affect the response time significantly) we just evenly "raise" the distribution instead.
                     */
                    sleepTime = responseTemplate.delayMin + (long) (random.nextGaussian() * range + range);
                } while (sleepTime < responseTemplate.delayMin || sleepTime > responseTemplate.delayMax);
                return sleepTime;
            }
        },
        /**
         * Response will be delayed by a random time from an exponential distribution algorithm.
         */
        EXPONENTIAL {
            @Override
            public long calculateDelay(ResponseTemplate responseTemplate) {
                if (responseTemplate.delayMin < 0 || responseTemplate.delayMin > responseTemplate.delayMean || responseTemplate.delayMean > responseTemplate.delayMax) {
                    log.warn("Misconfigured values for delay strategy EXPONENTIAL, min value must be >= 0 and <= mean, and max value must be >= mean value");
                    return 0;
                }
                return (long) Math.min((responseTemplate.delayMin - responseTemplate.delayMean * Math.log(1.0 - random.nextDouble())), responseTemplate.delayMax);
            }
        };

        /**
         * Random number generator.
         * It's threadsafe, so we'll just keep one for all Recordings.
         */
        private static Random random = new Random();

        /**
         * Calculate delay for the response.
         * @param responseTemplate The responseTemplate to calculate delay for.
         * @return The amount of milliseconds the response should be delayed.
         */
        public abstract long calculateDelay(ResponseTemplate responseTemplate);
    }
    /**
     * Logger for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(ResponseTemplate.class);
    /**
     * A regular expression used to escape characters that can be mistaken as the character used to denote a variable.
     */
    private static final Pattern ESCAPE_VARIABLE_PREFIX_PATTERN = Pattern.compile("(\\$)");

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
     * The original response for this ResponseTemplate.
     * This is the response from the server when first creating a recording.
     * This value should under normal circumstances never be modified.
     */
    private Response originalResponse;
    /**
     * The delay strategy for this Recording.
     */
    private DelayStrategy delayStrategy = DelayStrategy.NONE;
    /**
     * Min delay for response.
     */
    private long delayMin;
    /**
     * Mean delay for response.
     */
    private long delayMean;
    /**
     * Max delay for response.
     */
    private long delayMax;
    /**
     * The response weight, used for calculating probability of this response being returned.
     */
    private long weight = 1;

    /**
     * Empty constructor.
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
        response.setDelay(delayStrategy.calculateDelay(this));
        return response;
    }

    /**
     * Get the delay strategy for this recording.
     * @return Delay strategy for this recording.
     */
    public DelayStrategy getDelayStrategy() {
        return delayStrategy;
    }

    /**
     * Set the delay strategy for this recording.
     * @param delayStrategy Delay strategy for this recording.
     */
    public void setDelayStrategy(DelayStrategy delayStrategy) {
        this.delayStrategy = delayStrategy;
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
     * Get min delay.
     * @return Min delay.
     */
    public long getDelayMin() {
        return delayMin;
    }

    /**
     * Set min delay.
     * @param delayMin Min delay.
     */
    public void setDelayMin(long delayMin) {
        this.delayMin = Math.max(delayMin, 0);
    }

    /**
     * Get mean delay.
     * @return Mean delay.
     */
    public long getDelayMean() {
        return delayMean;
    }

    /**
     * Set mean delay.
     * @param delayMean Mean delay.
     */
    public void setDelayMean(long delayMean) {
        this.delayMean = Math.max(delayMean, 0);
    }

    /**
     * Get max delay.
     * @return Max delay.
     */
    public long getDelayMax() {
        return delayMax;
    }

    /**
     * Set max delay.
     * @param delayMax Max delay.
     */
    public void setDelayMax(long delayMax) {
        this.delayMax = Math.max(delayMax, 0);
    }

    /**
     * Get response weight.
     * @return Response weight.
     */
    public long getWeight() {
        return weight;
    }

    /**
     * Set response weight.
     * @param weight Response weight.
     */
    public void setWeight(long weight) {
        this.weight = Math.max(weight, 0);
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
