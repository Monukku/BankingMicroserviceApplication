package com.RewaBank.loans.command.aggregate;

import com.RewaBank.loans.command.CreateLoanCommand;
import com.RewaBank.loans.command.DeleteLoanCommand;
import com.RewaBank.loans.command.UpdateLoanCommand;
import com.RewaBank.loans.command.event.LoanCreatedEvent;
import com.RewaBank.loans.command.event.LoanDeletedEvent;
import com.RewaBank.loans.command.event.LoanUpdatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;
import org.springframework.beans.BeanUtils;

@Aggregate
@Slf4j
public class LoanAggregate {

    @AggregateIdentifier
    private Long loanNumber;

    private String mobileNumber;

    private String loanType;

    private int totalLoan;

    private String loanStatus;

    private int amountPaid;

    private  int outstandingAmount;

    private boolean activeSw;


    public LoanAggregate() {

    }

    @CommandHandler
    public LoanAggregate(CreateLoanCommand createLoanCommand) {
        LoanCreatedEvent loanCreatedEvent=new LoanCreatedEvent();
        BeanUtils.copyProperties(createLoanCommand,loanCreatedEvent);
        AggregateLifecycle.apply(loanCreatedEvent);
    }

    @EventSourcingHandler
    public void on(LoanCreatedEvent loanCreatedEvent){
              this.loanNumber = loanCreatedEvent.getLoanNumber();
              this.mobileNumber=loanCreatedEvent.getMobileNumber();
              this.loanType =loanCreatedEvent.getLoanType();
              this.totalLoan=loanCreatedEvent.getTotalLoan();
              this.loanStatus=loanCreatedEvent.getLoanStatus();
              this.amountPaid=loanCreatedEvent.getAmountPaid();
              this.outstandingAmount=loanCreatedEvent.getOutstandingAmount();
              this.activeSw =loanCreatedEvent.isActiveSw();
    }

    @CommandHandler
    public void handle(UpdateLoanCommand updateLoanCommand){
        LoanUpdatedEvent loanUpdatedEvent=new LoanUpdatedEvent();
        BeanUtils.copyProperties(updateLoanCommand,loanUpdatedEvent);
        AggregateLifecycle.apply(loanUpdatedEvent);
    }

    @EventSourcingHandler
    public void on(LoanUpdatedEvent loanUpdatedEvent){
            this.totalLoan=loanUpdatedEvent.getTotalLoan();
            this.loanType=loanUpdatedEvent.getLoanType();
            this.outstandingAmount=loanUpdatedEvent.getOutstandingAmount();
            this.amountPaid=loanUpdatedEvent.getAmountPaid();
    }

    @CommandHandler
    public void handle(DeleteLoanCommand deleteLoanCommand){
        LoanDeletedEvent loanDeletedEvent=new LoanDeletedEvent();
        BeanUtils.copyProperties(deleteLoanCommand,loanDeletedEvent);
        AggregateLifecycle.apply(loanDeletedEvent);
    }

    @EventSourcingHandler
    public void on(LoanDeletedEvent loanDeletedEvent){
           this.activeSw=loanDeletedEvent.isActiveSw();
    }
}
