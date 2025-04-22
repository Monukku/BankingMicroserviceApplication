package com.RewaBank.accounts;

import com.RewaBank.accounts.dto.AccountsContactInfoDto;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
@EnableFeignClients
@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditAwareImpl")
@OpenAPIDefinition(
		info = @Info(
				title = "Accounts Microservice REST API documentations",
				description = "RewaBank Accounts Microservice REST API documentations ",
				version = "v1",
				contact = @Contact(
						name = "Monu Kumar",
						email = "monu@gmail.com",
						url = "http://www.rewabank.com"
				),
				license = @License(
						name = "Apache 2.0",
						url = "http://www.rewabank.com"
				)
		),
		externalDocs = @ExternalDocumentation(
				description = "RewaBank Accounts Microservice REST API documentations",
						url ="http://www.rewabank.com/swagger-ui/html"
		)
)
@EnableConfigurationProperties(value = {AccountsContactInfoDto.class})
public class AccountsApplication {

	public static void main(String[] args) {
		SpringApplication.run(AccountsApplication.class, args);
	}

}
