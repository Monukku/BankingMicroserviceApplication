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


package com.rewabank.customer.mapper;


import com.rewabank.customer.DTO.CustomerDetailsDto;
import com.rewabank.customer.DTO.CustomerDto;
import com.rewabank.customer.Entity.Customer;
import com.rewabank.customer.command.event.CustomerUpdatedEvent;

public class CustomerMapper {
    public static CustomerDto mapToCustomerDto(Customer customer, CustomerDto customerDto) {
        customerDto.setCustomerId(customer.getCustomerId());
        customerDto.setName(customer.getName());
        customerDto.setEmail(customer.getEmail());
        customerDto.setMobileNumber(customer.getMobileNumber());
        customerDto.setActiveSw(customer.isActiveSw());
        return customerDto;
    }

    public static Customer mapToCustomer(CustomerDto customerDto, Customer customer) {
        customer.setCustomerId(customerDto.getCustomerId());
        customer.setName(customerDto.getName());
        customer.setEmail(customerDto.getEmail());
        customer.setMobileNumber(customerDto.getMobileNumber());
        if(customerDto.isActiveSw()) {
            customer.setActiveSw(customerDto.isActiveSw());
        }
        return customer;
    }

    public static Customer mapEventToCustomer(CustomerUpdatedEvent customerUpdatedEvent, Customer customer) {
        customer.setName(customerUpdatedEvent.getName());
        customer.setEmail(customerUpdatedEvent.getEmail());
        return customer;

    }
}

