package br.pucrs.constrsw.oauth.infrastructure.adapter.in.rest;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.pucrs.constrsw.oauth.application.port.in.CreateUserUseCase;
import br.pucrs.constrsw.oauth.application.port.in.DisableUserUseCase;
import br.pucrs.constrsw.oauth.application.port.in.GetUserUseCase;
import br.pucrs.constrsw.oauth.application.port.in.ListUsersUseCase;
import br.pucrs.constrsw.oauth.application.port.in.UpdatePasswordUseCase;
import br.pucrs.constrsw.oauth.application.port.in.UpdateUserUseCase;
import br.pucrs.constrsw.oauth.domain.model.User;
import br.pucrs.constrsw.oauth.infrastructure.adapter.in.rest.dto.ErrorResponseDto;
import br.pucrs.constrsw.oauth.infrastructure.adapter.in.rest.dto.PasswordUpdateRequestDto;
import br.pucrs.constrsw.oauth.infrastructure.adapter.in.rest.dto.UserCreateRequestDto;
import br.pucrs.constrsw.oauth.infrastructure.adapter.in.rest.dto.UserResponseDto;
import br.pucrs.constrsw.oauth.infrastructure.adapter.in.rest.dto.UserUpdateRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Adapter de entrada REST para os casos de uso de Users. Traduz requests HTTP
 * em value objects de dominio, invoca os casos de uso, e serializa a resposta.
 * Todos os endpoints exigem Bearer token (garantido pela SecurityConfig).
 */
@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "Gestao de usuarios (proxy do Keycloak Admin API)")
// Codigos comuns a todas as rotas; 404/409 ficam nos metodos em que se aplicam.
@ApiResponse(responseCode = "400", description = "Bad Request - erro na estrutura da chamada",
        content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
@ApiResponse(responseCode = "401", description = "Unauthorized - access token ausente ou invalido",
        content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
@ApiResponse(responseCode = "403", description = "Forbidden - access token nao concede permissao",
        content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
public class UserRestController {

    private final CreateUserUseCase createUser;
    private final ListUsersUseCase listUsers;
    private final GetUserUseCase getUser;
    private final UpdateUserUseCase updateUser;
    private final UpdatePasswordUseCase updatePassword;
    private final DisableUserUseCase disableUser;

    public UserRestController(CreateUserUseCase createUser,
                              ListUsersUseCase listUsers,
                              GetUserUseCase getUser,
                              UpdateUserUseCase updateUser,
                              UpdatePasswordUseCase updatePassword,
                              DisableUserUseCase disableUser) {
        this.createUser = createUser;
        this.listUsers = listUsers;
        this.getUser = getUser;
        this.updateUser = updateUser;
        this.updatePassword = updatePassword;
        this.disableUser = disableUser;
    }

    @Operation(summary = "Cria um novo usuario")
    @ApiResponse(responseCode = "201", description = "Created",
            content = @Content(schema = @Schema(implementation = UserResponseDto.class)))
    @ApiResponse(responseCode = "409", description = "Conflict - username ja existente",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @PostMapping
    public ResponseEntity<UserResponseDto> create(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @Valid @RequestBody UserCreateRequestDto request) {
        User created = createUser.execute(bearer, request.toDomain());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponseDto.fromDomain(created));
    }

    @Operation(summary = "Lista todos os usuarios, opcionalmente filtrando por enabled.")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponseDto.class))))
    @GetMapping
    public ResponseEntity<List<UserResponseDto>> list(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestParam(value = "enabled", required = false) Boolean enabled) {
        List<UserResponseDto> body = listUsers.execute(bearer, enabled).stream()
                .map(UserResponseDto::fromDomain)
                .toList();
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "Recupera um usuario pelo id.")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = UserResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "Not Found - usuario nao localizado",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> get(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable("id") String id) {
        return ResponseEntity.ok(UserResponseDto.fromDomain(getUser.execute(bearer, id)));
    }

    @Operation(summary = "Atualiza atributos de um usuario.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content)
    @ApiResponse(responseCode = "404", description = "Not Found - usuario nao localizado",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable("id") String id,
            @RequestBody UserUpdateRequestDto request) {
        updateUser.execute(bearer, id, request.toDomain());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Atualiza a senha de um usuario.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content)
    @ApiResponse(responseCode = "404", description = "Not Found - usuario nao localizado",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @PatchMapping("/{id}")
    public ResponseEntity<Void> patchPassword(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable("id") String id,
            @Valid @RequestBody PasswordUpdateRequestDto request) {
        updatePassword.execute(bearer, id, request.getPassword());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Exclusao logica: desabilita o usuario (enabled=false).")
    @ApiResponse(responseCode = "204", description = "No Content", content = @Content)
    @ApiResponse(responseCode = "404", description = "Not Found - usuario nao localizado",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disable(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable("id") String id) {
        disableUser.execute(bearer, id);
        return ResponseEntity.noContent().build();
    }
}
