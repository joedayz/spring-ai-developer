package com.joedayz.springai.m08;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
class MultimodalController {

    private final ChatClient chatClient;
    private final TranscriptionModel transcriptionModel;
    private final TextToSpeechModel speechModel;
    private final ImageModel imageModel;

    MultimodalController(ChatClient.Builder builder, TranscriptionModel transcriptionModel,
                         TextToSpeechModel speechModel, ImageModel imageModel) {
        this.chatClient = builder.build();
        this.transcriptionModel = transcriptionModel;
        this.speechModel = speechModel;
        this.imageModel = imageModel;
    }

    /** Básico: imagen como entrada. El texto y la imagen viajan en el mismo mensaje de usuario. */
    @PostMapping(path = "/vision/describe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Map<String, String> describe(@RequestParam MultipartFile image,
                                 @RequestParam(defaultValue = "Describe esta imagen en 2 frases.") String question)
            throws IOException {
        var img = Uploads.image(image);
        String answer = chatClient.prompt()
                .user(u -> u.text(question).media(img.mimeType(), img.resource()))
                .call()
                .content();
        return Map.of("answer", answer);
    }

    /** Intermedio: voz a texto. */
    @PostMapping(path = "/audio/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Map<String, String> transcribe(@RequestParam MultipartFile audio) throws IOException {
        return Map.of("text", transcriptionModel.transcribe(Uploads.audio(audio)));
    }

    /** Intermedio: texto a voz. Devuelve un MP3. */
    @PostMapping(path = "/audio/speech", consumes = MediaType.TEXT_PLAIN_VALUE, produces = "audio/mpeg")
    byte[] speech(@RequestBody String text) {
        if (text.isBlank() || text.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Entre 1 y 1000 caracteres");
        }
        return speechModel.call(text);
    }

    /** Intermedio: texto a imagen. Cuidado con el costo: es la llamada más cara del módulo. */
    @PostMapping(path = "/images/generate", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.IMAGE_PNG_VALUE)
    ResponseEntity<byte[]> generate(@RequestBody String prompt) {
        if (prompt.isBlank() || prompt.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Entre 1 y 500 caracteres");
        }
        var image = imageModel.call(new ImagePrompt(prompt)).getResult().getOutput();
        if (image.getB64Json() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "El modelo no devolvió la imagen en base64");
        }
        return ResponseEntity.ok(Base64.getDecoder().decode(image.getB64Json()));
    }
}
