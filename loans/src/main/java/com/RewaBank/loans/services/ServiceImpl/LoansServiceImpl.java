package com.RewaBank.loans.services.ServiceImpl;

import com.RewaBank.loans.Entity.Loans;
import com.RewaBank.loans.constants.LoansConstants;
import com.RewaBank.loans.dto.LoansDto;
import com.RewaBank.loans.exception.LoanAlreadyExistsException;
import com.RewaBank.loans.exception.ResourceNotFoundException;
import com.RewaBank.loans.mapper.LoansMapper;
import com.RewaBank.loans.repository.LoansRepository;
import com.RewaBank.loans.services.ILoansService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.Random;

@Service
@AllArgsConstructor
public class LoansServiceImpl  implements ILoansService {

    private LoansRepository loansRepository;

    @Override
    public void createLoans(String mobileNumber) {
         Optional<Loans> optionalLoans=loansRepository.findByMobileNumber(mobileNumber);

         if(optionalLoans.isPresent()){
             throw new LoanAlreadyExistsException("Loans with given mobile number already exist");
         }
          loansRepository.save(createNewLoan(mobileNumber));
    }

    private Loans createNewLoan(String mobileNumber){

        Loans loan=new Loans();
        long randomLoanNumber = 100000000000L + new Random().nextInt(900000000);

        loan.setLoanNumber(String.valueOf(randomLoanNumber));
        loan.setMobileNumber(mobileNumber);
        loan.setLoanType(LoansConstants.HOME_LOAN);
        loan.setAmountPaid(0);
        loan.setOutstandingAmount(LoansConstants.NEW_LOAN_LIMIT);
        loan.setTotalLoan(LoansConstants.NEW_LOAN_LIMIT);

        return loan;
    }

    @Override
    public LoansDto fetchLoansDetails(String mobileNumber) {

                Loans  loans= loansRepository.findByMobileNumber(mobileNumber).orElseThrow(
                           ()-> new ResourceNotFoundException("Loans","MobileNumber",mobileNumber)
                   );

        return  LoansMapper.mapToLoansDto(loans,new LoansDto());
    }

    @Override
    public boolean update(LoansDto loansDto) {
         boolean   isUpdated=false;
         if(!isUpdated) {

             Loans loans = loansRepository.findByMobileNumber(loansDto.getMobileNumber()).orElseThrow(
                     () -> new ResourceNotFoundException("Loans", "Mobile Number", loansDto.getMobileNumber())
             );

             LoansMapper.mapToLoans(loansDto,loans);
             loansRepository.save(loans);

             isUpdated=true;
         }
        return isUpdated;
    }

    @Override
    public boolean delete(String mobileNumber) {
        boolean   isDeleted=false;
        if(!isDeleted) {
            Loans loans = loansRepository.findByMobileNumber(mobileNumber).orElseThrow(
                    () -> new ResourceNotFoundException("Loans", "mobileNumber", mobileNumber)
            );

            loansRepository.delete(loans);
            isDeleted = true;
        }
        return isDeleted;

    }

}
