package com.RewaBank.loans.services;

import com.RewaBank.loans.Entity.Loans;
import com.RewaBank.loans.command.event.LoanUpdatedEvent;
import com.RewaBank.loans.dto.LoansDto;

public interface ILoansService {


    void createLoans(Loans loans);

    LoansDto fetchLoansDetails(String mobileNumber);

    boolean  updateLoan(LoanUpdatedEvent event);

    boolean  deleteLoan(Long loanNumber);






}
