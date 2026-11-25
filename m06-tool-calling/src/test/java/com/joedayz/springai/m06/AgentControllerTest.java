package com.joedayz.springai.m06;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
@AutoConfigureMockMvc
class AgentControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @MockitoBean ChatModel chatModel;

    @BeforeEach
    void setUp() {
        reset(chatModel);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        jdbc.sql("update orders set status = 'PROCESSING' where id in (1, 3)").update();
        jdbc.sql("update orders set status = 'SHIPPED' where id = 2").update();
    }

    private static ChatResponse toolCall(String name, String args) {
        var message = AssistantMessage.builder().content("")
                .toolCalls(List.of(new ToolCall("call_1", "function", name, args))).build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    private static ChatResponse text(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private String statusOf(long id) {
        return jdbc.sql("select status from orders where id = :id").param("id", id).query(String.class).single();
    }

    private String promptSentToModel(int callNumber) {
        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, org.mockito.Mockito.atLeast(callNumber)).call(captor.capture());
        return captor.getAllValues().get(callNumber - 1).getInstructions().toString();
    }

    private MvcResult chat(String customer, String message) throws Exception {
        return mvc.perform(post("/agent/chat").header("X-Customer", customer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"" + message + "\"}")).andReturn();
    }

    private String pendingIdOf(MvcResult result) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.pendingId");
    }

    private org.springframework.test.web.servlet.ResultActions decide(String customer, String id, boolean approve)
            throws Exception {
        return mvc.perform(post("/agent/confirm/" + id).header("X-Customer", customer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"approve\":" + approve + "}"));
    }

    @Test
    void toolDeSoloLecturaSeEjecutaSinConfirmacionYSoloVeLosPedidosDelCliente() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCall("listMyOrders", "{}"), text("Tienes 2 pedidos"));

        var result = chat("ana", "¿Qué pedidos tengo?");

        assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"DONE\"", "Tienes 2 pedidos");
        String secondPrompt = promptSentToModel(2);
        assertThat(secondPrompt).contains("Audífonos", "Teclado").doesNotContain("Monitor");
    }

    @Test
    void cancelarPausaElBucleYNoEjecutaNadaHastaAprobar() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCall("cancelOrder", "{\"orderId\":1}"));

        var result = chat("ana", "Cancela mi pedido 1");

        assertThat(result.getResponse().getContentAsString()).contains("AWAITING_CONFIRMATION", "cancelOrder");
        assertThat(statusOf(1)).isEqualTo("PROCESSING");
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void alAprobarSeEjecutaLaToolYSeContinuaLaConversacion() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCall("cancelOrder", "{\"orderId\":1}"), text("Pedido 1 cancelado"));
        String id = pendingIdOf(chat("ana", "Cancela mi pedido 1"));

        decide("ana", id, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.answer").value("Pedido 1 cancelado"));

        assertThat(statusOf(1)).isEqualTo("CANCELLED");
    }

    @Test
    void alRechazarNoSeEjecutaYElModeloRecibeElMotivo() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCall("cancelOrder", "{\"orderId\":1}"), text("Entendido, no lo cancelo"));
        String id = pendingIdOf(chat("ana", "Cancela mi pedido 1"));

        decide("ana", id, false).andExpect(status().isOk());

        assertThat(statusOf(1)).isEqualTo("PROCESSING");
        assertThat(promptSentToModel(2)).contains("rechazó la acción");
    }

    @Test
    void otroClienteNoPuedeDecidirNiConsumirLaPausaAjena() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCall("cancelOrder", "{\"orderId\":1}"), text("Pedido 1 cancelado"));
        String id = pendingIdOf(chat("ana", "Cancela mi pedido 1"));

        decide("luis", id, true).andExpect(status().isNotFound());
        assertThat(statusOf(1)).isEqualTo("PROCESSING");

        decide("ana", id, true).andExpect(status().isOk()); // la pausa de ana sigue vigente
        assertThat(statusOf(1)).isEqualTo("CANCELLED");
    }

    @Test
    void unaDecisionNoSePuedeRepetir() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCall("cancelOrder", "{\"orderId\":1}"), text("ok"));
        String id = pendingIdOf(chat("ana", "Cancela mi pedido 1"));

        decide("ana", id, true).andExpect(status().isOk());
        decide("ana", id, true).andExpect(status().isNotFound());
    }

    @Test
    void noSePuedeCancelarUnPedidoDeOtroCliente() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCall("cancelOrder", "{\"orderId\":3}"), text("No encontré ese pedido"));
        String id = pendingIdOf(chat("ana", "Cancela el pedido 3"));

        decide("ana", id, true).andExpect(status().isOk());

        assertThat(statusOf(3)).isEqualTo("PROCESSING");
        assertThat(promptSentToModel(2)).contains("No se encontró el pedido 3");
    }

    @Test
    void siLaToolFallaElModeloRecibeElErrorYLaConversacionContinua() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCall("cancelOrder", "{\"orderId\":2}"), text("Ese pedido ya fue enviado"));
        String id = pendingIdOf(chat("ana", "Cancela el pedido 2"));

        decide("ana", id, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Ese pedido ya fue enviado"));

        assertThat(statusOf(2)).isEqualTo("SHIPPED");
        assertThat(promptSentToModel(2)).contains("No se puede cancelar");
    }
}
