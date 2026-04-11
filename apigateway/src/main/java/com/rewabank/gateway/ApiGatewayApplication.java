package com.rewabank.gateway; // ← CHANGED from com.gateway.apigateway

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;
import org.springframework.context.annotation.Primary;
import java.time.Duration;
import java.time.LocalDateTime;

@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

	// ── K8s DNS format: http://{service-name}.{namespace}.svc.cluster.local ──
	private static final String ACCOUNTS_URI      = "http://accounts-service.banking.svc.cluster.local:8081";
	private static final String AUTH_URI           = "http://auth-service.banking.svc.cluster.local:8073";
	private static final String CUSTOMERS_URI      = "http://customers-service.banking.svc.cluster.local:8080";
	private static final String TRANSACTIONS_URI   = "http://transactions-service.banking.svc.cluster.local:8082";
	private static final String LOANS_URI          = "http://loans-service.banking.svc.cluster.local:8090";
	private static final String REPAYMENT_URI      = "http://repayment-service.banking.svc.cluster.local:8091";
	private static final String CARDS_URI          = "http://cards-service.banking.svc.cluster.local:8092";
	private static final String PAYMENT_URI        = "http://payment-service.banking.svc.cluster.local:8093";
	private static final String FRAUD_URI          = "http://fraud-service.banking.svc.cluster.local:8094";
	private static final String NOTIFICATIONS_URI  = "http://notifications-service.banking.svc.cluster.local:9010";
	private static final String AUDIT_URI          = "http://audit-service.banking.svc.cluster.local:9011";
	private static final String STATEMENT_URI      = "http://statement-service.banking.svc.cluster.local:9012";
	private static final String REPORT_URI         = "http://report-service.banking.svc.cluster.local:9013";

	public static final String HEADER_RESPONSE_TIME = "X-Response-Time";

	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}

	@Bean
	public RouteLocator rewaBankRouteConfig(RouteLocatorBuilder builder) {
		return builder.routes()

				// ── Auth MS ──────────────────────────────────────────────────
				.route("auth-service", p -> p
						.path("/api/v1/auth/**")
						.filters(f -> f
								.circuitBreaker(c -> c.setName("authCircuitBreaker")
										.setFallbackUri("forward:/fallback/auth"))
								.addResponseHeader(HEADER_RESPONSE_TIME, LocalDateTime.now().toString()))
						.uri(AUTH_URI))

				// ── Customers MS ──────────────────────────────────────────────
				.route("customers-service", p -> p
						.path("/api/v1/customers/**")
						.filters(f -> f
								.circuitBreaker(c -> c.setName("customersCircuitBreaker")
										.setFallbackUri("forward:/fallback/customers"))
								.addResponseHeader(HEADER_RESPONSE_TIME, LocalDateTime.now().toString()))
						.uri(CUSTOMERS_URI))

                                // -- KYC (served by Customers MS)
                                .route("kyc-service", p -> p
                                                .path("/api/v1/kyc/**")
                                                .filters(f -> f
                                                                .circuitBreaker(c -> c.setName("customersCircuitBreaker")
                                                                                .setFallbackUri("forward:/fallback/customers"))
                                                                .addResponseHeader(HEADER_RESPONSE_TIME, LocalDateTime.now().toString()))
                                                .uri(CUSTOMERS_URI))

				// ── Accounts MS ───────────────────────────────────────────────
				.route("accounts-service", p -> p
						.path("/api/v1/accounts/**")
						.filters(f -> f
								.circuitBreaker(c -> c.setName("accountsCircuitBreaker")
										.setFallbackUri("forward:/fallback/accounts"))
								.addResponseHeader(HEADER_RESPONSE_TIME, LocalDateTime.now().toString()))
						.uri(ACCOUNTS_URI))

				// ── Transactions MS — retries=0, NEVER retry financial ops ────
				.route("transactions-service", p -> p
						.path("/api/v1/transactions/**")
						.filters(f -> f
								.circuitBreaker(c -> c.setName("transactionsCircuitBreaker")
										.setFallbackUri("forward:/fallback/transactions"))
								// ← CHANGED: retries=0 — no duplicate debits ever
								.requestRateLimiter(c -> c
										.setRateLimiter(transactionRateLimiter())
										.setKeyResolver(userKeyResolver()))
								.addResponseHeader(HEADER_RESPONSE_TIME, LocalDateTime.now().toString()))
						.uri(TRANSACTIONS_URI))

				// ── Loans MS — GET retries ok, write ops no retry ─────────────
				.route("loans-service", p -> p
						.path("/api/v1/loans/**")
						.filters(f -> f
								.circuitBreaker(c -> c.setName("loansCircuitBreaker")
										.setFallbackUri("forward:/fallback/loans"))
								.retry(r -> r.setRetries(2)          // ← CHANGED: 3→2, GET only
										.setMethods(HttpMethod.GET)
										.setBackoff(Duration.ofMillis(100), Duration.ofMillis(500), 2, true))
								.addResponseHeader(HEADER_RESPONSE_TIME, LocalDateTime.now().toString()))
						.uri(LOANS_URI))

				// ── Repayment MS ──────────────────────────────────────────────
				.route("repayment-service", p -> p
						.path("/api/v1/repayment/**")
						.filters(f -> f
								.circuitBreaker(c -> c.setName("repaymentCircuitBreaker")
										.setFallbackUri("forward:/fallback/repayment"))
								.addResponseHeader(HEADER_RESPONSE_TIME, LocalDateTime.now().toString()))
						.uri(REPAYMENT_URI))

				// ── Cards MS ──────────────────────────────────────────────────
				.route("cards-service", p -> p
						.path("/api/v1/cards/**")
						.filters(f -> f
								.circuitBreaker(c -> c.setName("cardsCircuitBreaker")
										.setFallbackUri("forward:/fallback/cards"))
								.requestRateLimiter(c -> c
										.setRateLimiter(redisRateLimiter())
										.setKeyResolver(userKeyResolver()))
								.addResponseHeader(HEADER_RESPONSE_TIME, LocalDateTime.now().toString()))
						.uri(CARDS_URI))

				// ── Payment Gateway MS — retries=0, NEVER retry payments ──────
				.route("payment-service", p -> p
						.path("/api/v1/payments/**")
						.filters(f -> f
								.circuitBreaker(c -> c.setName("paymentCircuitBreaker")
										.setFallbackUri("forward:/fallback/payments"))
								// ← retries=0 enforced — duplicate payment prevention
								.requestRateLimiter(c -> c
										.setRateLimiter(transactionRateLimiter())
										.setKeyResolver(userKeyResolver()))
								.addResponseHeader(HEADER_RESPONSE_TIME, LocalDateTime.now().toString()))
						.uri(PAYMENT_URI))

				// ── Fraud MS — admin/internal only ────────────────────────────
				.route("fraud-service", p -> p
						.path("/api/v1/fraud/**")
						.filters(f -> f
								.circuitBreaker(c -> c.setName("fraudCircuitBreaker")
										.setFallbackUri("forward:/fallback/fraud"))
								.addResponseHeader(HEADER_RESPONSE_TIME, LocalDateTime.now().toString()))
						.uri(FRAUD_URI))

				// ── Notifications MS ──────────────────────────────────────────
				.route("notifications-service", p -> p
						.path("/api/v1/notifications/**")
						.filters(f -> f
								.circuitBreaker(c -> c.setName("notificationsCircuitBreaker")
										.setFallbackUri("forward:/fallback/notifications"))
								.addResponseHeader(HEADER_RESPONSE_TIME, LocalDateTime.now().toString()))
						.uri(NOTIFICATIONS_URI))

				// ── Audit MS — write-only, no circuit breaker fallback ────────
				.route("audit-service", p -> p
						.path("/api/v1/audit/**")
						.filters(f -> f
								.addResponseHeader(HEADER_RESPONSE_TIME, LocalDateTime.now().toString()))
						.uri(AUDIT_URI))

				// ── Statement MS ──────────────────────────────────────────────
				.route("statement-service", p -> p
						.path("/api/v1/statements/**")
						.filters(f -> f
								.circuitBreaker(c -> c.setName("statementCircuitBreaker")
										.setFallbackUri("forward:/fallback/statements"))
								.addResponseHeader(HEADER_RESPONSE_TIME, LocalDateTime.now().toString()))
						.uri(STATEMENT_URI))

				// ── Report MS ─────────────────────────────────────────────────
				.route("report-service", p -> p
						.path("/api/v1/reports/**")
						.filters(f -> f
								.circuitBreaker(c -> c.setName("reportCircuitBreaker")
										.setFallbackUri("forward:/fallback/reports"))
								.addResponseHeader(HEADER_RESPONSE_TIME, LocalDateTime.now().toString()))
						.uri(REPORT_URI))

				.build();
	}

	// ── Circuit breaker: default (4s timeout) ─────────────────────────────────
	@Bean
	public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
		return factory -> factory.configureDefault(id ->
				new Resilience4JConfigBuilder(id)
						.circuitBreakerConfig(CircuitBreakerConfig.custom()
								.slidingWindowSize(10)
								.failureRateThreshold(50)
								.waitDurationInOpenState(Duration.ofSeconds(10))
								.permittedNumberOfCallsInHalfOpenState(2)
								.build())
						.timeLimiterConfig(TimeLimiterConfig.custom()
								.timeoutDuration(Duration.ofSeconds(4))
								.build())
						.build());
	}

	// ── Circuit breaker: fraud — 3s timeout, fail fast ── ← NEW
	@Bean
	public Customizer<ReactiveResilience4JCircuitBreakerFactory> fraudCustomizer() {
		return factory -> factory.configure(builder ->
						builder.circuitBreakerConfig(CircuitBreakerConfig.custom()
										.slidingWindowSize(5)
										.failureRateThreshold(30)   // open faster for fraud
										.waitDurationInOpenState(Duration.ofSeconds(5))
										.build())
								.timeLimiterConfig(TimeLimiterConfig.custom()
										.timeoutDuration(Duration.ofSeconds(3)) // ← 3s max for fraud
										.build()),
				"fraudCircuitBreaker");
	}

	// ── Rate limiter: general (20 req/s) ──────────────────────────────────────
	@Bean
	@Primary
	public RedisRateLimiter redisRateLimiter() {
		return new RedisRateLimiter(20, 40, 1); // ← CHANGED: 1,1,1 → 20,40 (replenish, burst)
	}

	// ── Rate limiter: transactions/payments (5 req/s) ─── ← NEW (stricter)
	@Bean
	public RedisRateLimiter transactionRateLimiter() {
		return new RedisRateLimiter(5, 10, 1);
	}

	// ── Key resolver: per user (from X-User-Id header set by AuthenticationFilter)
	@Bean
	public KeyResolver userKeyResolver() {
		return exchange -> Mono
				.justOrEmpty(exchange.getRequest().getHeaders().getFirst("X-User-Id"))
				.defaultIfEmpty("anonymous");
	}
}
