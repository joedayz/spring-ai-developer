package com.joedayz.springai.m02;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
@AutoConfigureMockMvc
class SupportControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ChatModel chatModel;

    @Test
    void askDevuelveLaRespuestaDelModelo() throws Exception {
        when(chatModel.getOptions()).thenReturn(org.springframework.ai.openai.OpenAiChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(java.util.List.of(new Generation(new AssistantMessage("hola")))));

        mvc.perform(get("/ask").param("question", "hi"))
                .andExpect(status().isOk())
                .andExpect(content().string("hola"));
    }
}
