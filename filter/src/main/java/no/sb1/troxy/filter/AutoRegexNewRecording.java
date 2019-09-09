package no.sb1.troxy.filter;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import no.sb1.troxy.http.common.Filter;
import no.sb1.troxy.record.v3.Recording;
import no.sb1.troxy.record.v3.RequestPattern;
import no.sb1.troxy.record.v3.ResponseTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A filter that search for text matching a regular expression, and then replacing the text with the regular expression itself.
 */
public class AutoRegexNewRecording extends Filter {
    /**
     * Logger for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(AutoRegexNewRecording.class);
    /**
     * When this filter is executed certain characters have been escaped, we need to unescape these before we match,
     * and the escape them again (except what we replaced with a regex).
     */
    private static final Pattern UNESCAPE_BACKSLASH_PATTERN = Pattern.compile("\\\\(.)");

    private static Map<Pattern, String> protocolPatterns = new HashMap<>();
    private static Map<Pattern, String> hostPatterns = new HashMap<>();
    private static Map<Pattern, String> portPatterns = new HashMap<>();
    private static Map<Pattern, String> pathPatterns = new HashMap<>();
    private static Map<Pattern, String> queryPatterns = new HashMap<>();
    private static Map<Pattern, String> methodPatterns = new HashMap<>();
    private static Map<Pattern, String> headerPatterns = new HashMap<>();
    private static Map<Pattern, String> contentPatterns = new HashMap<>();

    private static Map<Pattern, String> responseCodePatterns = new HashMap<>();
    private static Map<Pattern, String> responseHeaderPatterns = new HashMap<>();
    private static Map<Pattern, String> responseContentPatterns = new HashMap<>();

