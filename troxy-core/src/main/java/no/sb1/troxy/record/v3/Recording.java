package no.sb1.troxy.record.v3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlTransient;

/**
 * This is a container class for a recording.
 * It contains the original request, and a RequestPattern (modified request) and a list of original responseTemplates and ResponseTemplates.
 * In addition metadata for a recording is stored in this file, such as filename, statistics and response delays.

 */
public class Recording {
    public enum ResponseStrategy {
        SEQUENTIAL {
            @Override
            public ResponseTemplate getNextResponseTemplate(List<ResponseTemplate> responseTemplates, int counter) {
                long totalWeight = getTotalWeight(responseTemplates);
                if (totalWeight <= 0)
                    return null;
                return findNextResponseTemplate(responseTemplates, (long) counter % (totalWeight));
            }
        },

        RANDOM {
            @Override
            public ResponseTemplate getNextResponseTemplate(List<ResponseTemplate> responseTemplates, int counter) {
                long totalWeight = getTotalWeight(responseTemplates);
                if (totalWeight <= 0)
                    return null;
                return findNextResponseTemplate(responseTemplates, (long) (Math.random() * (double) totalWeight));
            }
        };

        public abstract ResponseTemplate getNextResponseTemplate(List<ResponseTemplate> responseTemplates, int counter);

        private static long getTotalWeight(List<ResponseTemplate> responseTemplates) {
            return responseTemplates.stream().collect(Collectors.summingLong(ResponseTemplate::getWeight));
        }

        private static ResponseTemplate findNextResponseTemplate(List<ResponseTemplate> responseTemplates, long weight) {
            int responseId = -1;
            int responses = responseTemplates.size();
            while (weight >= 0)
                weight -= responseTemplates.get(++responseId).getWeight();
            log.info("Recording have multiple responses, using Response {} of {}", responseId + 1, responses);
            return responseTemplates.get(responseId);
        }
    }
    /**
     * Logger for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(Recording.class);
    /**
     * Filename for this Recording.
     * When we load and save a recording we'll overwrite the record rather than create a new record.
     */
    private String filename;
    /**
     * Comment for this Recording.
     */
    private String comment = "";
    /**
     * The response strategy used for this Recording.
     */
    private ResponseStrategy responseStrategy = ResponseStrategy.SEQUENTIAL;
    /**
     * The RequestTemplate with regular expressions, used to match incoming requests.
     */
    private RequestPattern requestPattern;
    /**
     * The ResponseTemplates associated with the request.
     */
    private List<ResponseTemplate> responseTemplates = new ArrayList<>();
    /**
     * Counter for how many times a response has been returned since this Recording was loaded into Cache.
     */
    private AtomicInteger responseCounterTotal = new AtomicInteger(0);
    /**
     * Counter for how many times a response has been returned since the counter for this Recording last was reset.
     */
    private AtomicInteger responseCounterCurrent = new AtomicInteger(0);

    /**
     * Empty constructor.
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

        return recording;
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
     * Get the comment for this Recording.
     * @return The comment for this Recording.
     */
    public String getComment() {
        return comment;
    }

    /**
     * Set the comment for this Recording.
     * @param comment The filename for this Recording.
     */
    public void setComment(String comment) {
        this.comment = comment == null ? "" : comment;
    }

    /**
     * Get response strategy for this Recording.
     * @return Response strategy for this Recording.
     */
    public ResponseStrategy getResponseStrategy() {
        return responseStrategy;
    }

    /**
     * Set response strategy for this Recording.
     * @param responseStrategy Response strategy for this Recording.
     */
    public void setResponseStrategy(ResponseStrategy responseStrategy) {
        this.responseStrategy = responseStrategy;
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
     * Convenience method for adding a ResponseTemplate to the list of ResponseTemplates.
     * @param responseTemplate The ResponseTemplate to add to the list.
     */
    public void addResponse(ResponseTemplate responseTemplate) {
        if (responseTemplate != null)
            responseTemplates.add(responseTemplate);
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
        log.info("Found matching recording ({})", filename);
        if (responseTemplates.isEmpty())
            return null;
        /* increase current and total response counters */
        responseCounterCurrent.getAndIncrement();
        int counterTotal = responseCounterTotal.getAndIncrement();
        return responseStrategy.getNextResponseTemplate(responseTemplates, counterTotal);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "File: " + filename + ", Responses: " + responseTemplates.size();
    }
}
