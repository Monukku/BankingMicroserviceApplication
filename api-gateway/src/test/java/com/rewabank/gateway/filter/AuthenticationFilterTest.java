package com.rewabank.gateway.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationFilterTest {

    @Spy  FilterUtility       filterUtility;
    @InjectMocks AuthenticationFilter filter;

    private Jwt jwtWith(String subject, String email) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        when(jwt.getClaimAsString("email")).thenReturn(email);
        return jwt;
    }

    // ── JWT authenticated path ─────────────────────────────────────────────────

    @Test
    void filter_jwtAuthenticated_injectsUserHeaders() {
        // Kills: NullReturn on chain.filter(mutated), NullReturn on .map() lambda,
        // NegateConditionals on userId != null, email != null
        Jwt jwt = jwtWith("user-123", "user@rewabank.com");
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/accounts").build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
        ).verifyComplete();

        HttpHeaders headers = captor.getValue().getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-123");
        assertThat(headers.getFirst("X-User-Email")).isEqualTo("user@rewabank.com");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("CUSTOMER");
    }

    @Test
    void filter_jwtAuthenticated_multipleRoles_joinsWithComma() {
        Jwt jwt = jwtWith("rm-456", "rm@rewabank.com");
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(
                new SimpleGrantedAuthority("ROLE_RELATIONSHIP_MANAGER"),
                new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/customers").build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
        ).verifyComplete();

        String roles = captor.getValue().getRequest().getHeaders().getFirst("X-User-Role");
        assertThat(roles).contains("RELATIONSHIP_MANAGER").contains("BRANCH_MANAGER");
    }

    @Test
    void filter_jwtAuthenticated_withCorrelationId_forwardsIt() {
        // Kills NegateConditionals on: correlationId != null ? correlationId : ""
        Jwt jwt = jwtWith("user-789", "u@rewabank.com");
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test")
                        .header(FilterUtility.CORRELATION_ID, "corr-abc-999")
                        .build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
        ).verifyComplete();

        assertThat(captor.getValue().getRequest().getHeaders()
                .getFirst(FilterUtility.CORRELATION_ID)).isEqualTo("corr-abc-999");
    }

    @Test
    void filter_jwtAuthenticated_noCorrelationId_setsEmptyHeader() {
        // Kills NegateConditionals on null correlationId guard
        Jwt jwt = jwtWith("user-789", "u@rewabank.com");
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build()); // no correlation header
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
        ).verifyComplete();

        // correlationId was null → should be "" not null
        String corrId = captor.getValue().getRequest().getHeaders()
                .getFirst(FilterUtility.CORRELATION_ID);
        assertThat(corrId).isNotNull().isEmpty();
    }

    @Test
    void filter_jwtAuthenticated_nullUserId_usesEmptyString() {
        // Kills NegateConditionals on: userId != null ? userId : ""
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(null);
        when(jwt.getClaimAsString("email")).thenReturn("u@rewabank.com");
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
        ).verifyComplete();

        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-User-Id")).isEmpty();
    }

    @Test
    void filter_jwtAuthenticated_nullEmail_usesEmptyString() {
        // Kills NegateConditionals on: email != null ? email : ""
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("user-123");
        when(jwt.getClaimAsString("email")).thenReturn(null);
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
        ).verifyComplete();

        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-User-Email")).isEmpty();
    }

    // ── Non-JWT authenticated path ─────────────────────────────────────────────

    @Test
    void filter_nonJwtAuthentication_passesExchangeUnmutated() {
        // Kills NullReturn on: return chain.filter(exchange) (non-JWT branch)
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "user", "pass", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
        ).verifyComplete();

        // Chain was called (not blocked)
        verify(chain).filter(any());
        // X-User-Id header was NOT set (no mutation to the exchange)
        assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id")).isNull();
    }

    // ── Empty security context (switchIfEmpty) ─────────────────────────────────

    @Test
    void filter_noSecurityContext_chainsWithOriginalExchange() {
        // Kills NullReturn on the switchIfEmpty path: chain.filter(exchange)
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        // No contextWrite → ReactiveSecurityContextHolder is empty
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
    }
}