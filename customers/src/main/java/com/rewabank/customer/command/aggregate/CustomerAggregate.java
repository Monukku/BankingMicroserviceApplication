package com.rewabank.customer.command.aggregate;

import com.rewabank.customer.Entity.Customer;
import com.rewabank.customer.command.CreateCustomerCommand;
import com.rewabank.customer.command.DeleteCustomerCommand;
import com.rewabank.customer.command.UpdateCustomerCommand;
import com.rewabank.customer.command.event.CustomerCreatedEvent;
import com.rewabank.customer.command.event.CustomerDeletedEvent;
import com.rewabank.customer.command.event.CustomerUpdatedEvent;
import com.rewabank.customer.exception.CustomerAlreadyExistsException;
import com.rewabank.customer.repository.CustomerRepository;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;
import org.springframework.beans.BeanUtils;
import java.util.Optional;

//in this class we're handling  the commands triggered from command controller using command gateway

@Aggregate
public class CustomerAggregate {
    @AggregateIdentifier
    private String customerId;
    private String name;
    private String mobileNumber;
    private String email;
    private boolean activeSw;

    public CustomerAggregate() {
    }

    @CommandHandler
    public CustomerAggregate(CreateCustomerCommand createCustomerCommand, CustomerRepository customerRepository) {
        Optional<Customer> customer = customerRepository.findByMobileNumberAndActiveSw(createCustomerCommand.getMobileNumber(), true);
        if (customer.isPresent()) {
            throw new CustomerAlreadyExistsException("Customer already exists with the given mobile number: " + createCustomerCommand.getMobileNumber());
        }
        CustomerCreatedEvent customerCreatedEvent = new CustomerCreatedEvent();
        BeanUtils.copyProperties(createCustomerCommand, customerCreatedEvent);

        AggregateLifecycle.apply(customerCreatedEvent);

    }

    @EventSourcingHandler
    public void on(CustomerCreatedEvent customerCreatedEvent) {
        this.customerId = customerCreatedEvent.getCustomerId();
        this.name = customerCreatedEvent.getName();
        this.mobileNumber = customerCreatedEvent.getMobileNumber();
        this.email = customerCreatedEvent.getEmail();
        this.activeSw = customerCreatedEvent.isActiveSw();
    }

    @CommandHandler
    public void handle(UpdateCustomerCommand updateCustomerCommand) {
        CustomerUpdatedEvent customerUpdatedEvent = new CustomerUpdatedEvent();
        BeanUtils.copyProperties(updateCustomerCommand, customerUpdatedEvent);

        AggregateLifecycle.apply(customerUpdatedEvent);
    }

    @EventSourcingHandler
    public void on(CustomerUpdatedEvent customerUpdatedEvent) {
        this.name = customerUpdatedEvent.getName();
        this.email = customerUpdatedEvent.getEmail();
    }

    @CommandHandler
    public void handle(DeleteCustomerCommand deleteCustomerCommand) {
        CustomerDeletedEvent customerDeletedEvent = new CustomerDeletedEvent();
        BeanUtils.copyProperties(deleteCustomerCommand, customerDeletedEvent);
        AggregateLifecycle.apply(customerDeletedEvent);
    }

    @EventSourcingHandler
    public void on(CustomerDeletedEvent customerDeletedEvent) {
        this.activeSw = customerDeletedEvent.isActiveSw();

    }
}