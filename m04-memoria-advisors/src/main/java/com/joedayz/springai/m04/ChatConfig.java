package com.joedayz.springai.m04;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.ai.session.compaction.ChatModelSummarizer;
import org.springframework.ai.session.compaction.TurnCountTrigger;
import org.springframework.ai.session.compaction.WindowCompactionStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ChatConfig {

    private static final String SYSTEM = "Eres un asistente de soporte de Acme. Responde en español y de forma breve.";

    /**
     * Básico: sesión en memoria del proceso, se pierde al reiniciar.
     * No se declara como bean para no reemplazar al SessionService JDBC autoconfigurado.
     * Al pasar de 10 turnos se conservan solo los últimos 5 (ventana por turnos).
     */
    @Bean
    ChatClient inMemoryClient(ChatClient.Builder builder) {
        SessionService inMemory = DefaultSessionService.builder()
                .sessionRepository(InMemorySessionRepository.builder().build())
                .build();
        return builder.clone()
                .defaultSystem(SYSTEM)
                .defaultAdvisors(with(SessionAdvisors.builder(inMemory)
                        .compaction(new TurnCountTrigger(10), WindowCompactionStrategy.builder().lastTurns(5).build())
                        .build(), new SimpleLoggerAdvisor()))
                .build();
    }

    /** Intermedio: sesión persistente en base de datos. El SessionService JDBC lo crea el starter. */
    @Bean
    ChatClient persistentClient(ChatClient.Builder builder, SessionService sessionService) {
        return builder.clone()
                .defaultSystem(SYSTEM)
                .defaultAdvisors(SessionAdvisors.builder(sessionService)
                        .compaction(new TurnCountTrigger(20), WindowCompactionStrategy.builder().lastTurns(10).build())
                        .build())
                .build();
    }

    /**
     * Avanzado: sesión persistente + advisor propio de PII + compactación con resumen.
     * Al superar el umbral de turnos, el modelo resume los turnos antiguos y la vista conserva el resumen
     * más los últimos turnos. El log original no se modifica: la compactación solo agrega un límite.
     */
    @Bean
    ChatClient safeClient(ChatClient.Builder builder, SessionService sessionService, ChatModel chatModel,
                          @Value("${course.summary.max-turns:6}") int maxTurns,
                          @Value("${course.summary.keep-turns:2}") int keepTurns) {
        var summarizing = WindowCompactionStrategy.builder()
                .lastTurns(keepTurns)
                .summarizer(ChatModelSummarizer.builder(chatModel).build())
                .build();
        return builder.clone()
                .defaultSystem(SYSTEM)
                .defaultAdvisors(with(SessionAdvisors.builder(sessionService)
                        .compaction(new TurnCountTrigger(maxTurns), summarizing)
                        .build(), new PiiMaskingAdvisor()))
                .build();
    }

    private static List<Advisor> with(List<Advisor> sessionAdvisors, Advisor extra) {
        var all = new ArrayList<>(sessionAdvisors);
        all.add(extra);
        return all;
    }
}
