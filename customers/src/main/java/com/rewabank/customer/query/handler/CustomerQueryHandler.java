package com.rewabank.customer.query.handler;

import com.rewabank.customer.DTO.CustomerDto;
import com.rewabank.customer.query.FindCustomerQuery;
import com.rewabank.customer.services.ICustomerService;
import lombok.RequiredArgsConstructor;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomerQueryHandler {
    private final ICustomerService iCustomerService;
    @QueryHandler
    public CustomerDto handle(FindCustomerQuery findCustomerQuery){
       return iCustomerService.fetchCustomerDetails(findCustomerQuery.getCorrelationId(),findCustomerQuery.getMobileNumber());
    }
}
