package com.joedayz.springai.m06;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Simula el servicio del transportista para poder ejecutar el curso sin dependencias externas. */
@RestController
class FakeCarrierController {

    @GetMapping("/fake-carrier/{tracking}")
    CarrierClient.Tracking track(@PathVariable String tracking) {
        return new CarrierClient.Tracking("EN_TRANSITO", "2 días hábiles");
    }
}
