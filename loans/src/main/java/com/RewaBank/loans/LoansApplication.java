package com.RewaBank.loans;

import com.RewaBank.loans.dto.LoansContactInfoDto;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditAwareImpl")
@EnableConfigurationProperties(value = {LoansContactInfoDto.class})
@OpenAPIDefinition(
		info = @Info(
				title = "Loans microservice REST API Documentation",
				description = "RewaBank Loans microservice REST API Documentation",
				version = "v1",
				contact = @Contact(
						name = "Monu Kumar",
						email = "tutor@rewabank.com",
						url = "https://www.rewabank.com"
				),
				license = @License(
						name = "Apache 2.0",
						url = "https://www.rewabank.com"
				)
		),
		externalDocs = @ExternalDocumentation(
				description = "rewabank Loans microservice REST API Documentation",
				url = "https://www.rewabank.com/swagger-ui.html"
		)
)
public class LoansApplication {

	public static void main(String[] args) {
		SpringApplication.run(LoansApplication.class, args);
	}

}
