package no.sb1.troxy.embedded;

import no.sb1.troxy.jetty.TroxyJettyServer;
import no.sb1.troxy.util.Cache;
import no.sb1.troxy.util.CacheUtil;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Map;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class StatisticsTest {
    private static TroxyJettyServer troxyServer;
    private static Cache cache;


    @Test
    public void statistics_per_path_should_be_increased_by_one_after_request() throws IOException {

        runRequests();

        Map<String, Integer> statisticsPerPath = CacheUtil.getRequestCounterPerPath(cache);
        assertThat(statisticsPerPath.get("/samepath"), equalTo(3));
        assertThat(statisticsPerPath.get("/differentpath"), equalTo(1));
    }

    @Test
    public void statistics_per_mock_file_should_be_increased_by_one_after_request() throws IOException {

        runRequests();

        Map<String, Integer> statisticsPerFile = CacheUtil.getRequestCounterPerRecording(cache);
        assertThat(statisticsPerFile.get("request_same_path1.troxy"), equalTo(2));
        assertThat(statisticsPerFile.get("request_same_path2.troxy"), equalTo(1));
        assertThat(statisticsPerFile.get("request_different_path.troxy"), equalTo(1));
    }

    @Test
    public void reset_statistics_should_set_statistics_to_zero() throws IOException {

        runRequests();

        CacheUtil.resetTotalStatisticCounter(cache);

        Map<String, Integer> statisticsPerFile = CacheUtil.getRequestCounterPerRecording(cache);
        assertThat(statisticsPerFile.get("request_same_path1.troxy"), equalTo(0));
        assertThat(statisticsPerFile.get("request_same_path2.troxy"), equalTo(0));
        assertThat(statisticsPerFile.get("request_different_path.troxy"), equalTo(0));
        Map<String, Integer> statisticsPerPath = CacheUtil.getRequestCounterPerPath(cache);
        assertThat(statisticsPerPath.get("/samepath"), equalTo(0));
        assertThat(statisticsPerPath.get("/differentpath"), equalTo(0));
    }

    private void runRequests() throws IOException {
        responseBodyForRequestTo("/samepath", "recording1");
        responseBodyForRequestTo("/samepath", "recording1");
        responseBodyForRequestTo("/samepath", "recording2");
        responseBodyForRequestTo("/differentpath", "");
    }


    private static void responseBodyForRequestTo(String path, String headerValue) throws IOException {
        HttpUriRequest request = new HttpGet(format("http://localhost:9999%s", path));
        request.addHeader("SomeHeader", headerValue);
        HttpClientBuilder.create().build().execute(request);
    }

    @BeforeAll
    public static void setup() {
        troxyServer = TroxyEmbedded.runTroxyEmbedded(
                asList("src/test/resources/statistics/request_same_path1.troxy",
                "src/test/resources/statistics/request_same_path2.troxy",
                "src/test/resources/statistics/request_different_path.troxy"
                ), 9999);

        cache = TroxyEmbedded.getCache();
    }

    @AfterAll
    public static void teardown() {
        troxyServer.stop();
    }

    @AfterEach
    public void afterEach() {
        CacheUtil.resetTotalStatisticCounter(cache);
    }
}