    @Override
    protected void loadConfig(Map<String, Map<String, String>> configuration) {
        Map<String, String> entries = configuration.get(null);
        if (entries == null) {
            log.warn("No global configuration values found");
            return;
        }
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            try {
                String value = entry.getValue();
                int delimiterPos = -1;
                String replacement = null;
                while ((delimiterPos = value.indexOf(';', delimiterPos + 1)) > 0) {
                    int delimiters = 0;
                    for (; delimiterPos < value.length(); ++delimiterPos) {
                        if (value.charAt(delimiterPos) != ';')
                            break;
                        ++delimiters;
                    }
                    if (delimiterPos > 0 && delimiters % 2 == 1) {
                        // odd number of delimiters, we want to insert a custom replacement
                        replacement = value.substring(delimiterPos);
                        value = value.substring(0, delimiterPos - 1);
                    }
                }
                Pattern pattern = Pattern.compile(value.replace(";;", ";"));
                String key = entry.getKey();
                int dotPos = key.indexOf('.');
                switch (key.substring(0, dotPos > 0 ? dotPos : key.length())) {
                    case "protocol":
                        protocolPatterns.put(pattern, replacement);
                        break;

                    case "host":
                        hostPatterns.put(pattern, replacement);
                        break;

                    case "port":
                        portPatterns.put(pattern, replacement);
                        break;

                    case "path":
                        pathPatterns.put(pattern, replacement);
                        break;

                    case "query":
                        queryPatterns.put(pattern, replacement);
                        break;

                    case "method":
                        methodPatterns.put(pattern, replacement);
                        break;

                    case "header":
                        headerPatterns.put(pattern, replacement);
                        break;

                    case "content":
                        contentPatterns.put(pattern, replacement);
                        break;

                    case "response_code":
                        responseCodePatterns.put(pattern, replacement);
                        break;

                    case "response_header":
                        responseHeaderPatterns.put(pattern, replacement);
                        break;

                    case "response_content":
                        responseContentPatterns.put(pattern, replacement);
                        break;

                    default:
                        log.warn("Unknown configuration key: {}", key);
                        break;
                }
            } catch (PatternSyntaxException e) {
                log.warn("Unable to parse regex '{}' for key '{}'", entry.getValue(), entry.getKey(), e);
            }
        }
    }

    @Override
    protected void filterNewRecording(Recording recording) {
        RequestPattern requestPattern = recording.getRequestPattern();
        requestPattern.setProtocol(getReplacedField(requestPattern.getProtocol(), protocolPatterns, true));
        requestPattern.setHost(getReplacedField(requestPattern.getHost(), hostPatterns, true));
        requestPattern.setPort(getReplacedField(requestPattern.getPort(), portPatterns, true));
        requestPattern.setPath(getReplacedField(requestPattern.getPath(), pathPatterns, true));
        requestPattern.setQuery(getReplacedField(requestPattern.getQuery(), queryPatterns, true));
        requestPattern.setHeader(getReplacedField(requestPattern.getHeader(), headerPatterns, true));
        requestPattern.setMethod(getReplacedField(requestPattern.getMethod(), methodPatterns, true));
        requestPattern.setContent(getReplacedField(requestPattern.getContent(), contentPatterns, true));

        ResponseTemplate responseTemplate = recording.getNextResponseTemplate();
        responseTemplate.setCode(getReplacedField(responseTemplate.getCode(), responseCodePatterns, false));
        responseTemplate.setHeader(getReplacedField(responseTemplate.getHeader(), responseHeaderPatterns, false));
        responseTemplate.setContent(getReplacedField(responseTemplate.getContent(), responseContentPatterns, false));
    }

    private String getReplacedField(String input, Map<Pattern, String> patterns, boolean escapeText) {
        // do nothing if no pattern
        if (patterns.isEmpty())
            return input;

        // unescape input and chop off leading "^" and trailing "$"
        String text = escapeText ? unescape(input.substring(1, input.length() - 1)) : input;
        if (text == null) {
            log.warn("Unable to unescape input '{}', can't search & replace text with regular expressions", input);
            return input;
        }

        Map<Integer, ExtendedMatcher> extendedMatchers = new TreeMap<>();
        for (Map.Entry<Pattern, String> pattern : patterns.entrySet()) {
            Matcher matcher = pattern.getKey().matcher(text);
            if (matcher.find())
                extendedMatchers.put(matcher.start(), new ExtendedMatcher(matcher, pattern.getValue()));
        }

        String output = "";
        int lastEnd = 0;
        while (!extendedMatchers.isEmpty()) {
            Map<Integer, ExtendedMatcher> newExtendedMatchers = new TreeMap<>();
            for (Map.Entry<Integer, ExtendedMatcher> extendedMatcher : extendedMatchers.entrySet()) {
                Matcher matcher = extendedMatcher.getValue().matcher;
                String replacement = extendedMatcher.getValue().replacement;
                if (lastEnd < matcher.start())
                    output += escapeText ? RequestPattern.escape(text.substring(lastEnd, matcher.start())): text.substring(lastEnd, matcher.start());
                if (replacement == null) {
                    output += matcher.pattern().pattern();
                    if (!escapeText)
                        log.warn("No replacement set for response field. Inserting regular expression, but this is probably not what you want!");
                } else {
                    String tmpReplacement = replacement;
                    // TODO?: user may not add a literal "$<digit>" in replacement
                    // TODO?: $10 and above won't work
                    for (int groupIndex = 0; groupIndex < matcher.groupCount(); ++groupIndex)
                        tmpReplacement = tmpReplacement.replace("$" + (groupIndex + 1), matcher.group(groupIndex + 1));
                    output += tmpReplacement;
                }
                lastEnd = matcher.end();
                if (matcher.find() && matcher.start() != text.length())
                    newExtendedMatchers.put(matcher.start(), new ExtendedMatcher(matcher, replacement));
            }
            extendedMatchers = newExtendedMatchers;
        }
        output += escapeText ? RequestPattern.escape(text.substring(lastEnd)) : text.substring(lastEnd);

        if (escapeText) {
            // add back "^" and "$" unless it was already added by the pattern
            if (!output.startsWith("^"))
                output = "^" + output;
            if (!output.endsWith("$"))
                output += "$";
        }
        return output;
    }

    /**
     * Unescape characters that have been escaped to prevent them from being mistaken as a regular expression.
     * @param text The text to be unescaped.
     * @return An unescaped version of the given text.
     */
    private static String unescape(String text) {
        if (text == null)
            return null;
        return UNESCAPE_BACKSLASH_PATTERN.matcher(text).replaceAll("$1");
    }

    private class ExtendedMatcher {
        public Matcher matcher;
        public String replacement;

        public ExtendedMatcher(Matcher matcher, String replacement) {
            this.matcher = matcher;
            this.replacement = replacement;
        }
    }
}
