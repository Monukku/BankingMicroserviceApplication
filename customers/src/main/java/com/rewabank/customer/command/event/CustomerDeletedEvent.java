package com.rewabank.customer.command.event;

import lombok.Data;

@Data
public class CustomerDeletedEvent {
    private String customerId;
    private boolean activeSw;
}
