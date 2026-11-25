package com.joedayz.springai.m04;

import java.util.regex.Pattern;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.core.Ordered;

/**
 * Advisor propio: enmascara correos y teléfonos antes de que el texto llegue a la
 * sesión y al modelo. Debe ejecutarse ANTES que los advisors de sesión (orden menor que
 * SessionTurnAdvisor, HIGHEST_PRECEDENCE + 200), de lo contrario la sesión guardaría el dato original.
 */
public class PiiMaskingAdvisor implements BaseAdvisor {

    static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d\\s-]{7,}\\d");

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        var masked = request.prompt().augmentUserMessage(user ->
                user.mutate().text(mask(user.getText())).build());
        return request.mutate().prompt(masked).build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    static String mask(String text) {
        String result = EMAIL.matcher(text).replaceAll("[EMAIL]");
        return PHONE.matcher(result).replaceAll("[TELEFONO]");
    }
}
