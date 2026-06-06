package com.rewabank.customer.services;


import com.rewabank.customer.DTO.CustomerDto;
import com.rewabank.customer.Entity.Customer;

import com.rewabank.customer.command.event.CustomerUpdatedEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ICustomerService {

    public void createCustomer(Customer customer );

    public Optional<Customer> getCustomerById(String id);

    public Page<CustomerDto> getAllCustomers(String role, Pageable pageable);

    public boolean updateCustomer(CustomerUpdatedEvent customerUpdatedEvent);

    public CustomerDto fetchCustomerDetails(String mobileNubmer);

    public boolean deleteCustomer(String customerid);

}
