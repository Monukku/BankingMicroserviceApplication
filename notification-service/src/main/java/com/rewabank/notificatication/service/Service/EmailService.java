package com.rewabank.notificatication.service.Service;
//
//import com.notificatication.service.dto.AccountsMessageDto;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.mail.SimpleMailMessage;
//import org.springframework.mail.javamail.JavaMailSender;
//import org.springframework.stereotype.Service;
//
//@Service
//public class EmailService {
//
//    @Autowired
//    private JavaMailSender javaMailSender;
//
//    public void sendEmail(AccountsMessageDto accountsMessageDto) {
//        SimpleMailMessage mailMessage = new SimpleMailMessage();
//        mailMessage.setTo(accountsMessageDto.email());
//        mailMessage.setSubject("Account Notification");
//        mailMessage.setText("Dear " + accountsMessageDto.name() + ",\n\nYour account ("
//                + accountsMessageDto.accountNumber() + ") has been created/updated successfully.\n\nThank you!");
//
//        javaMailSender.send(mailMessage);
//    }
//}


import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
