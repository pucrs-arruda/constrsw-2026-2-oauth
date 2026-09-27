package br.pucrs.constrsw.oauth.controller;

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
import org.springframework.web.bind.annotation.RestController;

import br.pucrs.constrsw.oauth.dto.ApiErrorResponse;
import br.pucrs.constrsw.oauth.dto.RoleDto;
import br.pucrs.constrsw.oauth.domain.Role;
import br.pucrs.constrsw.oauth.error.UnauthorizedException;
import br.pucrs.constrsw.oauth.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Roles", description = "Manage roles integrated with Keycloak")
@SecurityRequirement(name = "bearerAuth") // ajustar
@RestController
@RequestMapping("/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    private String requireAuth(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }
        return authorization;
    }

    @Operation(summary = "Create a role", description = "Creates a role in Keycloak")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Role already exists", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping
    public ResponseEntity<RoleDto> createRole(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody RoleDto role) {
        String auth = requireAuth(authorization);
        Role created = roleService.createRole(auth, role.toDomain());
        return ResponseEntity.status(HttpStatus.CREATED).body(RoleDto.fromDomain(created));
    }

    @Operation(summary = "List roles", description = "Lists roles from Keycloak")
    @ApiResponse(responseCode = "200", description = "Roles returned")
    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping
    public ResponseEntity<List<RoleDto>> getAll(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization) {
        String auth = requireAuth(authorization);
        List<Role> roles = roleService.getAllRoles(auth);
        return ResponseEntity.ok(roles == null ? null : roles.stream().map(RoleDto::fromDomain).toList());
    }

    @Operation(summary = "Get a role", description = "Gets a role by Keycloak id")
    @ApiResponse(responseCode = "200", description = "Role returned")
    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Role not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/{id}")
    public ResponseEntity<RoleDto> getById(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id) {
        String auth = requireAuth(authorization);
        Role role = roleService.getRoleById(auth, id);
        return ResponseEntity.ok(RoleDto.fromDomain(role));
    }

    @Operation(summary = "Update a role", description = "Updates an existing role")
    @ApiResponse(responseCode = "200", description = "Role updated")
    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Role not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PutMapping("/{id}")
    public ResponseEntity<RoleDto> update(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id,
            @RequestBody RoleDto role) {
        String auth = requireAuth(authorization);
        Role updated = roleService.updateRole(auth, id, role.toDomain());
        return ResponseEntity.ok(RoleDto.fromDomain(updated));
    }

    @Operation(summary = "Partially update a role", description = "Updates specific role attributes")
    @ApiResponse(responseCode = "200", description = "Role updated")
    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Role not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PatchMapping("/{id}")
    public ResponseEntity<RoleDto> patch(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id,
            @RequestBody RoleDto partial) {
        String auth = requireAuth(authorization);
        Role updated = roleService.patchRole(auth, id, partial.toDomain());
        return ResponseEntity.ok(RoleDto.fromDomain(updated));
    }

    @Operation(summary = "Disable a role", description = "Logically deletes a role by disabling it in Keycloak")
    @ApiResponse(responseCode = "204", description = "Role disabled")
    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Role not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id) {
        String auth = requireAuth(authorization);
        roleService.deleteRole(auth, id);
        return ResponseEntity.noContent().build();
    }
}
