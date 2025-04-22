package com.RewaBank.accounts.services.serviceImpl;

import com.RewaBank.accounts.dto.*;
import com.RewaBank.accounts.entity.Accounts;
import com.RewaBank.accounts.entity.Customer;
import com.RewaBank.accounts.exception.ResourceNotFoundException;
import com.RewaBank.accounts.mapper.AccountsMapper;
import com.RewaBank.accounts.mapper.CustomerMapper;
import com.RewaBank.accounts.repository.AccountsRepository;
import com.RewaBank.accounts.repository.CustomerRepository;
import com.RewaBank.accounts.services.ICustomersService;
import com.RewaBank.accounts.services.client.CardsFeignClient;
import com.RewaBank.accounts.services.client.LoansFeignClient;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class CustomersServiceImpl implements ICustomersService {

    private AccountsRepository accountsRepository;
    private CustomerRepository customerRepository;
    private CardsFeignClient cardsFeignClient;
    private LoansFeignClient loansFeignClient;

    @Override
    public Customer createCustomer(Customer customer) {
        return customerRepository.save(customer);
    }

    @Override
    public CustomerDetailsDto fetchCustomerDetails(String correlationId, String mobileNumber) {
//         customerRepository.findByMobileNumber(mobileNumber);
        Customer customer= customerRepository.findByMobileNumber(mobileNumber).orElseThrow(
                () -> new ResourceNotFoundException("Customer","mobileNmber",mobileNumber)
        );

        Accounts accounts= accountsRepository.findById(customer.getId()).orElseThrow(
                () -> new ResourceNotFoundException("Accounts","mobileNumber",customer.getId().toString())
        );

//        CustomerDetailsDto customerDetailsDto= CustomerMapper.mapToCustomerDetailsDto(customer,new CustomerDetailsDto());
        CustomerDetailsDto customerDetailsDto= CustomerMapper.mapToCustomerDetailsDto(customer);

        customerDetailsDto.setAccountsDto(AccountsMapper.mapToAccountsDto(accounts));

        ResponseEntity<LoansDto> loansDtoResponseEntity= loansFeignClient.fetchLoansDetails(correlationId, mobileNumber);
        if(null!=loansDtoResponseEntity){
            customerDetailsDto.setLoansDto(loansDtoResponseEntity.getBody());
        }

        ResponseEntity<CardsDto> cardsDtoResponseEntity= cardsFeignClient.fetchCardDetails(correlationId, mobileNumber);
        if (null!=cardsDtoResponseEntity){
            customerDetailsDto.setCardsDto(cardsDtoResponseEntity.getBody());
        }

        return customerDetailsDto;
    }
    @Override
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }
    @Override
    public Optional<Customer> getCustomerById(Long id) {
        return customerRepository.findById(id);
    }
    @Override
    public Customer updateCustomer(Long id, Customer customerDetails) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer","mobileNumber",id.toString()));

        customer.setName(customerDetails.getName());
        customer.setEmail(customerDetails.getEmail());
        customer.setMobileNumber(customerDetails.getMobileNumber());
        // Update other fields as needed

        return customerRepository.save(customer);
    }

    @Override
    public void deleteCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer","mobileNumber",id.toString()));

        customerRepository.delete(customer);
    }
}
