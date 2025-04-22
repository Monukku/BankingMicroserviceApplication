//package com.RewaBank.accounts.mapper;
//
//import com.RewaBank.accounts.dto.CustomerDetailsDto;
//import com.RewaBank.accounts.dto.CustomerDto;
//import com.RewaBank.accounts.entity.Customer;
//
//public class CustomerMapper {
//
//    public static CustomerDto mapToCustomerDto(Customer customer,CustomerDto customerDto) {
//        if (customer == null) return null;
//
//        CustomerDto dto = new CustomerDto();
//        dto.setName(customer.getName());
//        dto.setEmail(customer.getEmail());
//        dto.setMobileNumber(customer.getMobileNumber());
//        if (!customer.getAccounts().isEmpty()) {
//            dto.setAccountsDto(AccountsMapper.mapToAccountsDto(customer.getAccounts().iterator().next()));
//        }
//        return dto;
//    }
//
//        public static CustomerDetailsDto mapToCustomerDetailsDto(Customer customer, CustomerDetailsDto customerDetailsDto){
//
//        customerDetailsDto.setName(customer.getName());
//        customerDetailsDto.setEmail(customer.getEmail());
//        customerDetailsDto.setMobileNumber(customer.getMobileNumber());
//
//        return  customerDetailsDto;
//    }
//
//    public static Customer mapToCustomer(CustomerDto customerDto,Customer customer) {
//        if (customerDto == null) return null;
//
//        customer.setName(customerDto.getName());
//        customer.setEmail(customerDto.getEmail());
//        customer.setMobileNumber(customerDto.getMobileNumber());
//        if (customerDto.getAccountsDto() != null) {
//            customer.addAccount(AccountsMapper.mapToAccounts(customerDto.getAccountsDto()));
//        }
//        return customer;
//    }
//}


package com.RewaBank.accounts.mapper;

import com.RewaBank.accounts.dto.CustomerDetailsDto;
import com.RewaBank.accounts.dto.CustomerDto;
import com.RewaBank.accounts.entity.Customer;

public class CustomerMapper {

    // Mapping Customer entity to CustomerDto
    public static CustomerDto mapToCustomerDto(Customer customer) {
        if (customer == null) return null;

        CustomerDto dto = new CustomerDto();
        dto.setName(customer.getName());
        dto.setEmail(customer.getEmail());
        dto.setMobileNumber(customer.getMobileNumber());

        // Map the first account (if any) associated with the customer
        if (!customer.getAccounts().isEmpty()) {
            dto.setAccountsDto(AccountsMapper.mapToAccountsDto(customer.getAccounts().iterator().next()));
        }
        return dto;
    }

    // Mapping Customer entity to CustomerDetailsDto (for other use cases, if needed)
    public static CustomerDetailsDto mapToCustomerDetailsDto(Customer customer) {
        if (customer == null) return null;

        CustomerDetailsDto dto = new CustomerDetailsDto();
        dto.setName(customer.getName());
        dto.setEmail(customer.getEmail());
        dto.setMobileNumber(customer.getMobileNumber());

        return dto;
    }

    // Mapping CustomerDto to existing Customer entity (for updates)
    public static Customer mapToCustomer(CustomerDto customerDto, Customer customer) {
        if (customerDto == null || customer == null) return null;

        // Update only mutable fields
        customer.setName(customerDto.getName());
        customer.setEmail(customerDto.getEmail());
        customer.setMobileNumber(customerDto.getMobileNumber());

        // Update associated account, if present in DTO
        if (customerDto.getAccountsDto() != null) {
            // Ensure existing accounts are updated rather than creating new ones (if required)
            customer.addAccount(AccountsMapper.mapToAccounts(customerDto.getAccountsDto(), customer.getAccounts().iterator().next()));
        }

        return customer;
    }
}

