package com.seugrupo.oauth.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void rotaInexistenteRetorna404NoContratoDeErro() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNotFound(new NoResourceFoundException(HttpMethod.GET, "nao-existe"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("OA-404");
        assertThat(response.getBody().errorSource()).isEqualTo("OAuthAPI");
        assertThat(response.getBody().errorStack()).hasSize(1);
    }
}
