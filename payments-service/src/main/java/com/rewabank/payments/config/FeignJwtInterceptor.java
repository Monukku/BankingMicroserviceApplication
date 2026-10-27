package com.rewabank.payments.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Relays the caller's JWT to downstream services (accounts-service), same pattern as
 * cards-service / transactions-service. Payments only ever calls accounts for the
 * paying user's OWN account, so the user token is the correct identity — accounts'
 * ownership check then returns that account.
 *
 * If there is no JWT in the SecurityContext (e.g. an async / no-user thread), attach
 * nothing and warn — the resulting downstream 401 is the correct signal, not something
 * to paper over with a forged or missing identity.
 *
 * Registered as a @Component so Spring Cloud OpenFeign applies it to every Feign client
 * (payments only has the accounts client).
 */
@Slf4j
@Component
public class FeignJwtInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            template.header("Authorization", "Bearer " + jwtAuth.getToken().getTokenValue());
        } else {
            log.warn("No JWT in SecurityContext for downstream call to '{}' — sending no "
                    + "Authorization header (downstream 401 expected)", template.path());
        }
    }
}