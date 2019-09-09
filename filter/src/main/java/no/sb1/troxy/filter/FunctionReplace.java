package no.sb1.troxy.filter;

import no.sb1.troxy.http.common.Filter;
import no.sb1.troxy.http.common.Request;
import no.sb1.troxy.http.common.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A filter that replace a key with a value returned from a function
 */
public class FunctionReplace extends Filter {

    private static final Logger log = LoggerFactory.getLogger(FunctionReplace.class);
    private static List<FunctionReplace.Replace> replacePatterns = new ArrayList<>();

    @Override
    protected void loadConfig(Map<String, Map<String, String>> configuration) {

        Map<String, String> entries = configuration.get(null);
        if (entries == null) {
            log.warn("No global configuration values found");
            return;
        }

        for (Map.Entry<String, String> entry : entries.entrySet()) {
            try {
                String pattern = entry.getValue();
                String key = entry.getKey();
                String function = key.substring(0, key.indexOf('.') );
                replacePatterns.add(new Replace(function, pattern));

            } catch (Exception e) {
                log.warn("Not able to all settings properties. Pattern: '{}' or Key: '{}'", entry.getValue(), entry.getKey());
            }
        }
    }

    @Override
    protected void filterClientRequest(Request request, String group) {
    }

    @Override
    protected void filterClientResponse(Response response, String group) {

        for (Replace functionValues : replacePatterns) {

            switch (functionValues.function){
                case "now":
                    response.setHeader(replaceKeysWithDatePattern(response.getHeader(), functionValues.keyPattern));
                    response.setContent(replaceKeysWithDatePattern(response.getContent(), functionValues.keyPattern));
                    break;
                default:
                    log.warn("Filter FunctionReplace: Unknown function " + functionValues.function + " specified in properties");
                    break;
            }
        }

    }

    private String replaceKeysWithDatePattern(String content, String keyAndFormatToReplace){

        if(keyAndFormatToReplace.isEmpty()){
            log.debug("Filter FunctionReplace: No valid config set, original content returned");
            return content;
        }

        String[] configValues = keyAndFormatToReplace.split(";");

        String dateFormatterPattern = "yyyy-MM-dd'T'HH:mm:ss.SSS";

        if(configValues.length == 1){
            log.debug("Filter FunctionReplace: No separator set, defaulting to dateformat ISO_DATE_TIME = yyyy-MM-dd'T'HH:mm:ss.SSS");
        }
        else{
            dateFormatterPattern = configValues[1];
        }

        String keyToReplace = configValues[0];

        String dateNow;
        try {

            if(configValues.length > 2){
                String offset = configValues[2];
                LocalDateTime dateTimeWithOffset = getDateToReplace(offset);

                if(dateTimeWithOffset == null){
                    log.info("Filter FunctionReplace: No valid unit set on offset. Offset should be in format 1y, 1M, 1d, 1H, 1m or 1s. Provided offset: " + offset);
                    return content;
                }
                else {
                    dateNow = dateTimeWithOffset.format(DateTimeFormatter.ofPattern(dateFormatterPattern));
                }
            }
            else{
                dateNow = LocalDateTime.now().format(DateTimeFormatter.ofPattern(dateFormatterPattern));
            }

        }
        catch (Exception e){
            log.info("Filter FunctionReplace: Not able to parse LocalDateTime to format or add offset value. Provided Datetime pattern: " + dateFormatterPattern);
            return content;
        }

        if (!content.contains(keyToReplace)) {
            return content;
        }
        else{
            log.debug("Replacing " + keyToReplace + " with " + dateNow);
            return content.replaceAll(keyToReplace, dateNow);
        }
    }

    private LocalDateTime getDateToReplace(String offset){
        LocalDateTime dateNow = null;
        try {
            if (offset.contains("y")) {
                dateNow = LocalDateTime.now().plusYears(Integer.parseInt(offset.replace("y", "")));
            } else if (offset.contains("M")) {
                dateNow = LocalDateTime.now().plusMonths(Integer.parseInt(offset.replace("M", "")));
            } else if (offset.contains("d")) {
                dateNow = LocalDateTime.now().plusDays(Integer.parseInt(offset.replace("d", "")));
            } else if (offset.contains("H")) {
                dateNow = LocalDateTime.now().plusHours(Integer.parseInt(offset.replace("H", "")));
            } else if (offset.contains("m")) {
                dateNow = LocalDateTime.now().plusMinutes(Integer.parseInt(offset.replace("m", "")));
            } else if (offset.contains("s")) {
                dateNow = LocalDateTime.now().plusSeconds(Integer.parseInt(offset.replace("s", "")));
            }
        }
        catch (Exception e) {
            log.info("Filter FunctionReplace: Parsing offset to a number failed. Provided offset: " + offset);
            return null;
        }

        return dateNow;
    }

    private class Replace {
        Replace(String function, String keyPattern){
            this.function = function;
            this.keyPattern = keyPattern;
        }
        private String function;
        private String keyPattern;
    }
}
