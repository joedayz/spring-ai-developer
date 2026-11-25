package com.joedayz.springai.m03;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
@AutoConfigureMockMvc
class TicketControllerTest {

    static final String VALID = """
            {"category":"BILLING","priority":"HIGH","summary":"Cobro duplicado",
             "suggestedActions":["Reembolsar","Revisar facturación"]}""";

    @Autowired MockMvc mvc;
    @MockitoBean ChatModel chatModel;

    @BeforeEach
    void setUp() {
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
    }

    private static ChatResponse reply(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void classifyMapeaJsonAlRecord() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply(VALID));

        mvc.perform(post("/classify").contentType(MediaType.TEXT_PLAIN).content("Me cobraron dos veces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("BILLING"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.suggestedActions.length()").value(2));
    }

    @Test
    void productsRechazaSalidaQueNoPasaValidacion() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("{\"products\":[{\"product\":\"\",\"sentiment\":\"POSITIVE\"}]}"));

        mvc.perform(post("/products").contentType(MediaType.TEXT_PLAIN).content("Me encanta"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void classifyRobustReintentaCuandoElJsonEsInvalido() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(reply("{\"category\":\"NOPE\"}"))
                .thenReturn(reply(VALID));

        mvc.perform(post("/classify-robust").contentType(MediaType.TEXT_PLAIN).content("Me cobraron dos veces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("BILLING"));

        verify(chatModel, times(2)).call(any(Prompt.class));
    }
}
