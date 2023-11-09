package no.sb1.troxy.record.v2;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.xml.bind.annotation.XmlTransient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a container class for a recording.
 * It contains the original request, and a RequestPattern (modified request) and a list of original responseTemplates and ResponseTemplates.
 * In addition metadata for a recording is stored in this file, such as filename, statistics and response delays.

 * @deprecated Use v3 instead
 */
@Deprecated
public class Recording {
    /**
     * Logger for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(Recording.class);
    /**
     * The valid keys that can be used for configuring the various delay strategies.
     */
    public enum DelayValueKeys {
        MIN,
        MEAN,
        MAX
    }
    /**
     * Enum for various delay strategies.
     */
    public enum DelayStrategy {
        /**
         * Response will not be delayed.
         */
        NONE {
            @Override
            protected long calculateDelay(Recording recording) {
                return 0;
            }

            @Override
            public List<DelayValueKeys> getValidDelayValueKeys() {
                return new ArrayList<>();
            }
        },
        /**
         * Response will be delayed the configured amount of time.
         */
        FIXED {
            @Override
            protected long calculateDelay(Recording recording) {
                long mean = recording.delayValues.containsKey(DelayValueKeys.MEAN) ? recording.delayValues.get(DelayValueKeys.MEAN) : 0;
                if (0 >= mean) {
                    log.warn("Misconfigured values for delay strategy {}, must be \"0 < {}\", but the values are \"0 < {}\"", toString(), DelayValueKeys.MEAN, mean);
                    return 0;
                }
                return mean;
            }

            @Override
            public List<DelayValueKeys> getValidDelayValueKeys() {
                List<DelayValueKeys> keys = new ArrayList<>();
                keys.add(DelayValueKeys.MEAN);
                return keys;
            }
        },
        /**
         * Response will be delayed by a random amount of time within the configured range.
         */
        RANDOM {
            @Override
            protected long calculateDelay(Recording recording) {
                long min = recording.delayValues.containsKey(DelayValueKeys.MIN) ? recording.delayValues.get(DelayValueKeys.MIN) : 0;
                long max = recording.delayValues.containsKey(DelayValueKeys.MAX) ? recording.delayValues.get(DelayValueKeys.MAX) : 0;
                if (0 >= min || min > max) {
                    log.warn("Misconfigured values for delay strategy {}, must be \"0 < {} <= {}\", but the values are \"0 < {} <= {}\"", toString(), DelayValueKeys.MIN, DelayValueKeys.MAX, min, max);
                    return 0;
                }
                return random.nextInt((int) (max - min)) + min;
            }

            @Override
            public List<DelayValueKeys> getValidDelayValueKeys() {
                List<DelayValueKeys> keys = new ArrayList<>();
                keys.add(DelayValueKeys.MIN);
                keys.add(DelayValueKeys.MAX);
                return keys;
            }
        },
        /**
         * Response will be delayed by a random time from a normal (also called "Gaussian") distribution.
         */
        NORMAL {
            @Override
            protected long calculateDelay(Recording recording) {
                long min = recording.delayValues.containsKey(DelayValueKeys.MIN) ? recording.delayValues.get(DelayValueKeys.MIN) : 0;
                long max = recording.delayValues.containsKey(DelayValueKeys.MAX) ? recording.delayValues.get(DelayValueKeys.MAX) : 0;
                if (0 >= min || min > max) {
                    log.warn("Misconfigured values for delay strategy {}, must be \"0 < {} <= {}\", but the values are \"0 < {} <= {}\"", toString(), DelayValueKeys.MIN, DelayValueKeys.MAX, min, max);
                    return 0;
                }
                double range = (double) (max - min) / 2.0;
                long sleepTime;
                do {
                    /* ensure that the sleep time is within min/max (Random.nextGaussian() won't guarantee this) */
                    /* why not just "if (sleepTime < min) sleepTime = min; else if (sleepTime > max) sleepTime = max;"?
                     * because that would create spikes at min and max and not be a normal distribution.
                     * With the loop (which potentially may loop many times, but it's unlikely to happen often enough
                     * to affect the response time significantly) we just evenly "raise" the distribution instead.
                     */
                    sleepTime = min + (long) (random.nextGaussian() * range + range);
                } while (sleepTime < min || sleepTime > max);
                return sleepTime;
            }

            @Override
            public List<DelayValueKeys> getValidDelayValueKeys() {
                List<DelayValueKeys> keys = new ArrayList<>();
                keys.add(DelayValueKeys.MIN);
                keys.add(DelayValueKeys.MAX);
                return keys;
            }
        },
        /**
         * Response will be delayed by a random time from an exponential distribution algorithm.
         */
        EXPONENTIAL {
            @Override
            protected long calculateDelay(Recording recording) {
                long min = recording.delayValues.containsKey(DelayValueKeys.MIN) ? recording.delayValues.get(DelayValueKeys.MIN) : 0;
                long mean = recording.delayValues.containsKey(DelayValueKeys.MEAN) ? recording.delayValues.get(DelayValueKeys.MEAN) : 0;
                long max = recording.delayValues.containsKey(DelayValueKeys.MAX) ? recording.delayValues.get(DelayValueKeys.MAX) : 0;
                if (0 >= min || min > mean || mean > max) {
                    log.warn("Misconfigured values for delay strategy {}, must be \"0 < {} <= {} <= {}\", but the values are \"0 < {} <= {}\"", toString(), DelayValueKeys.MIN, DelayValueKeys.MEAN, DelayValueKeys.MAX, min, max);
                    return 0;
                }
                return (long) Math.min((min - mean * Math.log(1.0 - random.nextDouble())), max);
            }

            @Override
            public List<DelayValueKeys> getValidDelayValueKeys() {
                List<DelayValueKeys> keys = new ArrayList<>();
                keys.add(DelayValueKeys.MIN);
                keys.add(DelayValueKeys.MEAN);
                keys.add(DelayValueKeys.MAX);
                return keys;
            }
        };

