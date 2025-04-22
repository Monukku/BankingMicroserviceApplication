package com.rewabank.customer.command.event;

import lombok.Data;

/**
 * NOUN+VERB(PastTense)+Event
 */

@Data
public class CustomerCreatedEvent {
    private String customerId;
    private String name;
    private String mobileNumber;
    private String email;
    private  boolean activeSw;
}
