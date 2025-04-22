package com.gateway.apigateway.config;

import jakarta.ws.rs.PATCH;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

//    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
//    private String jwkSetUri;
//    private static final String ALLOWED_ORIGIN = "http://localhost:4200"; // Allow specific origin
//    private static final String ALLOWED_ORIGIN = "*"; // Allow all origins


//    @Bean
//    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity serverHttpSecurity) {
//        serverHttpSecurity
//                .authorizeExchange(exchanges -> exchanges
//                        .pathMatchers(HttpMethod.GET).permitAll()
//                        .pathMatchers("/rewabank/accounts/**").hasRole("ACCOUNTS")
//                        .pathMatchers("/rewabank/loans/**").hasRole("LOANS")
//                        .pathMatchers("/rewabank/cards/**").hasRole("CARDS")
//                        .pathMatchers("/rewabank/customers/**").hasRole("CUSTOMERS"))
//                .oauth2ResourceServer(oAuth2ResourceServerSpec -> oAuth2ResourceServerSpec
//                        .jwt(jwtSpec -> jwtSpec.jwtAuthenticationConverter(grantedAuthoritiesExtraction())));
//
//        serverHttpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
//        serverHttpSecurity.cors(corsSpec -> corsSpec.configurationSource(corsConfigurationSource()));
//
//        return serverHttpSecurity.build();
//    }
//
//    @Bean
//    public ReactiveJwtDecoder reactiveJwtDecoder() {
//        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
//                .webClient(WebClient.create())
//                .build();
//    }
//
//    private Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthoritiesExtraction() {
//        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
//        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new KeyCloakRoleConverter());
//        return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter);
//    }
//
//    @Bean
//    public CorsWebFilter corsWebFilter() {
//        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//        CorsConfiguration config = new CorsConfiguration();
//        config.setAllowCredentials(true);
//        config.addAllowedOrigin(ALLOWED_ORIGIN); // Allow specific origin
//        config.addAllowedHeader("*"); // Allow all headers
//        config.addAllowedMethod("*"); // Allow all methods
//        source.registerCorsConfiguration("/**", config);
//        return new CorsWebFilter(source);
//    }
//
//    private UrlBasedCorsConfigurationSource corsConfigurationSource() {
//        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//        CorsConfiguration config = new CorsConfiguration();
//        config.setAllowCredentials(true);
//        config.addAllowedOrigin(ALLOWED_ORIGIN); // Allow specific origin
//        config.addAllowedHeader("*"); // Allow all headers
//        config.addAllowedMethod("*"); // Allow all methods
//        source.registerCorsConfiguration("/**", config);
//        return source;
//    }

@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
private String jwkSetUri;

    private static final List<String> ALLOWED_ORIGINS = List.of("*"); // Allow all origins


    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.GET).permitAll()
                        .pathMatchers("/rewabank/accounts/**").hasRole("ACCOUNTS")
                        .pathMatchers("/rewabank/loans/**").hasRole("LOANS")
                        .pathMatchers("/rewabank/cards/**").hasRole("CARDS")
                        .pathMatchers("/rewabank/customers/**").hasRole("CUSTOMERS")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(grantedAuthoritiesExtraction())))
                .csrf(ServerHttpSecurity.CsrfSpec::disable);
        return http.build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
                .webClient(WebClient.create())
                .build();
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthoritiesExtraction() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new KeyCloakRoleConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter);
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(ALLOWED_ORIGINS);  // Use a list instead of single string
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT"," PATCH", "DELETE", "OPTIONS")); // Define explicitly
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }


}
