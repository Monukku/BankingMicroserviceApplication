package com.rewabank.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/realms/test/protocol/openid-connect/certs",
    "CORS_ORIGIN_WEB=http://localhost:4200",
    "CORS_ORIGIN_MOBILE=http://localhost:3000",
    "build.version=1.0.0"
})
class ApigatewayApplicationTests {

    @MockitoBean ReactiveJwtDecoder reactiveJwtDecoder;

    @Test
    void contextLoads() {
    }
}
