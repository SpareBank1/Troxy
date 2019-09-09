# Lage egne filter
Hvis Troxy og de medfølgende filtrene ikke dekker det behovet man har er det mulig å lage
et eget filter.

## Implementasjon av filter
For å opprette et eget filter behøver du Troxy SDK som følger med leveransen av Troxy.
I denne pakken finner du klassen `Filter` som ditt filter må utvide.

### Filter.loadConfig(`Map<String, Map<String, String>> configuration`)
Denne metoden må implementeres av alle filter.
Metoden blir kalt ved oppstart av Troxy og når konfigurasjon lastes inn på nytt.

Parameter `configuration` er en mapping av grupper til en mapping av nøkler og verdier.
Grupper, nøkler og verdier er alle oppgitt som strengverdier.
En enklere fremstilling av parameteren: `Map<Group, Map<Key, Value>>`

### Metoder som filter opsjonelt kan implementere
Disse metodene kan filter implementere ved behov. De er listet i den kronologiske
rekkefølgen som de blir kjørt når Troxy mottar en forespørsel fra en klient.

#### Filter.filterClientRequest(`Request request, String group`)
Denne metoden kalles når Troxy mottar en forespørsel og før noe mer blir gjort med den.
Merk at andre filter kan ha modifisert `request`-objektet, så ditt filter bør håndtere
uvanlige verdier i objektet, for eksempel `null`-verdier.
Strengen `group` er navnet på den aktive konfigurasjonsgruppen, som definert i
konfigurasjonsfilen: `filter.<filter>.<group>.request.*`.
Dersom det ikke er noen aktiv konfigurasjonsgruppe (global konfigurasjon for filteret)
så er denne verden satt til `null`.

#### Filter.filterServerRequest(`Request request, String group`)
Dersom Troxy skal gå mot et baksystem kalles denne metoden på filter rett før Request
sendes til baksystemet. Om Troxy ikke videresender Request til et baksystem vil heller
ikke denne metoden bli kalt.

#### filterServerResponse(`Response response, String group`)
Hvis Troxy har sendt en forespørsel til et baksystem og fått et svar kalles denne
metoden før Troxy gjør noe mer med den (som f.eks. å lage nytt opptak).
I likhet med de to foregående metodene er `group` navnet på den aktive
konfigurasjonsgruppen, men i dette tilfellet er det `filter.<filter>.<group>.response.`
som dikterer hvilken konfigurasjonsgruppe det er.

#### filterNewRecording(`Recording recording`)
Om Troxy er satt i et opptaksmodus og det opprettes et nytt opptak blir denne metoden
kalt før opptaket serialiseres til disk.

#### filterClientResponse(`Response response, String group`)
Dette er den siste metoden som blir kalt før Troxy sender svar tilbake til klienten.

## Komplett eksempel på et filter
```
package no.sb1.troxy.filter;

import java.util.HashMap;
import java.util.Map;
import no.sb1.troxy.http.common.Filter;
import no.sb1.troxy.http.common.Request;
import no.sb1.troxy.http.common.Response;
import no.sb1.troxy.record.v2.Recording;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Set a delay for the Recording upon creating it.
 * This filter will not delay a response, merely set the delay values and strategy used for playback.
 */
public class SetRecordingDelay extends Filter {
    /**
     * Logger for this class.
     */
    private static final Logger log = LogManager.getLogger();
    /**
     * Delay strategy used for the new recordings.
     */
    private static Recording.DelayStrategy delayStrategy = Recording.DelayStrategy.NONE;
    /**
     * Multipliers for delay.
     */
    private static Map<Recording.DelayValueKeys, Double> delayValueMultipliers = new HashMap<>();
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
            delayStrategy = Recording.DelayStrategy.valueOf(values.get("delay_strategy"));
        } catch (IllegalArgumentException e) {
            log.warn("You need to specify delay strategy, it must be one of: {}", Recording.DelayStrategy.values(), e);
        }
        for (Recording.DelayValueKeys key : delayStrategy.getValidDelayValueKeys()) {
            try {
                double delayValue = Double.parseDouble(values.get(key.toString().toLowerCase() + "_multiplier"));
                delayValueMultipliers.put(key, delayValue);
            } catch (NumberFormatException e) {
                log.info("Unable to read configuration value '{}_multiplier', which is needed by delay strategy {}, setting value to 1.0", key.toString().toLowerCase(), delayStrategy, e);
                delayValueMultipliers.put(key, 1.0);
            }
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
        recording.setDelayStrategy(delayStrategy);
        for (Map.Entry<Recording.DelayValueKeys, Double> entry : delayValueMultipliers.entrySet()) {
            long value = (long) (responseTime * entry.getValue());
            if (value < 0)
                value = 0;
            recording.getDelayValues().put(entry.getKey(), value);
        }
        log.debug("Setting delay strategy to {} {}", delayStrategy, recording.getDelayValues());
    }
}
```