package br.pucrs.constrsw.oauth.infrastructure.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.pucrs.constrsw.oauth.application.port.in.LoginUseCase;
import br.pucrs.constrsw.oauth.domain.model.AuthTokens;
import br.pucrs.constrsw.oauth.domain.model.Credentials;
import br.pucrs.constrsw.oauth.infrastructure.adapter.in.rest.dto.ErrorResponseDto;
import br.pucrs.constrsw.oauth.infrastructure.adapter.in.rest.dto.LoginResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Adapter de entrada REST para o caso de uso de login. Traduz a requisicao
 * multipart/form-data em {@link Credentials}, delega ao caso de uso e serializa
 * a resposta.
 */
@RestController
@Tag(name = "Auth", description = "Autenticacao de usuarios via Keycloak")
@SecurityRequirements
public class AuthRestController {

    private final LoginUseCase loginUseCase;

    public AuthRestController(LoginUseCase loginUseCase) {
        this.loginUseCase = loginUseCase;
    }

    @Operation(summary = "Autentica um usuario e retorna o access token emitido pelo Keycloak.")
    @ApiResponse(responseCode = "201", description = "Created",
            content = @Content(schema = @Schema(implementation = LoginResponseDto.class)))
    @ApiResponse(responseCode = "400", description = "Bad Request",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    // form-data (multipart) e o formato do enunciado; x-www-form-urlencoded tambem e
    // aceito porque e a outra opcao de formulario do Postman.
    @PostMapping(path = "/login",
            consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ResponseEntity<LoginResponseDto> login(@RequestParam("username") String username,
                                                   @RequestParam("password") String password) {
        AuthTokens tokens = loginUseCase.execute(new Credentials(username, password));
        return ResponseEntity.status(HttpStatus.CREATED).body(LoginResponseDto.fromDomain(tokens));
    }
}
