package com.RewaBank.loans.services.ServiceImpl;

import com.RewaBank.loans.Entity.Loans;
import com.RewaBank.loans.command.event.LoanUpdatedEvent;
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

@Service
@AllArgsConstructor
public class LoansServiceImpl  implements ILoansService {

    private LoansRepository loansRepository;

    @Override
    public void createLoans(Loans loans) {
         Optional<Loans> optionalLoans=loansRepository.findByMobileNumberAndActiveSw(loans.getMobileNumber(),LoansConstants.ACTIVE_SW);

         if(optionalLoans.isPresent()){
             throw new LoanAlreadyExistsException("Loans with given mobile number already exist");
         }
          loansRepository.save(loans);
    }

    @Override
    public LoansDto fetchLoansDetails(String mobileNumber) {

                Loans  loans= loansRepository.findByMobileNumberAndActiveSw(mobileNumber,LoansConstants.ACTIVE_SW).orElseThrow(
                           ()-> new ResourceNotFoundException("Loans","MobileNumber",mobileNumber)
                   );

        return  LoansMapper.mapToLoansDto(loans,new LoansDto());
    }

    @Override
    public boolean updateLoan(LoanUpdatedEvent event) {
         boolean   isUpdated=false;
         if(!isUpdated) {

             Loans loans = loansRepository.findByMobileNumberAndActiveSw(event.getMobileNumber(),LoansConstants.ACTIVE_SW).orElseThrow(
                     () -> new ResourceNotFoundException("Loans", "Mobile Number", event.getMobileNumber())
             );

             LoansMapper.mapEventToLoans(event,loans);
             loansRepository.save(loans);
             isUpdated=true;
         }
        return isUpdated;
    }

    @Override
    public boolean deleteLoan(Long loanNumber) {
        boolean   isDeleted=false;
            Loans loans = loansRepository.findByLoanNumberAndActiveSw(loanNumber,LoansConstants.ACTIVE_SW).orElseThrow(
                    () -> new ResourceNotFoundException("Loans", "mobileNumber", loanNumber.toString())
            );
            loans.setActiveSw(LoansConstants.IN_ACTIVE_SW);
            loansRepository.save(loans);
            isDeleted = true;
        return isDeleted;

    }

}
