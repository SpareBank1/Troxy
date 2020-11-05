package no.sb1.troxy.util;

import no.sb1.troxy.http.common.Request;
import no.sb1.troxy.record.v3.Recording;
import no.sb1.troxy.record.v3.RequestPattern;
import no.sb1.troxy.record.v3.ResponseTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CacheTest {

    private Cache cache;

    @BeforeEach
    void setUp() {
        cache = Cache.createCacheRoot();
    }

    private void putRecordingsInCache(String... paths) {
        Cache.loadRecordings(
                cache,
                new TroxyFileHandler("src/test/java/no/sb1/troxy/util", ""),
                new HashSet<>(Arrays.asList(paths))
        );
    }

    @Test
    void emptyCacheShouldReturnNothing() {
        final List<Cache.Result> results = cache.searchCache(testRequest("GET", "http", "example.com", "80", "/", "", null));
        assertEquals(0, results.size());
    }

    @Test
    void matchWithOneEntry() {
        cache.addRecoding(testRecording("GET", "http", "example.com", "80", "/", "", null));
        final List<Cache.Result> results = cache.searchCache(testRequest("GET", "http", "example.com", "80", "/", "", null));
        assertEquals(1, results.size());
    }

    @Test
    void noMatchWithOneEntry() {
        cache.addRecoding(testRecording("GET", "http", "example.com", "80", "/path", "", null));
        final List<Cache.Result> results = cache.searchCache(testRequest("GET", "http", "example.com", "80", "/", "", null));
        assertEquals(0, results.size());
    }

    @Test
    @Disabled
        // find out about how pattern matching works
    void matchWithSeveralEntries() {
        cache.addRecoding(testRecording("GET", "http", "example.com", "80", "/", "a", null));
        cache.addRecoding(testRecording("GET", "http", "example.com", "80", "/", "b", null));
        cache.addRecoding(testRecording("GET", "https", "example.com", "80", "/login", "", null));
        cache.addRecoding(testRecording("POST", "http", "example.com", "80", "/path", "", null));
        cache.addRecoding(testRecording("PUT", "http", "example.com", "80", "/path", "", null));

        final List<Cache.Result> results = cache.searchCache(testRequest("GET", "http", "example.com", "80", "/", "", null));
        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("searchCache should return 1 result when all headers were present")
    void testDefaultCase() {
        putRecordingsInCache("test-recording.troxy");
        final String requestHeader =
                "Accept: */*\n" +
                "Authorization: Basic authorization-123\n" +
                "Cache-Control: no-cache\n" +
                "Connection: close\n" +
                "Content-Length: 1850\n" +
                "Content-Type: text/xml; charset=UTF-8\n" +
                "Host: www.example.com\n" +
                "Pragma: no-cache\n" +
                "SOAPAction: \"my-soap-action\"\n" +
                "User-Agent: User-Agent iOS";
        final Request request = testRequest("POST", "https", "example.com", "443", "/example", "<>Content</>", requestHeader);

        final List<Cache.Result> results = cache.searchCache(request);

        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("searchCache should return 1 result when several recording were present in cache but only the one with the correct 'SOAPAction' matched")
    void test2() {
        putRecordingsInCache("test-recording.troxy", "test-recording-not-match.troxy");


        final String requestHeader =
                "Accept: */*\n" +
                "Authorization: Basic authorization-123\n" +
                "Cache-Control: no-cache\n" +
                "Connection: close\n" +
                "Content-Length: 1850\n" +
                "Content-Type: text/xml; charset=UTF-8\n" +
                "Host: www.example.com\n" +
                "Pragma: no-cache\n" +
                "SOAPAction: \"my-soap-action\"\n" +
                "User-Agent: User-Agent iOS";

        final Request request = testRequest("POST", "https", "example.com", "443", "/example", "<>Content</>", requestHeader);

        final List<Cache.Result> results = cache.searchCache(request);

        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("searchCache should return 1 result when request matches, but has additional headers")
    void testExtraHeaders() {
        putRecordingsInCache("test-recording.troxy");

        final String requestHeader =
                "Accept: */*\n" +
                "Authorization: Basic authorization-123\n" +
                "Cache-Control: no-cache\n" +
                "Connection: close\n" +
                "Content-Length: 1850\n" +
                "Content-Type: text/xml; charset=UTF-8\n" +
                "Cookie: delicious-cookie\n" +
                "Forwarded: forwarded-for\n" +
                "Host: www.example.com\n" +
                "Pragma: no-cache\n" +
                "SOAPAction: \"my-soap-action\"\n" +
                "User-Agent: User-Agent iOS\n" +
                "X-CACHE-BALANCE-HEADER: cache-balance-header\n" +
                "X-Forwarded-For: forwarded-for\n" +
                "X-Forwarded-Host: forwarded-host\n" +
                "X-Forwarded-Port: 443\n" +
                "X-Forwarded-Proto: https\n";

        final Request request = testRequest("POST", "https", "example.com", "443", "/example", "<>Content</>", requestHeader);

        final List<Cache.Result> results = cache.searchCache(request);

        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("searchCache should return 0 results when mandatory headers 'Dingo' and 'X-XLast-Header' are not present on request")
    void testMissingExtraHeaders() {
        putRecordingsInCache("test-recording-with-extra-headers.troxy");

        final String requestHeader =
                "Accept: */*\n" +
                "Authorization: Basic authorization-123\n" +
                "Cache-Control: no-cache\n" +
                "Connection: close\n" +
                "Content-Length: 1850\n" +
                "Content-Type: text/xml; charset=UTF-8\n" +
                "Cookie: delicious-cookie\n" +
                "Forwarded: forwarded-for\n" +
                "Host: www.example.com\n" +
                "Pragma: no-cache\n" +
                "SOAPAction: \"my-soap-action\"\n" +
                "User-Agent: User-Agent iOS\n" +
                "X-CACHE-BALANCE-HEADER: cache-balance-header\n" +
                "X-Forwarded-For: forwarded-for\n" +
                "X-Forwarded-Host: forwarded-host\n" +
                "X-Forwarded-Port: 443\n" +
                "X-Forwarded-Proto: https\n";

        final Request request = testRequest("POST", "https", "example.com", "443", "/example", "<>Content</>", requestHeader);

        final List<Cache.Result> results = cache.searchCache(request);

        assertEquals(0, results.size());
    }

    @Test
    @DisplayName("searchCache should return 1 result when mandatory headers 'Dingo' and 'X-XLast-Header' are present on request")
    void testExtraHeadersPresent() {
        putRecordingsInCache("test-recording-with-extra-headers.troxy");

        final String requestHeader =
                "Accept: */*\n" +
                "Authorization: Basic authorization-123\n" +
                "Cache-Control: no-cache\n" +
                "Connection: close\n" +
                "Content-Length: 1850\n" +
                "Content-Type: text/xml; charset=UTF-8\n" +
                "Cookie: delicious-cookie\n" +
                "Dingo: \"straya mate\"\n" +
                "Forwarded: forwarded-for\n" +
                "Host: www.example.com\n" +
                "Pragma: no-cache\n" +
                "SOAPAction: \"my-soap-action\"\n" +
                "User-Agent: User-Agent iOS\n" +
                "X-CACHE-BALANCE-HEADER: cache-balance-header\n" +
                "X-Forwarded-For: forwarded-for\n" +
                "X-Forwarded-Host: forwarded-host\n" +
                "X-Forwarded-Port: 443\n" +
                "X-Forwarded-Proto: https\n" +
                "X-XLast-Header: true\n";

        final Request request = testRequest("POST", "https", "example.com", "443", "/example", "<>Content</>", requestHeader);

        final List<Cache.Result> results = cache.searchCache(request);

        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("searchCache should return 0 results when mandatory headers 'Dingo' and 'X-XLast-Header' are present, but had the wrong value")
    void testExtraHeadersPresentButWrongValue() {
        putRecordingsInCache("test-recording-with-extra-headers.troxy");

        final String requestHeader =
                "Accept: */*\n" +
                "Authorization: Basic authorization-123\n" +
                "Cache-Control: no-cache\n" +
                "Connection: close\n" +
                "Content-Length: 1850\n" +
                "Content-Type: text/xml; charset=UTF-8\n" +
                "Cookie: delicious-cookie\n" +
                "Dingo: \"kiwi mate\"\n" +
                "Forwarded: forwarded-for\n" +
                "Host: www.example.com\n" +
                "Pragma: no-cache\n" +
                "SOAPAction: \"my-soap-action\"\n" +
                "User-Agent: User-Agent iOS\n" +
                "X-CACHE-BALANCE-HEADER: cache-balance-header\n" +
                "X-Forwarded-For: forwarded-for\n" +
                "X-Forwarded-Host: forwarded-host\n" +
                "X-Forwarded-Port: 443\n" +
                "X-Forwarded-Proto: https\n" +
                "X-XLast-Header: false\n";

        final Request request = testRequest("POST", "https", "example.com", "443", "/example", "<>Content</>", requestHeader);

        final List<Cache.Result> results = cache.searchCache(request);

        assertEquals(0, results.size());
    }

    private Recording testRecording(final String method, final String protocol, final String host, final String port, final String path, final String content, final String header) {
        final Recording recording = new Recording();
        recording.setResponseStrategy(Recording.ResponseStrategy.SEQUENTIAL);
        final RequestPattern requestPattern = new RequestPattern();
        requestPattern.setMethod(method);
        requestPattern.setProtocol(protocol);
        requestPattern.setHost(host);
        requestPattern.setPort(port);
        requestPattern.setPath(path);
        requestPattern.setHeader(header);
        requestPattern.setContent(content);
        recording.setRequestPattern(requestPattern);
        recording.setResponseTemplates(Collections.singletonList(new ResponseTemplate()));
        return recording;
    }

    private Request testRequest(final String method, final String protocol, final String host, final String port, final String path, final String content, final String header) {
        final Request request = new Request();
        request.setMethod(method);
        request.setProtocol(protocol);
        request.setHost(host);
        request.setPort(port);
        request.setPath(path);
        request.setHeader(header);
        request.setContent(content);

        return request;
    }
}