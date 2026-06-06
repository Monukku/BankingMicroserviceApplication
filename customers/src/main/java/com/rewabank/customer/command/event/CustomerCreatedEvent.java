package com.rewabank.customer.command.event;

import com.rewabank.customer.Utility.Role;
import lombok.Data;

/**
 * NOUN+VERB(PastTense)+Event
 */

@Data
public class CustomerCreatedEvent {

    private String customerId;
    private String name;
    private String mobileNumber;
    private Role role;
    private String email;
    private  boolean activeSw;
}