        /**
         * Random number generator.
         * It's threadsafe, so we'll just keep one for all Recordings.
         */
        private static Random random = new Random();

        /**
         * Calculate delay for the response.
         * Method is protected since it's only supposed to be called from the Recording class,
         * but "private abstract" is not an allowed modifier of methods.
         * @param recording The recording to calculate delay for.
         * @return The amount of milliseconds the response should be delayed.
         */
        protected abstract long calculateDelay(Recording recording);

        /**
         * Get the valid keys for values used by the delay strategy.
         * @return Valid keys for values used by the delay strategy.
         */
        public abstract List<DelayValueKeys> getValidDelayValueKeys();
    }
    /**
     * The RequestTemplate with regular expressions, used to match incoming requests.
     */
    private RequestPattern requestPattern;
    /**
     * The ResponseTemplates associated with the request.
     */
    private List<ResponseTemplate> responseTemplates = new ArrayList<>();
    /**
     * The delay strategy for this Recording.
     */
    private DelayStrategy delayStrategy;
    /**
     * Values for the delay strategy.
     */
    private Map<DelayValueKeys, Long> delayValues = new HashMap<>();
    /**
     * Filename for this Recording.
     * When we load and save a recording we'll overwrite the record rather than create a new record.
     */
    private transient String filename;
    /**
     * Counter for how many times a response has been returned since this Recording was loaded into Cache.
     */
    private transient AtomicInteger responseCounterTotal = new AtomicInteger(0);
    /**
     * Counter for how many times a response has been returned since the counter for this Recording last was reset.
     */
    private transient AtomicInteger responseCounterCurrent = new AtomicInteger(0);

    /**
     * Unfortunately XML serialization ignores "transient" keyword.
     * To prevent transient variables from being serialized we need the following code.
     * Every getter/setter pair will be serialized, so if the class got "setFilename()" and "getFilename()", then "filename"
     * will be stored in the serialized file. If a class only got one of "setVariable()" or "getVariable()" then "variable"
     * will not be stored in the serialized file.
     * So strictly speaking, it's not necessary to have the code below for transient members where we don't have both a getter
     * and a setter, but it's kept for transient members in case getter/setter is implemented for those members at a later time.
     */
    static {
        try {
            PropertyDescriptor[] propertyDescriptors = Introspector.getBeanInfo(Recording.class).getPropertyDescriptors();
            for (PropertyDescriptor pd : propertyDescriptors) {
                if (pd == null)
                    continue;
                switch (pd.getName()) {
                case "filename":
                case "responseCounterTotal":
                case "responseCounterCurrent":
                    pd.setValue("transient", Boolean.TRUE);
                    break;

                default:
                    /* nothing */
                    break;
                }
            }
        } catch (IntrospectionException e) {
            log.warn("Unable to mark variable as transient", e);
        }
    }

    /**
     * Empty constructor, needed to create a Recording object from a serialized (XML) state.
     */
    public Recording() {
    }

    /**
     * Default constructor.
     * @param requestPattern The optionally modified request.
     * @param responseTemplate The optionally modified response.
     */
    public Recording(RequestPattern requestPattern, ResponseTemplate responseTemplate) {
        this.requestPattern = requestPattern;
        addResponse(responseTemplate);
        setDelayStrategy(DelayStrategy.NONE);
        for (DelayValueKeys key : DelayValueKeys.values())
            getDelayValues().put(key, 0L);
    }

    /**
     * Constructor for creating an empty Recording.
     * This is a workaround to force XMLEncoder to always create all fields in XML when serializing this object.
     *
     * @return An empty Recording object.
     */
    public static Recording createEmptyRecording() {
        Recording recording = new Recording();

        RequestPattern requestPattern = new RequestPattern();
        requestPattern.setProtocol("^$");
        requestPattern.setHost("^$");
        requestPattern.setPort("^$");
        requestPattern.setPath("^$");
        requestPattern.setQuery("^$");
        requestPattern.setMethod("^$");
        requestPattern.setHeader("^$");
        requestPattern.setContent("^$");
        // note: we don't set original request as we don't have any (to prevent "regex doesn't match!" notifications in GUI)
        recording.setRequestPattern(requestPattern);

        ResponseTemplate responseTemplate = new ResponseTemplate();
        responseTemplate.setCode("");
        responseTemplate.setHeader("");
        responseTemplate.setContent("");
        // note: we don't set original response as we don't have any (to prevent "regex doesn't match!" notifications in GUI)
        List<ResponseTemplate> responseTemplates = new ArrayList<>();
        responseTemplates.add(responseTemplate);
        recording.setResponseTemplates(responseTemplates);

        recording.setDelayStrategy(DelayStrategy.NONE);
        for (DelayValueKeys key : DelayValueKeys.values())
            recording.getDelayValues().put(key, 0L);
        return recording;
    }

