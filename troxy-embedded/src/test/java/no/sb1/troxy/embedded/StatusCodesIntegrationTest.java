package no.sb1.troxy.embedded;

import no.sb1.troxy.jetty.TroxyJettyServer;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static java.lang.String.*;
import static java.util.Arrays.asList;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class StatusCodesIntegrationTest {

    @Test
    public void troxy_should_return_200_status_code_when_specified_in_troxy_file() throws IOException {
        assertThat(statusCodeForRequestTo("/200"), equalTo(SC_OK));
    }

    @Test
    public void troxy_should_return_500_status_code_when_specified_in_troxy_file() throws IOException {
        assertThat(statusCodeForRequestTo("/500"), equalTo(SC_INTERNAL_SERVER_ERROR));
    }

    @Test
    public void troxy_should_return_418_status_code_when_request_doesnt_match_troxy_mocks() throws IOException {
        assertThat(statusCodeForRequestTo("/snafu"), equalTo(418));
    }



    private static int  statusCodeForRequestTo(String path) throws IOException {
        HttpUriRequest request = new HttpGet(format("http://localhost:9999%s", path));
        request.addHeader("Host", "testhost:8000");

        HttpResponse httpResponse = HttpClientBuilder.create().build().execute(request);

        return httpResponse.getStatusLine().getStatusCode();
    }

    private static TroxyJettyServer troxyJettyServer;

    @BeforeAll
    public static void setup() {
        troxyJettyServer = TroxyEmbedded.runTroxyEmbedded(
                asList("src/test/resources/statuscodes/200.troxy",
                       "src/test/resources/statuscodes/500.troxy"
                ), 9999);
    }

    @AfterAll
    public static void teardown() {
        troxyJettyServer.stop();
    }
}
