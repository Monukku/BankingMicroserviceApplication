package com.rewabank.customer.services;


import com.rewabank.customer.DTO.CustomerDetailsDto;
import com.rewabank.customer.DTO.CustomerDto;
import com.rewabank.customer.Entity.Customer;
import com.rewabank.customer.command.event.CustomerDeletedEvent;
import com.rewabank.customer.command.event.CustomerUpdatedEvent;

import java.util.List;
import java.util.Optional;

public interface ICustomerService {

    public void createCustomer(Customer customer );

    public Optional<Customer> getCustomerById(String id);

    public List<Customer> getAllCustomers();

    public boolean updateCustomer(CustomerUpdatedEvent customerUpdatedEvent);

    public CustomerDto fetchCustomerDetails(String correlationId, String mobileNubmer);

    public boolean deleteCustomer(String customerid);

}