    /**
     * Get the RequestPattern for this Recording.
     * @return RequestPattern for this Recording.
     */
    public RequestPattern getRequestPattern() {
        return requestPattern;
    }

    /**
     * Set the RequestPattern for this Recording.
     * @param requestPattern RequestPattern for this Recording.
     */
    public void setRequestPattern(RequestPattern requestPattern) {
        this.requestPattern = requestPattern;
    }

    /**
     * Get all ResponseTemplates for this Recording.
     * @return ResponseTemplates for this Recording.
     */
    public List<ResponseTemplate> getResponseTemplates() {
        return responseTemplates;
    }

    /**
     * Set all responseTemplates for this Recording.
     * @param responseTemplates The ResponseTemplates for this Recording.
     */
    public void setResponseTemplates(List<ResponseTemplate> responseTemplates) {
        this.responseTemplates = responseTemplates;
    }

    /**
     * Get the filename for this Recording.
     * @return The filename for this Recording.
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Set the filename for this Recording.
     * @param filename The filename for this Recording.
     */
    public void setFilename(String filename) {
        this.filename = filename;
    }

    /**
     * Convenience method for adding a ResponseTemplate to the list of ResponseTemplates.
     * @param responseTemplate The ResponseTemplate to add to the list.
     */
    public void addResponse(ResponseTemplate responseTemplate) {
        if (responseTemplate != null)
            responseTemplates.add(responseTemplate);
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
     * Get the values used for the various delay strategies.
     * @return Values used for the various delay strategies.
     */
    public Map<DelayValueKeys, Long> getDelayValues() {
        return delayValues;
    }

    /**
     * Set the values used for the various delay strategies.
     * @param delayValues Values used for the various delay strategies.
     */
    public void setDelayValues(Map<DelayValueKeys, Long> delayValues) {
        if (delayValues == null)
            return;
        this.delayValues = delayValues;
    }

    /**
     * Get the amount of milliseconds the response of this Recording should be delayed.
     * @return How many milliseconds the response should be delayed.
     */
    @XmlTransient
    public long getDelay() {
        long delay = delayStrategy.calculateDelay(this);
        if (delayStrategy != DelayStrategy.NONE && delay <= 0)
            log.warn("Recording {} use delay strategy {}, but this resulted in no delay. Delay in recording is likely misconfigured, or if you want no delay then use delay strategy {} instead", getFilename(), delayStrategy.toString(), DelayStrategy.NONE.toString());
        return delayStrategy.calculateDelay(this);
    }

    /**
     * Get the amount of times this Recording has returned a response since the Recording was loaded into the Cache.
     * @return The amount of times this Recording has returned a response since the Recording was loaded into the Cache.
     */
    @XmlTransient
    public int getResponseCounterTotal() {
        return responseCounterTotal.get();
    }

    /**
     * Get the amount of times this Recording has returned a response since the last statistics gathering.
     * @return The amount of times this Recording has returned a response since the last statistics gathering.
     */
    @XmlTransient
    public int getResponseCounterCurrent() {
        return responseCounterCurrent.get();
    }

    /**
     * Get the amount of times this Recording has returned a response since the last statistics gathering and reset the counter at the same time.
     * This is used for collecting statistics about this Recording, and should only be called when periodically collecting statistics.
     * @return The amount of times this Recording has returned a response since the last statistics gathering.
     */
    @XmlTransient
    public int getAndResetResponseCounterCurrent() {
        return responseCounterCurrent.getAndSet(0);
    }

    /**
     * Get the next ResponseTemplate for a request that matches this Recording.
     * @return The next ResponseTemplate for this Recording.
     */
    @XmlTransient
    public ResponseTemplate getNextResponseTemplate() {
        if (responseTemplates.isEmpty()) {
            log.info("No response saved in this recording, the recording file is invalid: {}", filename);
            return null;
        }
        /* increase response counters */
        responseCounterCurrent.getAndIncrement();
        int counterTotal = responseCounterTotal.getAndIncrement();
        /* find right response, currently we're just cycling through the responseTemplates */
        int responseId = counterTotal % responseTemplates.size();
        log.info("Found matching recording ({})", filename);
        if (responseTemplates.size() > 1) {
            log.info("Recording have multiple responses, using Response {} of {}", responseId + 1, responseTemplates.size());
        }
        return responseTemplates.get(responseId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "File: " + filename + ", Delay: " + delayStrategy + " " + delayValues + ", Responses: " + responseTemplates.size();
    }
}
