package com.rewabank.customer.command;

import lombok.Builder;
import lombok.Data;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * VERB+ NOUN+Command
 */

@Data
@Builder
public class UpdateCustomerCommand {

    @TargetAggregateIdentifier
    private final String customerId;

    private final String name;
    private final String mobileNumber;
    private final String email;
    private final  boolean activeSw;
}
