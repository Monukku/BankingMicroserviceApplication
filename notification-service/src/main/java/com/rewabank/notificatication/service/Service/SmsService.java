package com.rewabank.notificatication.service.Service;
//
//import com.notificatication.service.dto.AccountsMessageDto;
//import com.twilio.Twilio;
//import com.twilio.rest.api.v2010.account.Message;
//import com.twilio.type.PhoneNumber;
//import jakarta.annotation.PostConstruct;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//
//@Service
//public class SmsService {
//
//
//    private String accountSid;
//    private String authToken;
//    private String fromNumber;
//
//    @PostConstruct
//    public void init() {
//        Twilio.init(accountSid, authToken);
//    }
//
//    public void sendSMS(AccountsMessageDto accountsMessageDto) {
//        Message.creator(
//                new PhoneNumber(accountsMessageDto.mobileNumber()),
//                new PhoneNumber(fromNumber),
//                "Dear " + accountsMessageDto.name() + ", your account ("
//                        + accountsMessageDto.accountNumber() + ") has been created/updated successfully."
//        ).create();
//    }
//}

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.from-number}")
    private String fromNumber;

    public void sendSms(String to, String messageBody) {
        Twilio.init(accountSid, authToken);
        Message.creator(
                new PhoneNumber(to),
                new PhoneNumber(fromNumber),
                messageBody
        ).create();
    }
}
