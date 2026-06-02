package com.rewabank.fraud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FraudMsApplication {
	public static void main(String[] args) {
		SpringApplication.run(FraudMsApplication.class, args);
	}
}
