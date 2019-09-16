package no.sb1.troxy.filter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import no.sb1.troxy.http.common.Filter;
import no.sb1.troxy.http.common.Request;
import no.sb1.troxy.http.common.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A filter that replace some text based on a key found somewhere else.
 */
public class KeyBasedReplace extends Filter {
    /**
     * Logger for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(KeyBasedReplace.class);

    private static Map<String, GroupConfig> groupConfigs = new HashMap<>();

    private String key;

    @Override
    protected void loadConfig(Map<String, Map<String, String>> configuration) {
        for (Map.Entry<String, Map<String, String>> group : configuration.entrySet()) {
            String mappingFile = group.getValue().get("mapping_file");
            try {
                GroupConfig groupConfig = new GroupConfig();
                // read in mapping file
                for (String line : Files.readAllLines(new File(mappingFile).toPath())) {
                    String[] values = line.split(",");
                    if (values.length <= 0) {
                        log.warn("Unable to parse line: {}", line);
                        continue;
                    }
                    if (groupConfig.keyValuesMapping.containsKey(values[0]))
                        log.warn("Duplicate key in mapping file: {}", values[0]);
                    groupConfig.keyValuesMapping.put(values[0], Arrays.asList(values).subList(1, values.length));
                }

                // set key pattern
                // TODO: may throw NPE, and this seemingly crash Troxy. not good
                groupConfig.keyPattern = Pattern.compile(group.getValue().get("key"));

                // add replace patterns
                for (Map.Entry<String, String> entry : group.getValue().entrySet()) {
                    if (entry.getKey().startsWith("replace"))
                        groupConfig.replacePatterns.add(Pattern.compile(entry.getValue()));
                }

                // add config to HashMap
                groupConfigs.put(group.getKey(), groupConfig);
            } catch (IOException e) {
                log.warn("Unable to read mapping file: {}", mappingFile, e);
            }
        }
    }

    @Override
    protected void filterClientRequest(Request request, String group) {
        GroupConfig groupConfig = groupConfigs.get(group);
        if (groupConfig == null)
            return;
        // find key
        key = getKey(request.getPath(), groupConfig.keyPattern);
        if (key != null) {
            log.debug("Found key '{}' in request path", key);
        } else {
            key = getKey(request.getQuery(), groupConfig.keyPattern);
            if (key != null) {
                log.debug("Found key '{}' in request query", key);
            } else {
                key = getKey(request.getHeader(), groupConfig.keyPattern);
                if (key != null) {
                    log.debug("Found key '{}' in request header", key);
                } else {
                    key = getKey(request.getContent(), groupConfig.keyPattern);
                    if (key != null)
                        log.debug("Found key '{}' in request content", key);
                }
            }
        }
    }

    @Override
    protected void filterClientResponse(Response response, String group) {
        GroupConfig groupConfig = groupConfigs.get(group);
        if (groupConfig == null)
            return;
        if (key == null) {
            log.debug("No key found in request, no replacing done");
            return;
        }
        List<String> values = groupConfig.keyValuesMapping.get(key);
        if (values == null) {
            log.warn("Key '{}' does not exist in mapping file", key);
            return;
        }

        // replace values
        for (Pattern pattern : groupConfig.replacePatterns) {
            response.setCode(replaceValues(response.getCode(), pattern, values));
            response.setHeader(replaceValues(response.getHeader(), pattern, values));
            response.setContent(replaceValues(response.getContent(), pattern, values));
        }
    }

    private String getKey(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find())
            return matcher.groupCount() <= 1 ? matcher.group(matcher.groupCount()) : matcher.group("key");
        return null;
    }

    private String replaceValues(String text, Pattern pattern, List<String> values) {
        Matcher matcher = pattern.matcher(text);
        List<ReplaceToken> replaceTokens = new ArrayList<>();
        while (matcher.find()) {
            for (int i = 0; i < values.size(); ++i) {
                String value = values.get(i);
                try {
                    int start = matcher.start("v" + (i + 1));
                    int end = matcher.end("v" + (i + 1));
                    if (start >= 0 && end >= 0) {
                        log.debug("Replacing '{}' with '{}'", text.substring(start, end), text.substring(start, end));
                        replaceTokens.add(new ReplaceToken(start, end, value));
                    } else {
                        log.debug("Capturing group named 'v{}' didn't match anything, nothing replaced", i + 1);
                    }
                } catch (IllegalArgumentException e) {
                    log.debug("No capturing group named 'v{}' in pattern '{}'", i + 1, pattern.pattern());
                }
            }
        }

        while (!replaceTokens.isEmpty()) {
            int lastPosIndex = 0;
            for (int i = 1; i < replaceTokens.size(); ++i) {
                if (replaceTokens.get(i).start > replaceTokens.get(lastPosIndex).start)
                    lastPosIndex = i;
            }
            ReplaceToken replaceToken = replaceTokens.remove(lastPosIndex);
            text = text.substring(0, replaceToken.start) + replaceToken.value + text.substring(replaceToken.end);
        }

        return text;
    }

    private class GroupConfig {
        private Map<String, List<String>> keyValuesMapping = new HashMap<>();
        private Pattern keyPattern;
        private List<Pattern> replacePatterns = new ArrayList<>();
    }

    private class ReplaceToken {
        private int start;
        private int end;
        private String value;

        public ReplaceToken(int start, int end, String value) {
            this.start = start;
            this.end = end;
            this.value = value;
        }
    }
}
