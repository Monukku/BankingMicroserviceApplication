package com.rewabank.customer.query.handler;

import com.rewabank.customer.DTO.CustomerDto;
import com.rewabank.customer.query.FindCustomerQuery;
import com.rewabank.customer.services.ICustomerService;
import lombok.RequiredArgsConstructor;
import org.axonframework.queryhandling.QueryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomerQueryHandler {
    private static final Logger logger = LoggerFactory.getLogger(CustomerQueryHandler.class);

    private final ICustomerService iCustomerService;

    @QueryHandler
    public CustomerDto findCustomer(FindCustomerQuery findCustomerQuery) {
        return iCustomerService.fetchCustomerDetails(findCustomerQuery.getMobileNumber());
    }

}
