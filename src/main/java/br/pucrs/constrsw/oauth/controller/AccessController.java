package br.pucrs.constrsw.oauth.controller;

import br.pucrs.constrsw.oauth.dto.ApiErrorResponse;
import br.pucrs.constrsw.oauth.error.UnauthorizedException;
import br.pucrs.constrsw.oauth.service.AccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Authorization")
public class AccessController {

    private final AccessService accessService;

    public AccessController(AccessService accessService) {
        this.accessService = accessService;
    }

    @Operation(summary = "Evaluate access to a resource",
            description = "Checks the bearer access token against the resource URI configured in Keycloak")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Access granted"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "502", description = "Identity provider failure",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/access")
    public ResponseEntity<Void> evaluate(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String resource) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() == 7) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }
        return accessService.hasAccess(authorization, resource)
                ? ResponseEntity.ok().build()
                : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
