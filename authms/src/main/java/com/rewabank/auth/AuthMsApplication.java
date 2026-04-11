package com.rewabank.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AuthMsApplication {
	public static void main(String[] args) {
		SpringApplication.run(AuthMsApplication.class, args);
	}
}
