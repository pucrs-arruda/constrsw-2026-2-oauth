package com.seugrupo.oauth.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Metricas de negocio (nao-tecnicas) exportadas junto com as metricas
 * padrao do Actuator em /actuator/prometheus, para permitir observabilidade
 * sobre o dominio da aplicacao (login, gestao de usuarios/roles) e nao
 * apenas sobre a infraestrutura HTTP/JVM.
 */
@Component
public class BusinessMetrics {

    private static final String LOGIN_ATTEMPTS = "oauth_login_attempts_total";
    private static final String MANAGEMENT_OPERATIONS = "oauth_management_operations_total";

    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordLoginSuccess() {
        registry.counter(LOGIN_ATTEMPTS, "result", "success").increment();
    }

    public void recordLoginFailure() {
        registry.counter(LOGIN_ATTEMPTS, "result", "failure").increment();
    }

    public void recordManagementOperation(String operation) {
        registry.counter(MANAGEMENT_OPERATIONS, "operation", operation).increment();
    }
}
