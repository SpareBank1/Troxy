package no.sb1.troxy.util;

import no.sb1.troxy.http.common.Request;
import no.sb1.troxy.record.v3.Recording;
import no.sb1.troxy.record.v3.RequestPattern;
import no.sb1.troxy.record.v3.ResponseTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CacheTest {

    private Cache cache;

    @BeforeEach
    void setUp() {
        cache = Cache.createCacheRoot();
    }

    @Test
    void emptyCacheShouldReturnNothing() {
        final List<Cache.Result> results = cache.searchCache(testRequest("GET", "http", "example.com", "80", "/", ""));
        assertEquals(0, results.size());
    }

    @Test
    void matchWithOneEntry() {
        cache.addRecoding(testRecording("GET", "http", "example.com", "80", "/", ""));
        final List<Cache.Result> results = cache.searchCache(testRequest("GET", "http", "example.com", "80", "/", ""));
        assertEquals(1, results.size());
    }

    @Test
    void noMatchWithOneEntry() {
        cache.addRecoding(testRecording("GET", "http", "example.com", "80", "/path", ""));
        final List<Cache.Result> results = cache.searchCache(testRequest("GET", "http", "example.com", "80", "/", ""));
        assertEquals(0, results.size());
    }

    @Test
    @Disabled // find out about how pattern matching works
    void matchWithSeveralEntries() {
        cache.addRecoding(testRecording("GET", "http", "example.com", "80", "/", "a"));
        cache.addRecoding(testRecording("GET", "http", "example.com", "80", "/", "b"));
        cache.addRecoding(testRecording("GET", "https", "example.com", "80", "/login", ""));
        cache.addRecoding(testRecording("POST", "http", "example.com", "80", "/path", ""));
        cache.addRecoding(testRecording("PUT", "http", "example.com", "80", "/path", ""));

        final List<Cache.Result> results = cache.searchCache(testRequest("GET", "http", "example.com", "80", "/", ""));
        assertEquals(2, results.size());
    }

    private Recording testRecording(final String method, final String protocol, final String host, final String port, final String path, final String content) {
        final Recording recording = new Recording();
        recording.setResponseStrategy(Recording.ResponseStrategy.SEQUENTIAL);
        final RequestPattern requestPattern = new RequestPattern();
        requestPattern.setMethod(method);
        requestPattern.setProtocol(protocol);
        requestPattern.setHost(host);
        requestPattern.setPort(port);
        requestPattern.setPath(path);
        requestPattern.setContent(content);
        recording.setRequestPattern(requestPattern);
        recording.setResponseTemplates(Collections.singletonList(new ResponseTemplate()));
        return recording;
    }

    private Request testRequest(final String method, final String protocol, final String host, final String port, final String path, final String content) {
        final Request request = new Request();
        request.setMethod(method);
        request.setProtocol(protocol);
        request.setHost(host);
        request.setPort(port);
        request.setPath(path);
        request.setContent(content);

        return request;
    }
}