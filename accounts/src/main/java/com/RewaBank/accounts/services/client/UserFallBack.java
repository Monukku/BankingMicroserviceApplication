package com.RewaBank.accounts.services.client;

import com.RewaBank.accounts.dto.LoansDto;
import org.springframework.http.ResponseEntity;

public class UserFallBack implements UserFeignClient{
    @Override
    public ResponseEntity<LoansDto> fetchLoansDetails(String correlationId, String mobileNumber) {

        return null;
    }
}
