package com.rewabank.loans.services;

import com.rewabank.loans.dto.LoansDto;

public interface ILoansService {


    void createLoans(String mobileNumber);

    LoansDto fetchLoansDetails(String mobileNumber);

    boolean  update(LoansDto loansDto);

    boolean  delete(String mobileNumber);






}
