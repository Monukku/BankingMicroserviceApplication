package com.rewabank.customer.services.serviceImpl;

import com.rewabank.customer.DTO.CustomerDto;
import com.rewabank.customer.Entity.Customer;
import com.rewabank.customer.command.event.CustomerUpdatedEvent;
import com.rewabank.customer.constants.CustomerConstants;
import com.rewabank.customer.exception.CustomerAlreadyExistsException;
import com.rewabank.customer.exception.ResourceNotFoundException;
import com.rewabank.customer.mapper.CustomerMapper;
import com.rewabank.customer.repository.CustomerRepository;
import com.rewabank.customer.services.ICustomerService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import static com.rewabank.customer.mapper.CustomerMapper.mapEventToCustomer;

@Service
@AllArgsConstructor
public class CustomerServiceImpl implements ICustomerService {

    private CustomerRepository customerRepository;

    @Override
    public void createCustomer(Customer customer) {
        Optional<Customer> customerOptional = customerRepository.findByMobileNumberAndActiveSw(customer.getMobileNumber(), CustomerConstants.ACTIVE_SW);
        if (customerOptional.isPresent()) {
            throw new CustomerAlreadyExistsException("Customer already exists with the given mobile number: " + customer.getMobileNumber());
        }
         customerRepository.save(customer);
    }

    @Override
    public CustomerDto fetchCustomerDetails(String correlationId, String mobileNumber) {
        Customer customer= customerRepository.findByMobileNumberAndActiveSw (mobileNumber, CustomerConstants.ACTIVE_SW).orElseThrow(
                () -> new ResourceNotFoundException("Customer","mobileNmber",mobileNumber)
        );

        return CustomerMapper.mapToCustomerDto(customer, new CustomerDto());
    }
    @Override
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    @Override
    public Optional<Customer> getCustomerById(String id) {
        return customerRepository.findById(id);
    }

    @Override
    public boolean updateCustomer(CustomerUpdatedEvent customerUpdatedEvent) {
        Customer customer = customerRepository.findByMobileNumberAndActiveSw(customerUpdatedEvent.getMobileNumber(), CustomerConstants.ACTIVE_SW).orElseThrow(
                () -> new ResourceNotFoundException("Customer","mobileNmber",customerUpdatedEvent.getMobileNumber()));

           Customer updatedCustomer = mapEventToCustomer(customerUpdatedEvent, customer);

        customerRepository.save(customer);
        return true;
    }

    @Override
    public boolean deleteCustomer(String customerid) {
        Customer customer = customerRepository.findById(customerid)
                .orElseThrow(() -> new ResourceNotFoundException("Customer","mobileNumber",customerid));
      customer.setActiveSw(CustomerConstants.IN_ACTIVE_SW);
      customerRepository.save(customer);
      return true;
    }
}
