package com.rewabank.Transaction.Service.listener;



//import com.example.transactions.event.AccountCreatedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class AccountEventListener {

    @KafkaListener(topics = "account-topic", groupId = "transaction-group")
    public void handleAccountCreatedEvent(AccountCreatedEvent event) {
        System.out.println("Received AccountCreatedEvent: " + event.getAccountId());
        // Handle event and process transaction-related logic
    }
}
