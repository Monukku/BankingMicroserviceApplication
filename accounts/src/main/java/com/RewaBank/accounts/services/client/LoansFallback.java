package com.RewaBank.accounts.services.client;

import com.RewaBank.accounts.dto.LoansDto;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
//implementing fall back mechanism for LoansFeignClient
@Component
public class LoansFallback implements LoansFeignClient{

    @Override
    public ResponseEntity<LoansDto> fetchLoansDetails(String correlationId, String mobileNumber) {

        return null;
    }
}
