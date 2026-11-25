package com.joedayz.springai.m06;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Cliente HTTP del servicio externo de transporte. */
@Component
class CarrierClient {

    record Tracking(String status, String eta) { }

    private final RestClient restClient;

    CarrierClient(RestClient.Builder builder, @Value("${carrier.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    Tracking track(String trackingNumber) {
        return restClient.get().uri("/{tracking}", trackingNumber).retrieve().body(Tracking.class);
    }
}
