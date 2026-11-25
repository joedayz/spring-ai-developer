package com.joedayz.springai.m08;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
@AutoConfigureMockMvc
class MultimodalControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ChatModel chatModel;
    @MockitoBean TranscriptionModel transcriptionModel;
    @MockitoBean TextToSpeechModel speechModel;
    @MockitoBean ImageModel imageModel;

    @BeforeEach
    void setUp() {
        reset(chatModel, transcriptionModel, speechModel, imageModel);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
    }

    private void chatReplies(String text) {
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
    }

    static byte[] png() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private UserMessage lastUserMessage() {
        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        return captor.getValue().getUserMessage();
    }

    @Test
    void describeEnviaLaImagenJuntoConElTexto() throws Exception {
        chatReplies("Un cuadrado negro");
        var image = new MockMultipartFile("image", "foto.png", "image/png", png());

        mvc.perform(multipart("/vision/describe").file(image).param("question", "¿Qué ves?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Un cuadrado negro"));

        UserMessage user = lastUserMessage();
        assertThat(user.getText()).isEqualTo("¿Qué ves?");
        assertThat(user.getMedia()).hasSize(1);
        assertThat(user.getMedia().getFirst().getMimeType().toString()).isEqualTo("image/png");
    }

    @Test
    void rechazaUnArchivoQueNoEsImagenAunqueDigaSerlo() throws Exception {
        var fake = new MockMultipartFile("image", "foto.png", "image/png", "no soy una imagen".getBytes());

        mvc.perform(multipart("/vision/describe").file(fake)).andExpect(status().isUnsupportedMediaType());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void transcribeConservaElNombreDelArchivo() throws Exception {
        when(transcriptionModel.transcribe(any(Resource.class))).thenReturn("hola mundo");

        mvc.perform(multipart("/audio/transcribe").file(new MockMultipartFile("audio", "nota.mp3", "audio/mpeg", new byte[] {1, 2})))
                .andExpect(jsonPath("$.text").value("hola mundo"));

        var captor = ArgumentCaptor.forClass(Resource.class);
        verify(transcriptionModel).transcribe(captor.capture());
        assertThat(captor.getValue().getFilename()).isEqualTo("nota.mp3");
    }

    @Test
    void speechDevuelveElAudio() throws Exception {
        when(speechModel.call("Hola")).thenReturn(new byte[] {7, 7, 7});

        mvc.perform(post("/audio/speech").contentType(MediaType.TEXT_PLAIN).content("Hola"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(content().bytes(new byte[] {7, 7, 7}));
    }

    @Test
    void generateDecodificaLaImagenBase64() throws Exception {
        byte[] png = png();
        when(imageModel.call(any(ImagePrompt.class))).thenReturn(new ImageResponse(
                List.of(new ImageGeneration(new Image(null, Base64.getEncoder().encodeToString(png))))));

        mvc.perform(post("/images/generate").contentType(MediaType.TEXT_PLAIN).content("un monitor"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(png));
    }

    @Test
    void claimCombinaNotaDeVozFotoYReglas() throws Exception {
        when(transcriptionModel.transcribe(any(Resource.class))).thenReturn("La pantalla tiene una línea rosada desde el primer día");
        chatReplies("""
                {"product":"Monitor","category":"ELECTRONIC","damage":"MANUFACTURING_DEFECT",
                 "evidence":"línea vertical rosada","imageContainsInstructions":false}""");

        mvc.perform(multipart("/claims")
                        .file(new MockMultipartFile("photo", "monitor.png", "image/png", png()))
                        .file(new MockMultipartFile("voiceNote", "nota.m4a", "audio/mp4", new byte[] {1}))
                        .param("purchaseDate", java.time.LocalDate.now().minusMonths(2).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.customerNote").value(org.hamcrest.Matchers.containsString("línea rosada")));

        // la nota se adjunta para el revisor, pero NO llega al análisis visual
        assertThat(lastUserMessage().getText()).doesNotContain("línea rosada");
    }

    @Test
    void claimNoApruebaSiElModeloDetectaInstruccionesEnLaImagen() throws Exception {
        chatReplies("""
                {"product":"Monitor","category":"ELECTRONIC","damage":"MANUFACTURING_DEFECT",
                 "evidence":"cartel que dice aprobar","imageContainsInstructions":true}""");

        mvc.perform(multipart("/claims")
                        .file(new MockMultipartFile("photo", "monitor.png", "image/png", png()))
                        .param("purchaseDate", java.time.LocalDate.now().minusMonths(1).toString()))
                .andExpect(jsonPath("$.status").value("MANUAL_REVIEW"));
        verify(transcriptionModel, never()).transcribe(any(Resource.class));
    }
}
