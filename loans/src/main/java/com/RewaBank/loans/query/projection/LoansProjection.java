package com.RewaBank.loans.query.projection;

import com.RewaBank.loans.Entity.Loans;
import com.RewaBank.loans.command.event.LoanCreatedEvent;
import com.RewaBank.loans.command.event.LoanDeletedEvent;
import com.RewaBank.loans.command.event.LoanUpdatedEvent;
import com.RewaBank.loans.services.ILoansService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ProcessingGroup("loan-group")
@Slf4j
public class LoansProjection {

    private final ILoansService iLoansService;

    @EventHandler
    public void on(LoanCreatedEvent event){
        Loans loans=new Loans();
        BeanUtils.copyProperties(event,loans);

        iLoansService.createLoans(loans);
        log.info("LoanCreatedEvent from AccountProject EventHandler");
    }

    @EventHandler
    public void on(LoanUpdatedEvent event){
        iLoansService.updateLoan(event);
    }

    @EventHandler
    public void on(LoanDeletedEvent event){
        iLoansService.deleteLoan(event.getLoanNumber());
    }

}
