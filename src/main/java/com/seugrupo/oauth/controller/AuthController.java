package com.seugrupo.oauth.controller;

import com.seugrupo.oauth.dto.LoginRequest;
import com.seugrupo.oauth.dto.RefreshTokenRequest;
import com.seugrupo.oauth.dto.TokenResponse;
import com.seugrupo.oauth.metrics.BusinessMetrics;
import com.seugrupo.oauth.service.KeycloakAuthService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

    private final KeycloakAuthService authService;
    private final BusinessMetrics metrics;

    public AuthController(KeycloakAuthService authService, BusinessMetrics metrics) {
        this.authService = authService;
        this.metrics = metrics;
    }

    @PostMapping(
            path = "/login",
            consumes = {
                    MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                    MediaType.MULTIPART_FORM_DATA_VALUE
            }
    )
    public ResponseEntity<TokenResponse> login(@Valid @ModelAttribute LoginRequest request) {
        try {
            TokenResponse tokens = authService.login(request);
            metrics.recordLoginSuccess();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .cacheControl(CacheControl.noStore())
                    .body(tokens);
        } catch (RuntimeException ex) {
            metrics.recordLoginFailure();
            throw ex;
        }
    }

    @PostMapping(path = "/refresh-token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<TokenResponse> refresh(@Valid @ModelAttribute RefreshTokenRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authService.refresh(request));
    }
}
