package no.sb1.troxy.filter;

import no.sb1.troxy.record.v3.Recording;
import no.sb1.troxy.record.v3.RequestPattern;
import no.sb1.troxy.record.v3.ResponseTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static no.sb1.troxy.record.v3.RequestPattern.escape;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AutoRegexNewRecordingTest {
    private static RequestPattern createRequestPattern() {
        final RequestPattern requestPattern = new RequestPattern();
        requestPattern.setHeader(
                "^" + escape(
                        "Accept: */*\n" +
                                "Authorization: basic auth\n" +
                                "Cache-Control: no-cache\n" +
                                "Connection: close\n" +
                                "Content-Length: 1850\n" +
                                "Content-Type: text/xml; charset=UTF-8\n" +
                                "Cookie: delicious-cookie\n" +
                                "Forwarded: forwarded-for\n" +
                                "Host: host\n" +
                                "Pragma: no-cache\n" +
                                "SOAPAction: \"soap-action\"\n" +
                                "User-Agent: User-Agent\n" +
                                "X-CACHE-BALANCE-HEADER: balance-header\n" +
                                "X-Forwarded-For: 12.123.123.12\n" +
                                "X-Forwarded-Host: forwarded-host\n" +
                                "X-Forwarded-Port: 443\n" +
                                "X-Forwarded-Proto: https"
                ) + "$");
        return requestPattern;
    }

    private static RequestPattern createSimpleRequestPattern() {
        final RequestPattern requestPattern = new RequestPattern();
        requestPattern.setHeader(
                "^" + escape("Host: nettbank") + "$");
        return requestPattern;
    }

    @BeforeEach
    void clearAutoRegexNewRecording() {
        AutoRegexNewRecording.headerPatterns.clear();
    }

    @Test
    @DisplayName("loadConfig should load pattern without replacement value when valid config entry does not contain semicolon")
    void testLoadConfigWithoutReplacement() {
        final String headerPatternKey = "header.accept";
        final String headerPatternValue = "Accept: [^\\\\n]*";

        final Map<String, Map<String, String>> config = new HashMap<>();
        final Map<String, String> otherConfig = new HashMap<>();
        otherConfig.put(headerPatternKey, headerPatternValue);
        config.put(null, otherConfig);

        final AutoRegexNewRecording autoRegexNewRecording = new AutoRegexNewRecording();
        autoRegexNewRecording.loadConfig(config);

        assertNull(AutoRegexNewRecording.headerPatterns.get(0).getReplacement());
    }

    @Test
    @DisplayName("loadConfig should load pattern with replacement value when valid config entry contains semicolon")
    void testLoadConfigWithReplacement() {
        final String replacementValue = "Accept";
        final String headerPatternKey = "header.accept";
        final String headerPatternValue = "Accept: [^\\\\n]*;" + replacementValue;

        final Map<String, Map<String, String>> config = new HashMap<>();
        final Map<String, String> otherConfig = new HashMap<>();
        otherConfig.put(headerPatternKey, headerPatternValue);
        config.put(null, otherConfig);

        final AutoRegexNewRecording autoRegexNewRecording = new AutoRegexNewRecording();
        autoRegexNewRecording.loadConfig(config);

        assertEquals(replacementValue, AutoRegexNewRecording.headerPatterns.get(0).getReplacement());
    }

    @Test
    @DisplayName("loadConfig should load patterns according to config key")
    void testLoadConfigOrder() {
        final Map<String, Map<String, String>> config = new HashMap<>();
        final Map<String, String> otherConfig = new HashMap<>();
        otherConfig.put("header.xylophone", "fifth");
        otherConfig.put("header.accept", "second");
        otherConfig.put("header.bamse", "fourth");
        otherConfig.put("header.aardvark", "first");
        otherConfig.put("header.axolotl", "third");
        config.put(null, otherConfig);

        final AutoRegexNewRecording autoRegexNewRecording = new AutoRegexNewRecording();
        autoRegexNewRecording.loadConfig(config);

        assertEquals("first", AutoRegexNewRecording.headerPatterns.get(0).getPattern().pattern());
        assertEquals("second", AutoRegexNewRecording.headerPatterns.get(1).getPattern().pattern());
        assertEquals("third", AutoRegexNewRecording.headerPatterns.get(2).getPattern().pattern());
        assertEquals("fourth", AutoRegexNewRecording.headerPatterns.get(3).getPattern().pattern());
        assertEquals("fifth", AutoRegexNewRecording.headerPatterns.get(4).getPattern().pattern());
    }

    @Test
    @DisplayName("filterNewRecording should match and replace one pattern at a time")
    void testFilterNewRecordingOnebyOne() {
        final RequestPattern requestPattern = createSimpleRequestPattern();
        final Recording recording = new Recording(requestPattern, new ResponseTemplate());
        AutoRegexNewRecording.headerPatterns.add(new AutoRegexNewRecording.PatternWithReplacement("first_key", Pattern.compile("^Host: nettbank"), "Host: schnettbank"));
        AutoRegexNewRecording.headerPatterns.add(new AutoRegexNewRecording.PatternWithReplacement("second_key", Pattern.compile("^Host: schnettbank"), "Host: schpettbank"));
        final AutoRegexNewRecording autoRegexNewRecording = new AutoRegexNewRecording();

        autoRegexNewRecording.filterNewRecording(recording);

        final String expectedHeader = "^Host: schpettbank$";

        assertEquals(expectedHeader, requestPattern.getHeader());
    }

    @Test
    @DisplayName("filterNewRecording should replace headers matching pattern with the pattern itself when no replacement was specified")
    void testFilterNewRecordingWithoutReplacement() {
        final RequestPattern requestPattern = createRequestPattern();
        final Recording recording = new Recording(requestPattern, new ResponseTemplate());
        AutoRegexNewRecording.headerPatterns.add(new AutoRegexNewRecording.PatternWithReplacement("key", Pattern.compile("SOAPAction: (?<action>\"[a-zA-Z_-]+\")"), "SOAPAction: $1.*"));
        final AutoRegexNewRecording autoRegexNewRecording = new AutoRegexNewRecording();

        autoRegexNewRecording.filterNewRecording(recording);

        final String expectedHeader =
                "^Accept: \\*/\\*\n" +
                        "Authorization: basic auth\n" +
                        "Cache-Control: no-cache\n" +
                        "Connection: close\n" +
                        "Content-Length: 1850\n" +
                        "Content-Type: text/xml; charset=UTF-8\n" +
                        "Cookie: delicious-cookie\n" +
                        "Forwarded: forwarded-for\n" +
                        "Host: host\n" +
                        "Pragma: no-cache\n" +
                        "SOAPAction: \"soap-action\".*\n" +
                        "User-Agent: User-Agent\n" +
                        "X-CACHE-BALANCE-HEADER: balance-header\n" +
                        "X-Forwarded-For: 12\\.123\\.123\\.12\n" +
                        "X-Forwarded-Host: forwarded-host\n" +
                        "X-Forwarded-Port: 443\n" +
                        "X-Forwarded-Proto: https$";

        assertEquals(expectedHeader, requestPattern.getHeader());
    }

    @Test
    @DisplayName("filterNewRecording should replace headers matching pattern with replacement when replacement was specified")
    void testFilterNewRecordingWithReplacement() {
        final RequestPattern requestPattern = createRequestPattern();
        final Recording recording = new Recording(requestPattern, new ResponseTemplate());
        AutoRegexNewRecording.headerPatterns.add(new AutoRegexNewRecording.PatternWithReplacement("key", Pattern.compile("Accept: [^\\n]*"), "Accept: \"replaced value\""));
        final AutoRegexNewRecording autoRegexNewRecording = new AutoRegexNewRecording();

        autoRegexNewRecording.filterNewRecording(recording);

        final String expectedHeader =
                "^Accept: \"replaced value\"\n" +
                        "Authorization: basic auth\n" +
                        "Cache-Control: no-cache\n" +
                        "Connection: close\n" +
                        "Content-Length: 1850\n" +
                        "Content-Type: text/xml; charset=UTF-8\n" +
                        "Cookie: delicious-cookie\n" +
                        "Forwarded: forwarded-for\n" +
                        "Host: host\n" +
                        "Pragma: no-cache\n" +
                        "SOAPAction: \"soap-action\"\n" +
                        "User-Agent: User-Agent\n" +
                        "X-CACHE-BALANCE-HEADER: balance-header\n" +
                        "X-Forwarded-For: 12\\.123\\.123\\.12\n" +
                        "X-Forwarded-Host: forwarded-host\n" +
                        "X-Forwarded-Port: 443\n" +
                        "X-Forwarded-Proto: https$";
        assertEquals(expectedHeader, requestPattern.getHeader());
    }

    @Test
    @DisplayName("filterNewRecording should replace all matches with pattern itself when pattern has multiple matches and replacement is not specified")
    void testFilterNewRecordingMultipleMatchesWithoutReplacement() {
        final RequestPattern requestPattern = createRequestPattern();
        final Recording recording = new Recording(requestPattern, new ResponseTemplate());
        AutoRegexNewRecording.headerPatterns.add(new AutoRegexNewRecording.PatternWithReplacement("key", Pattern.compile("Host: [^\\n]*"), null));
        final AutoRegexNewRecording autoRegexNewRecording = new AutoRegexNewRecording();

        autoRegexNewRecording.filterNewRecording(recording);

        final String expectedHeader =
                "^Accept: \\*/\\*\n" +
                        "Authorization: basic auth\n" +
                        "Cache-Control: no-cache\n" +
                        "Connection: close\n" +
                        "Content-Length: 1850\n" +
                        "Content-Type: text/xml; charset=UTF-8\n" +
                        "Cookie: delicious-cookie\n" +
                        "Forwarded: forwarded-for\n" +
                        "Host: [^\\n]*\n" +
                        "Pragma: no-cache\n" +
                        "SOAPAction: \"soap-action\"\n" +
                        "User-Agent: User-Agent\n" +
                        "X-CACHE-BALANCE-HEADER: balance-header\n" +
                        "X-Forwarded-For: 12\\.123\\.123\\.12\n" +
                        "X-Forwarded-Host: [^\\n]*\n" +
                        "X-Forwarded-Port: 443\n" +
                        "X-Forwarded-Proto: https$";
        assertEquals(expectedHeader, requestPattern.getHeader());
    }

    @Test
    @DisplayName("filterNewRecording should replace all matches with replacement when pattern has multiple matches and replacement is specified")
    void testFilterNewRecordingMultipleMatchesWithReplacement() {
        final RequestPattern requestPattern = createRequestPattern();
        final Recording recording = new Recording(requestPattern, new ResponseTemplate());
        AutoRegexNewRecording.headerPatterns.add(new AutoRegexNewRecording.PatternWithReplacement("key", Pattern.compile("Host: [^\\n]*"), "Host: \"replaced value\""));
        final AutoRegexNewRecording autoRegexNewRecording = new AutoRegexNewRecording();

        autoRegexNewRecording.filterNewRecording(recording);

        final String expectedHeader =
                "^Accept: \\*/\\*\n" +
                        "Authorization: basic auth\n" +
                        "Cache-Control: no-cache\n" +
                        "Connection: close\n" +
                        "Content-Length: 1850\n" +
                        "Content-Type: text/xml; charset=UTF-8\n" +
                        "Cookie: delicious-cookie\n" +
                        "Forwarded: forwarded-for\n" +
                        "Host: \"replaced value\"\n" +
                        "Pragma: no-cache\n" +
                        "SOAPAction: \"soap-action\"\n" +
                        "User-Agent: User-Agent\n" +
                        "X-CACHE-BALANCE-HEADER: balance-header\n" +
                        "X-Forwarded-For: 12\\.123\\.123\\.12\n" +
                        "X-Forwarded-Host: \"replaced value\"\n" +
                        "X-Forwarded-Port: 443\n" +
                        "X-Forwarded-Proto: https$";
        assertEquals(expectedHeader, requestPattern.getHeader());
    }

    @Test
    @DisplayName("filterNewRecording should not replace anything when no patterns are present")
    void testFilterNewRecordingWithoutAnyPatterns() {
        final RequestPattern requestPattern = createRequestPattern();
        final Recording recording = new Recording(requestPattern, new ResponseTemplate());
        final AutoRegexNewRecording autoRegexNewRecording = new AutoRegexNewRecording();
        autoRegexNewRecording.filterNewRecording(recording);

        final String expectedHeader =
                "^Accept: \\*/\\*\n" +
                        "Authorization: basic auth\n" +
                        "Cache-Control: no-cache\n" +
                        "Connection: close\n" +
                        "Content-Length: 1850\n" +
                        "Content-Type: text/xml; charset=UTF-8\n" +
                        "Cookie: delicious-cookie\n" +
                        "Forwarded: forwarded-for\n" +
                        "Host: host\n" +
                        "Pragma: no-cache\n" +
                        "SOAPAction: \"soap-action\"\n" +
                        "User-Agent: User-Agent\n" +
                        "X-CACHE-BALANCE-HEADER: balance-header\n" +
                        "X-Forwarded-For: 12\\.123\\.123\\.12\n" +
                        "X-Forwarded-Host: forwarded-host\n" +
                        "X-Forwarded-Port: 443\n" +
                        "X-Forwarded-Proto: https$";
        assertEquals(expectedHeader, requestPattern.getHeader());
    }
}
