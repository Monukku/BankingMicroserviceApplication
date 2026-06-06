package com.rewabank.auth.service;

import com.rewabank.auth.dto.RegisterRequest;
import com.rewabank.auth.exception.AuthException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakUserService {

    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    public String createUser(RegisterRequest request) {
        UsersResource usersResource = keycloak.realm(realm).users();

        // Check if user already exists in Keycloak
        List<UserRepresentation> existing = usersResource.searchByEmail(request.email(), true);
        if (!existing.isEmpty()) {
            throw new AuthException("AUTH_001", "User already exists with this email");
        }

        UserRepresentation user = new UserRepresentation();
        user.setEnabled(true);
        user.setEmailVerified(false);
        user.setEmail(request.email());
        user.setUsername(request.email());
        user.setFirstName(extractFirstName(request.fullName()));
        user.setLastName(extractLastName(request.fullName()));
        user.setAttributes(java.util.Map.of(
                "mobileNumber", List.of(request.mobileNumber()),
                "fullName",     List.of(request.fullName())
        ));

        // Set password credential
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.password());
        credential.setTemporary(false);
        user.setCredentials(List.of(credential));

        Response response = usersResource.create(user);

        if (response.getStatus() == 201) {
            String locationHeader = response.getHeaderString("Location");
            String keycloakUserId = locationHeader.substring(locationHeader.lastIndexOf("/") + 1);
            log.info("User created in Keycloak: {}", keycloakUserId);

            // Assign default CUSTOMER role — if this fails, delete the created user
            // to avoid an orphan account that can never log in and blocks re-registration.
            try {
                assignRole(keycloakUserId, "CUSTOMER");
            } catch (Exception e) {
                log.error("Role assignment failed for {} — deleting orphan user: {}",
                        keycloakUserId, e.getMessage());
                deleteUser(keycloakUserId);
                throw new AuthException("AUTH_002", "User registration failed: role assignment error");
            }

            return keycloakUserId;
        } else if (response.getStatus() == 409) {
            throw new AuthException("AUTH_001", "User already exists");
        } else {
            log.error("Keycloak user creation failed — status: {}", response.getStatus());
            throw new AuthException("AUTH_002", "User registration failed");
        }
    }

    public void assignRole(String keycloakUserId, String roleName) {
        var roleRepresentation = keycloak.realm(realm)
                .roles()
                .get(roleName)
                .toRepresentation();

        keycloak.realm(realm)
                .users()
                .get(keycloakUserId)
                .roles()
                .realmLevel()
                .add(List.of(roleRepresentation));

        log.info("Role {} assigned to user {}", roleName, keycloakUserId);
    }

    private void deleteUser(String keycloakUserId) {
        try {
            keycloak.realm(realm).users().get(keycloakUserId).remove();
            log.info("Orphan user deleted from Keycloak: {}", keycloakUserId);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to delete orphan Keycloak user {} — manual cleanup required: {}",
                    keycloakUserId, e.getMessage());
        }
    }

    public void lockUser(String keycloakUserId) {
        UserRepresentation user = keycloak.realm(realm)
                .users()
                .get(keycloakUserId)
                .toRepresentation();
        user.setEnabled(false);
        keycloak.realm(realm).users().get(keycloakUserId).update(user);
        log.warn("User locked in Keycloak: {}", keycloakUserId);
    }

    private String extractFirstName(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        return parts[0];
    }

    private String extractLastName(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        return parts.length > 1 ? parts[parts.length - 1] : "";
    }
}
