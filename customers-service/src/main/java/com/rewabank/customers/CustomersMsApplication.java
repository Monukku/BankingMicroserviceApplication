package com.rewabank.customers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CustomersMsApplication {
	public static void main(String[] args) {
		SpringApplication.run(CustomersMsApplication.class, args);
	}
}
