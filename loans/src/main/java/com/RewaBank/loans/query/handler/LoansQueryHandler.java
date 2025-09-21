package com.RewaBank.loans.query.handler;

import com.RewaBank.loans.dto.LoansDto;
import com.RewaBank.loans.query.FindLoansQuery;
import com.RewaBank.loans.services.ILoansService;
import lombok.RequiredArgsConstructor;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoansQueryHandler {
    private final ILoansService iLoansService;

    @QueryHandler
    public LoansDto fetchLoanDetails(FindLoansQuery findLoansQuery){
       return iLoansService.fetchLoansDetails(findLoansQuery.getMobileNumber());
    }
}
