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

import br.pucrs.constrsw.oauth.application.port.in.AttachRoleToUserUseCase;
import br.pucrs.constrsw.oauth.application.port.in.CreateRoleUseCase;
import br.pucrs.constrsw.oauth.application.port.in.DeleteRoleUseCase;
import br.pucrs.constrsw.oauth.application.port.in.DetachRoleFromUserUseCase;
import br.pucrs.constrsw.oauth.application.port.in.GetRoleUseCase;
import br.pucrs.constrsw.oauth.application.port.in.ListRolesUseCase;
import br.pucrs.constrsw.oauth.application.port.in.ReplaceRoleUseCase;
import br.pucrs.constrsw.oauth.application.port.in.UpdateRoleUseCase;
import br.pucrs.constrsw.oauth.domain.model.Role;
import br.pucrs.constrsw.oauth.infrastructure.adapter.in.rest.dto.ErrorResponseDto;
import br.pucrs.constrsw.oauth.infrastructure.adapter.in.rest.dto.RoleCreateRequestDto;
import br.pucrs.constrsw.oauth.infrastructure.adapter.in.rest.dto.RoleReplaceRequestDto;
import br.pucrs.constrsw.oauth.infrastructure.adapter.in.rest.dto.RoleResponseDto;
import br.pucrs.constrsw.oauth.infrastructure.adapter.in.rest.dto.RoleUpdateRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Adapter de entrada REST para os casos de uso de Roles. Traduz requests HTTP
 * em value objects de dominio, invoca os casos de uso, e serializa a resposta.
 * Todos os endpoints exigem Bearer token (garantido pela SecurityConfig).
 */
@RestController
@RequestMapping("/roles")
@Tag(name = "Roles", description = "Gestao de roles e atribuicao a usuarios (proxy do Keycloak Admin API)")
// Codigos comuns a todas as rotas; 404/409 ficam nos metodos em que se aplicam.
@ApiResponse(responseCode = "400", description = "Bad Request - erro na estrutura da chamada",
        content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
@ApiResponse(responseCode = "401", description = "Unauthorized - access token ausente ou invalido",
        content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
@ApiResponse(responseCode = "403", description = "Forbidden - access token nao concede permissao",
        content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
public class RoleRestController {

    private final CreateRoleUseCase createRole;
    private final ListRolesUseCase listRoles;
    private final GetRoleUseCase getRole;
    private final ReplaceRoleUseCase replaceRole;
    private final UpdateRoleUseCase updateRole;
    private final DeleteRoleUseCase deleteRole;
    private final AttachRoleToUserUseCase attachRoleToUser;
    private final DetachRoleFromUserUseCase detachRoleFromUser;

    public RoleRestController(CreateRoleUseCase createRole,
                              ListRolesUseCase listRoles,
                              GetRoleUseCase getRole,
                              ReplaceRoleUseCase replaceRole,
                              UpdateRoleUseCase updateRole,
                              DeleteRoleUseCase deleteRole,
                              AttachRoleToUserUseCase attachRoleToUser,
                              DetachRoleFromUserUseCase detachRoleFromUser) {
        this.createRole = createRole;
        this.listRoles = listRoles;
        this.getRole = getRole;
        this.replaceRole = replaceRole;
        this.updateRole = updateRole;
        this.deleteRole = deleteRole;
        this.attachRoleToUser = attachRoleToUser;
        this.detachRoleFromUser = detachRoleFromUser;
    }

    @Operation(summary = "Cria um novo role")
    @ApiResponse(responseCode = "201", description = "Created",
            content = @Content(schema = @Schema(implementation = RoleResponseDto.class)))
    @ApiResponse(responseCode = "409", description = "Conflict - role ja existente",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @PostMapping
    public ResponseEntity<RoleResponseDto> create(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @Valid @RequestBody RoleCreateRequestDto request) {
        Role created = createRole.execute(bearer, request.toDomain());
        return ResponseEntity.status(HttpStatus.CREATED).body(RoleResponseDto.fromDomain(created));
    }

    @Operation(summary = "Lista todos os roles, opcionalmente filtrando por enabled.")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = RoleResponseDto.class))))
    @GetMapping
    public ResponseEntity<List<RoleResponseDto>> list(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestParam(value = "enabled", required = false) Boolean enabled) {
        List<RoleResponseDto> body = listRoles.execute(bearer, enabled).stream()
                .map(RoleResponseDto::fromDomain)
                .toList();
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "Recupera um role pelo id.")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = RoleResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "Not Found - role nao localizado",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @GetMapping("/{id}")
    public ResponseEntity<RoleResponseDto> get(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable("id") String id) {
        return ResponseEntity.ok(RoleResponseDto.fromDomain(getRole.execute(bearer, id)));
    }

    @Operation(summary = "Substitui um role inteiro.",
            description = "name e obrigatorio; description ausente apaga a descricao e "
                    + "enabled ausente vale true. Para alterar so alguns campos, use o PATCH.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content)
    @ApiResponse(responseCode = "404", description = "Not Found - role nao localizado",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @ApiResponse(responseCode = "409", description = "Conflict - ja existe role com esse nome",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @PutMapping("/{id}")
    public ResponseEntity<Void> replace(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable("id") String id,
            @Valid @RequestBody RoleReplaceRequestDto request) {
        replaceRole.execute(bearer, id, request.toDomain());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Atualiza parcialmente um role (so os campos enviados).")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content)
    @ApiResponse(responseCode = "404", description = "Not Found - role nao localizado",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @ApiResponse(responseCode = "409", description = "Conflict - ja existe role com esse nome",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @PatchMapping("/{id}")
    public ResponseEntity<Void> patch(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable("id") String id,
            @RequestBody RoleUpdateRequestDto request) {
        updateRole.execute(bearer, id, request.toDomain());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Exclusao logica: desabilita o role (enabled=false).")
    @ApiResponse(responseCode = "204", description = "No Content", content = @Content)
    @ApiResponse(responseCode = "404", description = "Not Found - role nao localizado",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable("id") String id) {
        deleteRole.execute(bearer, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Atribui um role a um usuario.")
    @ApiResponse(responseCode = "204", description = "No Content", content = @Content)
    @ApiResponse(responseCode = "404", description = "Not Found - role ou usuario nao localizado",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @PostMapping("/{roleId}/users/{userId}")
    public ResponseEntity<Void> attachToUser(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable("roleId") String roleId,
            @PathVariable("userId") String userId) {
        attachRoleToUser.execute(bearer, userId, roleId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remove a atribuicao de um role a um usuario.")
    @ApiResponse(responseCode = "204", description = "No Content", content = @Content)
    @ApiResponse(responseCode = "404", description = "Not Found - role ou usuario nao localizado",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @DeleteMapping("/{roleId}/users/{userId}")
    public ResponseEntity<Void> detachFromUser(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable("roleId") String roleId,
            @PathVariable("userId") String userId) {
        detachRoleFromUser.execute(bearer, userId, roleId);
        return ResponseEntity.noContent().build();
    }
}
