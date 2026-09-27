package br.pucrs.constrsw.oauth.infrastructure.adapter.out.keycloak;

import java.util.Map;

import br.pucrs.constrsw.oauth.domain.exception.UpstreamErrorException;

/**
 * Helpers compartilhados pelos gateways para preservar o erro original do
 * Keycloak como causa das excecoes de dominio (vira o primeiro item do
 * error_stack da resposta).
 */
final class KeycloakErrors {

    static final String SOURCE = "Keycloak";

    private KeycloakErrors() {}

    /** Erro de origem, com o status e a mensagem devolvidos pelo Keycloak. */
    static UpstreamErrorException upstream(int status, Object body) {
        String prefix = "HTTP " + status;
        String detail = message(body);
        // Alguns erros do Keycloak ja trazem o status na mensagem ("HTTP 403 Forbidden")
        String text = detail.isEmpty() ? prefix
                : detail.startsWith(prefix) ? detail
                : prefix + " - " + detail;
        return new UpstreamErrorException(SOURCE, status, text);
    }

    /** Anexa o erro de origem a excecao de dominio que o traduz. */
    static <T extends RuntimeException> T causedBy(T exception, UpstreamErrorException upstream) {
        exception.initCause(upstream);
        return exception;
    }

    /** Extrai a mensagem dos formatos de erro do Keycloak (Admin API e OpenID Connect). */
    static String message(Object body) {
        if (body instanceof Map<?, ?> m) {
            Object msg = m.get("errorMessage");
            if (msg != null) return msg.toString();
            Object err = m.get("error");
            Object desc = m.get("error_description");
            if (err != null && desc != null) return err + ": " + desc;
            if (err != null) return err.toString();
        }
        return "";
    }
}
