package com.rewabank.auth.service;

import com.rewabank.auth.dto.RegisterRequest;
import com.rewabank.auth.exception.AuthException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KeycloakUserServiceTest {

    @Mock private Keycloak            keycloak;
    @Mock private RealmResource       realmResource;
    @Mock private UsersResource       usersResource;
    @Mock private RolesResource       rolesResource;
    @Mock private RoleResource        roleResource;
    @Mock private UserResource        userResource;
    @Mock private RoleMappingResource roleMappingResource;
    @Mock private RoleScopeResource   roleScopeResource;
    @Mock private Response            response;

    @InjectMocks
    private KeycloakUserService keycloakUserService;

    private static final String REALM          = "rewabank";
    private static final String NEW_KEYCLOAK_ID = UUID.randomUUID().toString();

    private RegisterRequest validRequest;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(keycloakUserService, "realm", REALM);

        validRequest = new RegisterRequest(
                "Rahul Sharma",
                "rahul@example.com",
                "+919876543210",
                "SecurePass@123"
        );

        // Wire common Keycloak chain
        when(keycloak.realm(REALM)).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(realmResource.roles()).thenReturn(rolesResource);
        when(rolesResource.get(anyString())).thenReturn(roleResource);
        when(roleResource.toRepresentation()).thenReturn(new RoleRepresentation());
        when(usersResource.get(anyString())).thenReturn(userResource);
        when(userResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
        // roleScopeResource.add() is void — default mock no-op is correct
    }

    // ── createUser: happy path ────────────────────────────────────────────────

    @Test
    void createUser_ShouldReturnKeycloakUserId_WhenCreationSucceeds() {
        when(usersResource.searchByEmail(validRequest.email(), true))
                .thenReturn(Collections.emptyList());
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(201);
        when(response.getHeaderString("Location"))
                .thenReturn("http://keycloak/auth/realms/rewabank/users/" + NEW_KEYCLOAK_ID);

        String result = keycloakUserService.createUser(validRequest);

        assertEquals(NEW_KEYCLOAK_ID, result);
    }

    @Test
    void createUser_ShouldSetEmailAndUsername_WhenCreatingUser() {
        when(usersResource.searchByEmail(validRequest.email(), true))
                .thenReturn(Collections.emptyList());
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(201);
        when(response.getHeaderString("Location"))
                .thenReturn("http://keycloak/auth/realms/rewabank/users/" + NEW_KEYCLOAK_ID);

        keycloakUserService.createUser(validRequest);

        verify(usersResource).create(argThat(u ->
                "rahul@example.com".equals(u.getEmail()) &&
                "rahul@example.com".equals(u.getUsername()) &&
                u.isEnabled()
        ));
    }

    @Test
    void createUser_ShouldAssignCustomerRole_AfterCreation() {
        when(usersResource.searchByEmail(validRequest.email(), true))
                .thenReturn(Collections.emptyList());
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(201);
        when(response.getHeaderString("Location"))
                .thenReturn("http://keycloak/auth/realms/rewabank/users/" + NEW_KEYCLOAK_ID);

        keycloakUserService.createUser(validRequest);

        verify(rolesResource).get("CUSTOMER");
    }

    // ── createUser: name parsing ──────────────────────────────────────────────

    @Test
    void createUser_ShouldExtractFirstAndLastName_FromFullName() {
        when(usersResource.searchByEmail(validRequest.email(), true))
                .thenReturn(Collections.emptyList());
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(201);
        when(response.getHeaderString("Location"))
                .thenReturn("http://keycloak/auth/realms/rewabank/users/" + NEW_KEYCLOAK_ID);

        keycloakUserService.createUser(validRequest);

        verify(usersResource).create(argThat(u ->
                "Rahul".equals(u.getFirstName()) && "Sharma".equals(u.getLastName())
        ));
    }

    @Test
    void createUser_ShouldSetEmptyLastName_WhenOnlyFirstNameProvided() {
        RegisterRequest singleNameRequest = new RegisterRequest(
                "Rahul", "rahul@example.com", "+919876543210", "SecurePass@123");
        when(usersResource.searchByEmail(singleNameRequest.email(), true))
                .thenReturn(Collections.emptyList());
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(201);
        when(response.getHeaderString("Location"))
                .thenReturn("http://keycloak/auth/realms/rewabank/users/" + NEW_KEYCLOAK_ID);

        keycloakUserService.createUser(singleNameRequest);

        verify(usersResource).create(argThat(u ->
                "Rahul".equals(u.getFirstName()) && "".equals(u.getLastName())
        ));
    }

    @Test
    void createUser_ShouldUseLastWord_AsLastName_ForMultiPartName() {
        RegisterRequest multiNameRequest = new RegisterRequest(
                "Rahul Kumar Sharma", "rahul@example.com", "+919876543210", "SecurePass@123");
        when(usersResource.searchByEmail(multiNameRequest.email(), true))
                .thenReturn(Collections.emptyList());
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(201);
        when(response.getHeaderString("Location"))
                .thenReturn("http://keycloak/auth/realms/rewabank/users/" + NEW_KEYCLOAK_ID);

        keycloakUserService.createUser(multiNameRequest);

        verify(usersResource).create(argThat(u ->
                "Rahul".equals(u.getFirstName()) && "Sharma".equals(u.getLastName())
        ));
    }

    // ── createUser: error cases ───────────────────────────────────────────────

    @Test
    void createUser_ShouldThrowAuthException_WhenEmailExistsInKeycloak() {
        when(usersResource.searchByEmail(validRequest.email(), true))
                .thenReturn(List.of(new UserRepresentation()));

        AuthException ex = assertThrows(AuthException.class,
                () -> keycloakUserService.createUser(validRequest));
        assertEquals("AUTH_001", ex.getErrorCode());
        verify(usersResource, never()).create(any());
    }

    @Test
    void createUser_ShouldThrowAuthException_WhenKeycloakReturns409() {
        when(usersResource.searchByEmail(validRequest.email(), true))
                .thenReturn(Collections.emptyList());
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(409);

        AuthException ex = assertThrows(AuthException.class,
                () -> keycloakUserService.createUser(validRequest));
        assertEquals("AUTH_001", ex.getErrorCode());
    }

    @Test
    void createUser_ShouldThrowAuthException_WhenKeycloakReturns500() {
        when(usersResource.searchByEmail(validRequest.email(), true))
                .thenReturn(Collections.emptyList());
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(500);

        AuthException ex = assertThrows(AuthException.class,
                () -> keycloakUserService.createUser(validRequest));
        assertEquals("AUTH_002", ex.getErrorCode());
    }

    // ── createUser: role assignment failure ──────────────────────────────────

    @Test
    void createUser_ShouldThrowAuth002_WhenRoleAssignmentFails() {
        when(usersResource.searchByEmail(validRequest.email(), true))
                .thenReturn(Collections.emptyList());
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(201);
        when(response.getHeaderString("Location"))
                .thenReturn("http://keycloak/auth/realms/rewabank/users/" + NEW_KEYCLOAK_ID);
        doThrow(new RuntimeException("Keycloak role not found"))
                .when(roleResource).toRepresentation();

        AuthException ex = assertThrows(AuthException.class,
                () -> keycloakUserService.createUser(validRequest));
        assertEquals("AUTH_002", ex.getErrorCode());
    }

    @Test
    void createUser_ShouldDeleteOrphanUser_WhenRoleAssignmentFails() {
        when(usersResource.searchByEmail(validRequest.email(), true))
                .thenReturn(Collections.emptyList());
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(201);
        when(response.getHeaderString("Location"))
                .thenReturn("http://keycloak/auth/realms/rewabank/users/" + NEW_KEYCLOAK_ID);
        doThrow(new RuntimeException("Role not found")).when(roleResource).toRepresentation();

        assertThrows(AuthException.class, () -> keycloakUserService.createUser(validRequest));

        // Orphan user must be deleted to allow re-registration
        verify(userResource).remove();
    }

    @Test
    void createUser_ShouldStillThrowAuth002_WhenRoleAssignmentAndCleanupBothFail() {
        when(usersResource.searchByEmail(validRequest.email(), true))
                .thenReturn(Collections.emptyList());
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(201);
        when(response.getHeaderString("Location"))
                .thenReturn("http://keycloak/auth/realms/rewabank/users/" + NEW_KEYCLOAK_ID);
        doThrow(new RuntimeException("Role not found")).when(roleResource).toRepresentation();
        doThrow(new RuntimeException("Delete also failed")).when(userResource).remove();

        // Exception must propagate regardless of cleanup failure
        AuthException ex = assertThrows(AuthException.class,
                () -> keycloakUserService.createUser(validRequest));
        assertEquals("AUTH_002", ex.getErrorCode());
    }

    // ── lockUser ──────────────────────────────────────────────────────────────

    @Test
    void lockUser_ShouldDisableUser_InKeycloak() {
        UserRepresentation userRep = new UserRepresentation();
        userRep.setEnabled(true);
        when(userResource.toRepresentation()).thenReturn(userRep);

        keycloakUserService.lockUser(NEW_KEYCLOAK_ID);

        assertFalse(userRep.isEnabled());
        verify(userResource).update(argThat(u -> !u.isEnabled()));
    }
}