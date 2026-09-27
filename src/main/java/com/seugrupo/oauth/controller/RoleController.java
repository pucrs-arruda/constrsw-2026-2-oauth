package com.seugrupo.oauth.controller;

import com.seugrupo.oauth.dto.CreateRoleRequest;
import com.seugrupo.oauth.dto.PatchRoleRequest;
import com.seugrupo.oauth.dto.RoleResponse;
import com.seugrupo.oauth.dto.UpdateRoleRequest;
import com.seugrupo.oauth.metrics.BusinessMetrics;
import com.seugrupo.oauth.service.KeycloakRoleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping(path = "/roles", produces = MediaType.APPLICATION_JSON_VALUE)
public class RoleController {

    private final KeycloakRoleService roleService;
    private final BusinessMetrics metrics;

    public RoleController(KeycloakRoleService roleService, BusinessMetrics metrics) {
        this.roleService = roleService;
        this.metrics = metrics;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<RoleResponse> create(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody CreateRoleRequest request
    ) {
        RoleResponse created = roleService.create(authorization, request);
        metrics.recordManagementOperation("role_created");
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    List<RoleResponse> list(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return roleService.list(authorization);
    }

    @GetMapping("/{id}")
    RoleResponse getById(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String id
    ) {
        return roleService.getById(authorization, id);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> update(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String id,
            @Valid @RequestBody UpdateRoleRequest request
    ) {
        roleService.update(authorization, id, request);
        metrics.recordManagementOperation("role_updated");
        return ResponseEntity.ok().build();
    }

    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> patch(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String id,
            @Valid @RequestBody PatchRoleRequest request
    ) {
        roleService.patch(authorization, id, request);
        metrics.recordManagementOperation("role_patched");
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String id
    ) {
        roleService.delete(authorization, id);
        metrics.recordManagementOperation("role_deleted");
        return ResponseEntity.noContent().build();
    }
}
