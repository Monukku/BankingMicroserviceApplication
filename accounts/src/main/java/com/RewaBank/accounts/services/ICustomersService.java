package com.RewaBank.accounts.services;

import com.RewaBank.accounts.dto.CustomerDetailsDto;
import com.RewaBank.accounts.entity.Customer;
import java.util.List;
import java.util.Optional;

public interface ICustomersService {

    public Customer createCustomer(Customer customer);

    public Optional<Customer> getCustomerById(Long id);

    public List<Customer> getAllCustomers();

    public Customer updateCustomer(Long id, Customer customerDetails);

    public CustomerDetailsDto fetchCustomerDetails(String correlationId, String mobileNubmer);

    public void deleteCustomer(Long id);

}
