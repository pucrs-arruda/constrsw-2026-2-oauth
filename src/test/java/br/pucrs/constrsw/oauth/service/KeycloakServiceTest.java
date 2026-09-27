package br.pucrs.constrsw.oauth.service;

import br.pucrs.constrsw.oauth.exception.KeycloakException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class KeycloakServiceTest {

    private KeycloakService keycloakService;

    @BeforeEach
    void setUp() {
        keycloakService = new KeycloakService();
    }

    @Test
    void testAdministratorAccess() {
        List<String> adminRoles = List.of("administrator");

        assertTrue(keycloakService.hasAccessToResource(adminRoles, "resources"));
        assertTrue(keycloakService.hasAccessToResource(adminRoles, "rooms"));
        assertTrue(keycloakService.hasAccessToResource(adminRoles, "/professors"));
        assertTrue(keycloakService.hasAccessToResource(adminRoles, "students"));

        assertFalse(keycloakService.hasAccessToResource(adminRoles, "courses"));
        assertFalse(keycloakService.hasAccessToResource(adminRoles, "lessons"));
    }

    @Test
    void testCoordinatorAccess() {
        List<String> coordinatorRoles = List.of("coordinator");

        assertTrue(keycloakService.hasAccessToResource(coordinatorRoles, "courses"));
        assertTrue(keycloakService.hasAccessToResource(coordinatorRoles, "/classes"));

        assertFalse(keycloakService.hasAccessToResource(coordinatorRoles, "rooms"));
        assertFalse(keycloakService.hasAccessToResource(coordinatorRoles, "lessons"));
    }

    @Test
    void testProfessorAccess() {
        List<String> professorRoles = List.of("professor");

        assertTrue(keycloakService.hasAccessToResource(professorRoles, "lessons"));
        assertTrue(keycloakService.hasAccessToResource(professorRoles, "/reservations"));

        assertFalse(keycloakService.hasAccessToResource(professorRoles, "students"));
        assertFalse(keycloakService.hasAccessToResource(professorRoles, "courses"));
    }

    @Test
    void testStudentAccess() {
        List<String> studentRoles = List.of("student");

        assertFalse(keycloakService.hasAccessToResource(studentRoles, "lessons"));
        assertFalse(keycloakService.hasAccessToResource(studentRoles, "courses"));
        assertFalse(keycloakService.hasAccessToResource(studentRoles, "rooms"));
    }

    @Test
    void testNormalizeResource() {
        assertEquals("lessons", keycloakService.normalizeResource("/lessons"));
        assertEquals("courses", keycloakService.normalizeResource("COURSES"));
        assertEquals("rooms", keycloakService.normalizeResource("/ROOMS/"));
    }

    @Test
    void testExtractPrimaryRole() {
        assertNull(keycloakService.extractPrimaryRole(null));
        assertNull(keycloakService.extractPrimaryRole(List.of()));

        // Prioridade de cargos de negócio
        assertEquals("administrator", keycloakService.extractPrimaryRole(List.of("default-roles-constrsw", "administrator", "offline_access")));
        assertEquals("professor", keycloakService.extractPrimaryRole(List.of("default-roles-constrsw", "professor", "uma_authorization")));
        assertEquals("student", keycloakService.extractPrimaryRole(List.of("student", "default-roles-constrsw")));
        assertEquals("coordinator", keycloakService.extractPrimaryRole(List.of("coordinator")));

        // Cargo customizado (ignora default-roles e offline_access)
        assertEquals("monitor", keycloakService.extractPrimaryRole(List.of("default-roles-constrsw", "offline_access", "monitor")));
    }

    @Test
    void adminOperationsRejectMissingBearerToken() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        try {
            KeycloakException ex = assertThrows(KeycloakException.class, keycloakService::getCallerToken);
            assertEquals(401, ex.getStatus().value());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void adminOperationsAcceptActiveTokenWithoutOpenidScope() {
        RestTemplate client = mock(RestTemplate.class);
        KeycloakService service = new KeycloakService(client);
        ReflectionTestUtils.setField(service, "keycloakUrl", "http://keycloak");
        ReflectionTestUtils.setField(service, "realm", "constrsw");
        ReflectionTestUtils.setField(service, "clientId", "oauth");
        ReflectionTestUtils.setField(service, "clientSecret", "test-secret");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer caller-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            when(client.postForEntity(contains("/token/introspect"), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(Map.of("active", true, "scope", "email profile")));
            assertEquals("caller-token", service.getCallerToken());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void rejectsInactiveCallerToken() {
        RestTemplate client = mock(RestTemplate.class);
        KeycloakService service = new KeycloakService(client);
        ReflectionTestUtils.setField(service, "keycloakUrl", "http://keycloak");
        ReflectionTestUtils.setField(service, "realm", "constrsw");
        ReflectionTestUtils.setField(service, "clientId", "oauth");
        ReflectionTestUtils.setField(service, "clientSecret", "test-secret");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer expired-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            when(client.postForEntity(contains("/token/introspect"), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(Map.of("active", false)));
            KeycloakException ex = assertThrows(KeycloakException.class, service::getCallerToken);
            assertEquals(401, ex.getStatus().value());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void listsUsersBeyondKeycloakFirstPage() {
        RestTemplate client = mock(RestTemplate.class);
        KeycloakService service = new KeycloakService(client);
        ReflectionTestUtils.setField(service, "keycloakUrl", "http://keycloak");
        ReflectionTestUtils.setField(service, "realm", "constrsw");
        ReflectionTestUtils.setField(service, "clientId", "oauth");
        ReflectionTestUtils.setField(service, "clientSecret", "test-secret");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer caller-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            when(client.postForEntity(contains("/token/introspect"), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(Map.of("active", true)));
            List<Map<String, Object>> firstPage = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                firstPage.add(new HashMap<>(Map.of("id", "id-" + i, "username", "user" + i + "@example.com", "enabled", true)));
            }
            Map<String, Object> lastUser = new HashMap<>(Map.of("id", "id-100", "username", "user100@example.com", "enabled", true));
            when(client.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                    .thenAnswer(invocation -> {
                        String url = invocation.getArgument(0);
                        if (url.contains("role-mappings")) return ResponseEntity.ok(List.of());
                        if (url.contains("first=100")) return ResponseEntity.ok(List.of(lastUser));
                        return ResponseEntity.ok(firstPage);
                    });
            assertEquals(101, service.getUsers(null).size());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void updatingEmailAlsoUpdatesUsername() {
        RestTemplate client = mock(RestTemplate.class);
        KeycloakService service = new KeycloakService(client);
        ReflectionTestUtils.setField(service, "keycloakUrl", "http://keycloak");
        ReflectionTestUtils.setField(service, "realm", "constrsw");
        ReflectionTestUtils.setField(service, "clientId", "oauth");
        ReflectionTestUtils.setField(service, "clientSecret", "test-secret");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer caller-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            when(client.postForEntity(contains("/token/introspect"), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(Map.of("active", true)));
            Map<String, Object> user = new HashMap<>(Map.of("id", "id-1", "username", "old@example.com", "email", "old@example.com", "enabled", true));
            when(client.exchange(contains("/users/id-1"), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                    .thenReturn(ResponseEntity.ok(user));
            when(client.exchange(contains("role-mappings"), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                    .thenReturn(ResponseEntity.ok(List.of()));
            service.updateUser("id-1", new br.pucrs.constrsw.oauth.dto.UpdateUserRequest("new@example.com", null, null, null));
            org.mockito.ArgumentCaptor<HttpEntity> payload = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
            verify(client).put(contains("/users/id-1"), payload.capture());
            Map<?, ?> sent = (Map<?, ?>) payload.getValue().getBody();
            assertEquals("new@example.com", sent.get("username"));
            assertEquals("new@example.com", sent.get("email"));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void rejectsKeycloakCreationWithoutLocationId() {
        RestTemplate client = mock(RestTemplate.class);
        KeycloakService service = new KeycloakService(client);
        ReflectionTestUtils.setField(service, "keycloakUrl", "http://keycloak");
        ReflectionTestUtils.setField(service, "realm", "constrsw");
        ReflectionTestUtils.setField(service, "clientId", "oauth");
        ReflectionTestUtils.setField(service, "clientSecret", "test-secret");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer caller-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            when(client.postForEntity(contains("/token/introspect"), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(Map.of("active", true)));
            when(client.postForEntity(contains("/users"), any(HttpEntity.class), eq(Void.class)))
                    .thenReturn(ResponseEntity.status(201).build());
            var create = new br.pucrs.constrsw.oauth.dto.CreateUserRequest(
                    "new@example.com", "new@example.com", "New", "User", true, "password");
            KeycloakException ex = assertThrows(KeycloakException.class, () -> service.createUser(create));
            assertEquals(502, ex.getStatus().value());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
