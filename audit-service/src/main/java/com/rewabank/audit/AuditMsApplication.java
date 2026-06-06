package com.rewabank.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class AuditMsApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuditMsApplication.class, args);
    }
}