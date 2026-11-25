package com.joedayz.springai.m09;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Avanzado: presupuesto diario de tokens por usuario. Sin límite, un solo usuario (o un bot) puede
 * consumir todo tu presupuesto de OpenAI. En la demo vive en memoria; en producción usa Redis o la base de datos,
 * porque con varias instancias cada una tendría su propio contador.
 */
class TokenBudgetAdvisor implements CallAdvisor {

    static final String USER = "acme.user";

    private final long dailyTokens;
    private final Clock clock;
    private final MeterRegistry meters;
    private final Map<String, AtomicLong> used = new ConcurrentHashMap<>();

    TokenBudgetAdvisor(long dailyTokens, Clock clock, MeterRegistry meters) {
        this.dailyTokens = dailyTokens;
        this.clock = clock;
        this.meters = meters;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String user = Replies.param(request, USER);
        if (user == null) {
            throw new IllegalStateException("Falta el usuario: pásalo con .advisors(a -> a.param(TokenBudgetAdvisor.USER, ...))");
        }
        AtomicLong counter = used.computeIfAbsent(user + "|" + LocalDate.now(clock), k -> new AtomicLong());
        if (counter.get() >= dailyTokens) {
            meters.counter("acme.ai.budget.rejected").increment();
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Alcanzaste tu límite diario de consultas");
        }
        ChatClientResponse response = chain.nextCall(request);
        var usage = response.chatResponse() == null ? null : response.chatResponse().getMetadata().getUsage();
        if (usage != null && usage.getTotalTokens() != null) {
            counter.addAndGet(usage.getTotalTokens());
            meters.counter("acme.ai.tokens", "user", "all").increment(usage.getTotalTokens());
        }
        return response;
    }

    long usedToday(String user) {
        AtomicLong counter = used.get(user + "|" + LocalDate.now(clock));
        return counter == null ? 0 : counter.get();
    }

    @Override
    public String getName() {
        return "token-budget";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 70;
    }
}
