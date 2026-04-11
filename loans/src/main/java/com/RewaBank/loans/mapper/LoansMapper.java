package com.rewabank.loans.mapper;

import com.rewabank.loans.Entity.Loans;
import com.rewabank.loans.dto.LoansDto;

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
}
