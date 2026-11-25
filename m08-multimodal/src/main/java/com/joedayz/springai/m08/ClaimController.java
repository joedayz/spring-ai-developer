package com.joedayz.springai.m08;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
class ClaimController {

    private final ClaimService claims;
    private final Clock clock;

    ClaimController(ClaimService claims, java.util.Optional<Clock> clock) {
        this.claims = claims;
        this.clock = clock.orElse(Clock.systemDefaultZone());
    }

    @PostMapping(path = "/claims", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ClaimService.ClaimDecision claim(@RequestParam MultipartFile photo,
                                     @RequestParam(required = false) MultipartFile voiceNote,
                                     @RequestParam LocalDate purchaseDate) throws IOException {
        var note = voiceNote == null || voiceNote.isEmpty() ? null : Uploads.audio(voiceNote);
        return claims.evaluate(Uploads.image(photo), note, purchaseDate, LocalDate.now(clock));
    }
}
