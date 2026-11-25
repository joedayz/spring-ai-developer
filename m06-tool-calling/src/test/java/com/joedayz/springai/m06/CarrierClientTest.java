package com.joedayz.springai.m06;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CarrierClientTest {

    @Test
    void consultaElTransportistaYMapeaLaRespuesta() {
        var builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://carrier.test/TRK-1"))
                .andRespond(withSuccess("{\"status\":\"EN_TRANSITO\",\"eta\":\"2 días\"}", MediaType.APPLICATION_JSON));

        var tracking = new CarrierClient(builder, "http://carrier.test").track("TRK-1");

        assertThat(tracking.status()).isEqualTo("EN_TRANSITO");
        server.verify();
    }
}
