package no.sb1.troxy.filter;

import java.util.Map;
import no.sb1.troxy.http.common.Filter;
import no.sb1.troxy.http.common.Request;
import no.sb1.troxy.http.common.Response;
import no.sb1.troxy.record.v3.Recording;
import no.sb1.troxy.record.v3.ResponseTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Set a delay for the Recording upon creating it.
 * This filter will not delay a response, merely set the delay values and strategy used for playback.
 */
public class SetRecordingDelay extends Filter {
    /**
     * Logger for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(SetRecordingDelay.class);
    /**
     * Delay strategy used for the new recordings.
     */
    private static ResponseTemplate.DelayStrategy delayStrategy = ResponseTemplate.DelayStrategy.NONE;
    /**
     * Multipliers for min delay.
     */
    private static double multiplierMin;
    /**
     * Multipliers for mean delay.
     */
    private static double multiplierMean;
    /**
     * Multipliers for max delay.
     */
    private static double multiplierMax;
    /**
     * Variable used to calculate the response time of the request to the server.
     */
    private long responseTime;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadConfig(Map<String, Map<String, String>> configuration) {
        /* there are no groups for this filter, all config values are global */
        Map<String, String> values = configuration.get(null);
        if (values == null) {
            log.warn("No global configuration values found");
            return;
        }
        try {
            delayStrategy = ResponseTemplate.DelayStrategy.valueOf(values.get("delay_strategy"));
        } catch (IllegalArgumentException e) {
            log.warn("You need to specify delay strategy, it must be one of: {}", ResponseTemplate.DelayStrategy.values(), e);
        }
        try {
            multiplierMin = Double.parseDouble(values.get("min_multiplier"));
        } catch (NumberFormatException e) {
            log.info("Unable to read configuration value 'min_multiplier', setting value to 1.0", e);
            multiplierMin = 1.0;
        }
        try {
            multiplierMean = Double.parseDouble(values.get("mean_multiplier"));
        } catch (NumberFormatException e) {
            log.info("Unable to read configuration value 'mean_multiplier', setting value to 1.0", e);
            multiplierMean = 1.0;
        }
        try {
            multiplierMax = Double.parseDouble(values.get("max_multiplier"));
        } catch (NumberFormatException e) {
            log.info("Unable to read configuration value 'max_multiplier', setting value to 1.0", e);
            multiplierMax = 1.0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void filterServerRequest(Request request, String group) {
        responseTime = System.currentTimeMillis();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void filterServerResponse(Response response, String group) {
        responseTime = System.currentTimeMillis() - responseTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void filterNewRecording(Recording recording) {
        for (ResponseTemplate responseTemplate : recording.getResponseTemplates()) {
            responseTemplate.setDelayStrategy(delayStrategy);
            responseTemplate.setDelayMin((long) (responseTime * multiplierMin));
            responseTemplate.setDelayMean((long) (responseTime * multiplierMean));
            responseTemplate.setDelayMax((long) (responseTime * multiplierMax));
            log.debug("Setting delay strategy to {}[{}, {}, {}]", delayStrategy, responseTemplate.getDelayMin(), responseTemplate.getDelayMean(), responseTemplate.getDelayMax());
        }
    }
}
