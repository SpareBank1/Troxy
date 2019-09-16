package no.sb1.troxy.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import no.sb1.troxy.http.common.Filter;
import no.sb1.troxy.record.v3.Recording;
import no.sb1.troxy.record.v3.RequestPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A filter that search for text matching a regular expression, and then keeps that text.
 */
public class KeepRegexNewRecording extends Filter {
    /**
     * Logger for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(KeepRegexNewRecording.class);
    /**
     * When this filter is executed certain characters have been escaped, we need to unescape these before we match,
     * and the escape them again (except surrounding text we replaced with a regex).
     */
    private static final Pattern UNESCAPE_BACKSLASH_PATTERN = Pattern.compile("\\\\(.)");

    private static List<Pattern> protocolPatterns = new ArrayList<>();
    private static List<Pattern> hostPatterns = new ArrayList<>();
    private static List<Pattern> portPatterns = new ArrayList<>();
    private static List<Pattern> pathPatterns = new ArrayList<>();
    private static List<Pattern> queryPatterns = new ArrayList<>();
    private static List<Pattern> methodPatterns = new ArrayList<>();
    private static List<Pattern> headerPatterns = new ArrayList<>();
    private static List<Pattern> contentPatterns = new ArrayList<>();

    @Override
    protected void loadConfig(Map<String, Map<String, String>> configuration) {
        Map<String, String> entries = configuration.get(null);
        if (entries == null) {
            log.warn("No global configuration values found");
            return;
        }
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            try {
                Pattern pattern = Pattern.compile(entry.getValue());
                String key = entry.getKey();
                int dotPos = key.indexOf('.');
                switch (key.substring(0, dotPos > 0 ? dotPos : key.length())) {
                    case "protocol":
                        protocolPatterns.add(pattern);
                        break;

                    case "host":
                        hostPatterns.add(pattern);
                        break;

                    case "port":
                        portPatterns.add(pattern);
                        break;

                    case "path":
                        pathPatterns.add(pattern);
                        break;

                    case "query":
                        queryPatterns.add(pattern);
                        break;

                    case "method":
                        methodPatterns.add(pattern);
                        break;

                    case "header":
                        headerPatterns.add(pattern);
                        break;

                    case "content":
                        contentPatterns.add(pattern);
                        break;

                    default:
                        log.warn("Unknown configuration key: {}", key);
                        break;
                }
            } catch (PatternSyntaxException e) {
                log.warn("Unable to parse regex '{}' for key '{}'", entry.getValue(), entry.getKey());
            }
        }
    }

    @Override
    protected void filterNewRecording(Recording recording) {
        RequestPattern requestPattern = recording.getRequestPattern();
        requestPattern.setProtocol(getReplacedField(requestPattern.getProtocol(), protocolPatterns));
        requestPattern.setHost(getReplacedField(requestPattern.getHost(), hostPatterns));
        requestPattern.setPort(getReplacedField(requestPattern.getPort(), portPatterns));
        requestPattern.setPath(getReplacedField(requestPattern.getPath(), pathPatterns));
        requestPattern.setQuery(getReplacedField(requestPattern.getQuery(), queryPatterns));
        requestPattern.setHeader(getReplacedField(requestPattern.getHeader(), headerPatterns));
        requestPattern.setMethod(getReplacedField(requestPattern.getMethod(), methodPatterns));
        requestPattern.setContent(getReplacedField(requestPattern.getContent(), contentPatterns));
    }

    private String getReplacedField(String input, List<Pattern> patterns) {
        // do nothing if no pattern
        if (patterns.isEmpty()) {
            return input;
        }

        // unescape input and chop off leading "^" and trailing "$"
        String unescaped = unescape(input.substring(1, input.length() - 1));
        if (unescaped == null) {
            log.warn("Unable to unescape input '{}', can't search & replace text with regular expressions", input);
            return input;
        }

        // find all positions with regular expressions and keep the text, using TreeSet to order all matchings by start()
        Set<MatchPosition> matchPositions = new TreeSet<>();
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(unescaped);
            while (matcher.find()) {
                matchPositions.add(new MatchPosition(matcher.start(), matcher.end(), pattern.pattern()));
            }
        }

        // keep all findings, ignoring everything else
        String output = "";
        for (MatchPosition matchPosition : matchPositions) {
            output += RequestPattern.escape(unescaped.substring(matchPosition.start, matchPosition.end));
            output += ".*?";
        }

        // add back "^" and "$"
        output = "^.*?" + output;
        if (!output.endsWith("$"))
            output += "$";
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

    private class MatchPosition implements Comparable<MatchPosition> {
        public int start;
        public int end;
        public String pattern;

        public MatchPosition(int start, int end, String pattern) {
            this.start = start;
            this.end = end;
            this.pattern = pattern;
        }

        @Override
        public int compareTo(MatchPosition matchPosition) {
            return start - matchPosition.start;
        }
    }
}
