package com.RewaBank.loans.mapper;

import com.RewaBank.loans.Entity.Loans;
import com.RewaBank.loans.command.event.LoanUpdatedEvent;
import com.RewaBank.loans.dto.LoansDto;

public class LoansMapper {
    public  static LoansDto mapToLoansDto(Loans loans,LoansDto loansDto){

          loansDto.setLoanNumber(loans.getLoanNumber());
          loansDto.setMobileNumber(loans.getMobileNumber());
          loansDto.setLoanType(loans.getLoanType());
          loansDto.setTotalLoan(loans.getTotalLoan());
          loansDto.setAmountPaid(loans.getAmountPaid());
          loansDto.setOutstandingAmount(loans.getOutstandingAmount());

          return loansDto;
    }

    public  static Loans mapToLoans(LoansDto loansDto,Loans loans){

            loans.setAmountPaid(loansDto.getAmountPaid());
            loans.setLoanNumber(loansDto.getLoanNumber());
            loans.setTotalLoan(loansDto.getTotalLoan());
            loans.setMobileNumber(loansDto.getMobileNumber());
            loans.setLoanNumber(loansDto.getLoanNumber());
            loans.setOutstandingAmount(loansDto.getOutstandingAmount());

            return loans;
    }

    public static Loans mapEventToLoans(LoanUpdatedEvent event,Loans loans){
        loans.setLoanType(event.getLoanType());
        loans.setTotalLoan(event.getTotalLoan());
        loans.setAmountPaid(event.getAmountPaid());
        loans.setOutstandingAmount(event.getOutstandingAmount());
        return loans;
    }
}
