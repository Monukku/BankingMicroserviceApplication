package com.gateway.apigateway;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.time.LocalDateTime;

@SpringBootApplication
public class ApigatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApigatewayApplication.class, args);
	}
    @Bean
	public RouteLocator rewaBankRouteConfig(RouteLocatorBuilder routeLocatorBuilder){

		 return routeLocatorBuilder.routes()
				     .route( p-> p
						 .path("/rewabank/accounts/**")
						 .filters(f-> f.rewritePath("/rewabank/accounts/(?<segment>.*)","/${segment}")
								 .circuitBreaker(config-> config.setName("accountsCircuitBreaker")
										 .setFallbackUri("forward:/contactsupport"))
								 .addResponseHeader("X-Response-Time", LocalDateTime.now().toString()))
						 .uri("lb://ACCOUNTS"))

				 .route( p-> p
						 .path("/rewabank/customers/**")
						 .filters(f-> f.rewritePath("/rewabank/customers/(?<segment>.*)","/${segment}")
								 .circuitBreaker(config-> config.setName("customersCircuitBreaker")
										 .setFallbackUri("forward:/contactsupport"))
								 .addResponseHeader("X-Response-Time", LocalDateTime.now().toString()))
						 .uri("lb://CUSTOMERS"))

				 .route( p-> p
						 .path("/rewabank/loans/**")
						 .filters(f-> f.rewritePath("/rewabank/loans/(?<segment>.*)","/${segment}")
								 .addResponseHeader("X-Response-Time", LocalDateTime.now().toString())
								 .retry(retryConfig -> retryConfig.setRetries(3)
										 .setMethods(HttpMethod.GET)
										 .setBackoff(Duration.ofMillis(100),Duration.ofMillis(1000),2,true)))
						 .uri("lb://LOANS"))

				 .route( p-> p
						 .path("/rewabank/transactions/**")
						 .filters(f-> f.rewritePath("/rewabank/transactions/(?<segment>.*)","/${segment}")
								 .addResponseHeader("X-Response-Time", LocalDateTime.now().toString())
								 .requestRateLimiter(config -> config.setRateLimiter(redisRateLimiter()).setKeyResolver(userKeyResolver())))
						 .uri("lb://TRANSACTIONS"))

				 .route( p-> p
						 .path("/rewabank/cards/**")
						 .filters(f-> f.rewritePath("/rewabank/cards/(?<segment>.*)","/${segment}")
								 .addResponseHeader("X-Response-Time", LocalDateTime.now().toString())
								 .requestRateLimiter(config -> config.setRateLimiter(redisRateLimiter()).setKeyResolver(userKeyResolver())))
						 .uri("lb://CARDS")).build();

	}
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> {
            factory.configureDefault(id-> new Resilience4JConfigBuilder(id)
                    .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
                    .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(4)).build()).build());
        };
    }

	@Bean
	KeyResolver userKeyResolver(){
		return exchange -> Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst("user"))
				.defaultIfEmpty("anonymous");
	}

	@Bean
	RedisRateLimiter redisRateLimiter(){
		return new RedisRateLimiter(1,1,1);
	}

}

