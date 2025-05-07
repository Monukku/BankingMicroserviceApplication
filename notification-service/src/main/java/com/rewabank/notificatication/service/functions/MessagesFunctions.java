//package com.notificatication.service.functions;
//
//import com.notificatication.service.Service.EmailService;
//import com.notificatication.service.Service.SmsService;
//import com.notificatication.service.dto.AccountsMessageDto;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.beans.factory.annotation.Autowired;
//import java.util.function.Function;
//
//@Configuration
//public class MessagesFunctions {
//
//    private static final Logger log = LoggerFactory.getLogger(MessagesFunctions.class);
//
//    @Autowired
//    private EmailService emailService;
//
//    @Autowired
//    private SmsService smsService;
//
//    @Bean
//    public Function<AccountsMessageDto, AccountsMessageDto> email() {
//        return accountsMessageDto -> {
//            try {
//                emailService.sendEmail(accountsMessageDto);
//                log.info("Email sent successfully to {}", accountsMessageDto.email());
//            } catch (Exception e) {
//                log.error("Failed to send email to {}: {}", accountsMessageDto.email(), e.getMessage());
//                // Add retry logic if necessary
//            }
//            return accountsMessageDto;
//        };
//    }
//
//    @Bean
//    public Function<AccountsMessageDto, Long> sms() {
//        return accountsMessageDto -> {
//            log.info("Sending SMS to: {}", accountsMessageDto.mobileNumber());
//            try {
//                smsService.sendSMS(accountsMessageDto);
//                log.info("SMS sent successfully to {}", accountsMessageDto.mobileNumber());
//            } catch (Exception e) {
//                log.error("Failed to send SMS to {}: {}", accountsMessageDto.mobileNumber(), e.getMessage());
//                // Add retry logic if necessary
//            }
//            return accountsMessageDto.accountNumber();
//        };
//    }
//}
//
