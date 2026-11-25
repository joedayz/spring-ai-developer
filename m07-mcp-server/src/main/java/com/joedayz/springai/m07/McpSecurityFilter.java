package com.joedayz.springai.m07;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * El transporte MCP no autentica nada: sin este filtro, cualquiera que alcance /mcp puede listar e invocar
 * todas las tools. Este filtro aplica dos controles mínimos:
 *  1. Token Bearer compartido (comparación en tiempo constante).
 *  2. Validación del header Origin contra DNS rebinding: una página web abierta en el navegador
 *     podría llamar a un servidor en localhost.
 * En producción usa Spring Security con OAuth2 (o el proyecto MCP Security).
 */
@Component
class McpSecurityFilter extends OncePerRequestFilter {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    private final byte[] expectedToken;

    McpSecurityFilter(@Value("${acme.mcp.token}") String token) {
        if (token == null || token.length() < 16) {
            throw new IllegalStateException("acme.mcp.token debe tener al menos 16 caracteres");
        }
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/mcp");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !isLocalOrigin(origin)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Origin no permitido");
            return;
        }
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")
                || !MessageDigest.isEqual(expectedToken, auth.substring(7).getBytes(StandardCharsets.UTF_8))) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isLocalOrigin(String origin) {
        try {
            return LOCAL_HOSTS.contains(URI.create(origin).getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
