package com.rewabank.customer.command.event;

import lombok.Data;

@Data
public class CustomerUpdatedEvent {
    private String customerId;
    private String name;
    private String mobileNumber;
    private String email;
    private  boolean activeSw;
}
