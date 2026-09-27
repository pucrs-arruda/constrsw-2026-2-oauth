package br.pucrs.constrsw.oauth.controller;

import br.pucrs.constrsw.oauth.dto.ApiErrorResponse;
import br.pucrs.constrsw.oauth.dto.CreateUserRequest;
import br.pucrs.constrsw.oauth.dto.UpdatePasswordRequest;
import br.pucrs.constrsw.oauth.dto.UpdateUserRequest;
import br.pucrs.constrsw.oauth.dto.UserResponse;
import br.pucrs.constrsw.oauth.domain.CreateUserCommand;
import br.pucrs.constrsw.oauth.domain.UpdateUserCommand;
import br.pucrs.constrsw.oauth.domain.User;
import br.pucrs.constrsw.oauth.error.UnauthorizedException;
import br.pucrs.constrsw.oauth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import java.util.List;

@RestController
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Users")
@RequestMapping(path = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

        private String requireAuth(String authorization) {
                if (authorization == null || !authorization.startsWith("Bearer ")) {
                        throw new UnauthorizedException("Missing or invalid Authorization header");
                }
                return authorization;
        }

    @Operation(summary = "Create a user", description = "Creates a user in the identity provider")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "User already exists",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "501", description = "User management integration not implemented",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Identity provider failure",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<UserResponse> create(
                        @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
                        @Valid @RequestBody CreateUserRequest request) {
                User user = userService.create(requireAuth(authorization),
                        new CreateUserCommand(request.username(), request.password(), request.firstName(), request.lastName()));
                return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    @Operation(summary = "List users", description = "Lists users from the identity provider")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Users returned"),
            @ApiResponse(responseCode = "501", description = "User management integration not implemented",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Identity provider failure",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
        public ResponseEntity<List<UserResponse>> findAll(
                        @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization) {
                return ResponseEntity.ok(userService.findAll(requireAuth(authorization)).stream()
                        .map(UserController::toResponse).toList());
    }

    @Operation(summary = "Get a user", description = "Gets a user by identity provider id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User returned"),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "501", description = "User management integration not implemented",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Identity provider failure",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{id}")
        public ResponseEntity<UserResponse> findById(
                        @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
                        @PathVariable String id) {
                return ResponseEntity.ok(toResponse(userService.findById(requireAuth(authorization), id)));
    }

    @Operation(summary = "Update a user", description = "Updates user profile data")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "501", description = "User management integration not implemented",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Identity provider failure",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> update(
                        @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id,
            @Valid @RequestBody UpdateUserRequest request) {
                userService.update(requireAuth(authorization), id,
                        new UpdateUserCommand(request.firstName(), request.lastName(), request.enabled()));
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Update a user's password", description = "Replaces a user's password")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "501", description = "User management integration not implemented",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Identity provider failure",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updatePassword(
                        @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id,
            @Valid @RequestBody UpdatePasswordRequest request) {
                userService.updatePassword(requireAuth(authorization), id, request.password());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Disable a user", description = "Logically deletes a user by disabling it")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User disabled"),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "501", description = "User management integration not implemented",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Identity provider failure",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
        public ResponseEntity<Void> disable(
                        @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
                        @PathVariable String id) {
                userService.disable(requireAuth(authorization), id);
        return ResponseEntity.noContent().build();
    }

    private static UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(user.id(), user.username(), user.firstName(), user.lastName(), user.enabled());
    }
}
