package no.sb1.troxy.filter;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import no.sb1.troxy.http.common.Filter;
import no.sb1.troxy.http.common.Response;
import no.sb1.troxy.record.v3.ResponseTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A filter for delaying Responses based on configuration rather than recorded delay.

 */
public class DelayResponse extends Filter {
    /**
     * Logger for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(DelayResponse.class);
    /**
     * Random number generator.
     */
    private static Random random = new Random();
    /**
     * Map of delays for each group.
     */
    private static Map<String, Delay> groupDelays = new HashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadConfig(Map<String, Map<String, String>> configuration) {
        groupDelays.clear();
        for (Map.Entry<String, Map<String, String>> group : configuration.entrySet()) {
            String value = group.getValue().get("delay");
            if (value == null)
                continue;
            String[] values = value.replace(" ",  "").split(";");
            try {
                Delay delay = new Delay();
                delay.strategy = ResponseTemplate.DelayStrategy.valueOf(values[0].toUpperCase());
                if (delay.strategy == ResponseTemplate.DelayStrategy.FIXED) {
                    delay.max = Integer.parseInt(values[1]);
                } else if (delay.strategy == ResponseTemplate.DelayStrategy.RANDOM) {
                    delay.min = Integer.parseInt(values[1]);
                    delay.max = Integer.parseInt(values[2]);
                } else if (delay.strategy == ResponseTemplate.DelayStrategy.NORMAL) {
                    delay.min = Integer.parseInt(values[1]);
                    delay.max = Integer.parseInt(values[2]);
                } else if (delay.strategy == ResponseTemplate.DelayStrategy.EXPONENTIAL) {
                    delay.min = Integer.parseInt(values[1]);
                    delay.mean = Integer.parseInt(values[2]);
                    delay.max = Integer.parseInt(values[3]);
                }
                if (delay.min > delay.mean || delay.min > delay.max || delay.mean > delay.max)
                    log.warn("Inconsistent delay values for config \"{}\" in group \"{}\". Values must be: min <= mean <= max. Response will not be delayed", value, group.getKey());
                else
                    groupDelays.put(group.getKey(), delay);
            } catch (ArrayIndexOutOfBoundsException e) {
                log.warn("Too few parameters for delay strategy, failed parsing \"{}\". Won't delay Response for group \"{}\"", value, group.getKey());
            } catch (IllegalArgumentException e) {
                log.warn("Failed parsing \"{}\". Won't delay Response for group \"{}\"", value, group.getKey());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void filterClientResponse(Response response, String group) {
        Delay delay = groupDelays.get(group);
        if (delay == null)
            return;
        switch (delay.strategy) {
            case NONE:
                response.setDelay(0);
                break;

            case FIXED:
                response.setDelay(delay.max);
                break;

            case RANDOM:
                response.setDelay(random.nextInt(delay.max - delay.min + 1) + delay.min);
                break;

            case NORMAL:
                double range = (double) (delay.max - delay.min) / 2.0;
                /* ensure that the sleep time is within min/max (Random.nextGaussian() won't guarantee this) */
                int sleepTime;
                do {
                    sleepTime = delay.min + (int) (random.nextGaussian() * range + range);
                } while (sleepTime < delay.min || sleepTime > delay.max);
                response.setDelay(sleepTime);
                break;

            case EXPONENTIAL:
                response.setDelay(Math.min((int) (delay.min - delay.mean * Math.log(1.0 - Math.random())), delay.max));
                break;

            default:
                log.warn("Unknown delay strategy: {}", delay.strategy);
                break;
        }
    }

    private class Delay {
        public ResponseTemplate.DelayStrategy strategy;
        public int min;
        public int mean;
        public int max;
    }
}
