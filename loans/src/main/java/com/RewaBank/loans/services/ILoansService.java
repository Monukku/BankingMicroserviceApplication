package com.RewaBank.loans.services;

import com.RewaBank.loans.dto.LoansDto;

public interface ILoansService {


    void createLoans(String mobileNumber);

    LoansDto fetchLoansDetails(String mobileNumber);

    boolean  update(LoansDto loansDto);

    boolean  delete(String mobileNumber);






}
