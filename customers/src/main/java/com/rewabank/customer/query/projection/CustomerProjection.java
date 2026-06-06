package com.rewabank.customer.query.projection;

import com.rewabank.customer.Entity.Customer;
import com.rewabank.customer.command.event.CustomerCreatedEvent;
import com.rewabank.customer.command.event.CustomerDeletedEvent;
import com.rewabank.customer.command.event.CustomerUpdatedEvent;
import com.rewabank.customer.services.ICustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ProcessingGroup("customer-group")
public class CustomerProjection {

    private final ICustomerService iCustomerService;

    @EventHandler
    public void on(CustomerCreatedEvent customerCreatedEvent) {
        Customer customerEntity = new Customer();
        BeanUtils.copyProperties(customerCreatedEvent, customerEntity);
        log.info("iCustomerService.createCustomer call from Customer Projection EventHandler started...");
        iCustomerService.createCustomer(customerEntity);
        log.info("iCustomerService.createCustomer call from Customer Projection EventHandler completed....");
    }
    @EventHandler
    public void on(CustomerUpdatedEvent customerUpdatedEvent) {
        iCustomerService.updateCustomer(customerUpdatedEvent);
    }

    @EventHandler
    public void on(CustomerDeletedEvent customerDeletedEvent) {
        iCustomerService.deleteCustomer(customerDeletedEvent.getCustomerId());

    }
}
