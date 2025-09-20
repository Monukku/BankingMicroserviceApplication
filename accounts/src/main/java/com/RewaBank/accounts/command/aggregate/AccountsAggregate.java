package com.RewaBank.accounts.command.aggregate;

import com.RewaBank.accounts.Utility.AccountCategory;
import com.RewaBank.accounts.Utility.AccountStatus;
import com.RewaBank.accounts.Utility.AccountType;
import com.RewaBank.accounts.command.CreateAccountCommand;
import com.RewaBank.accounts.command.DeleteAccountCommand;
import com.RewaBank.accounts.command.UpdateAccountCommand;
import com.RewaBank.accounts.command.event.AccountCreatedEvent;
import com.RewaBank.accounts.command.event.AccountDeletedEvent;
import com.RewaBank.accounts.command.event.AccountUpdatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;
import org.springframework.beans.BeanUtils;
import java.math.BigDecimal;

@Aggregate
@Slf4j
public class AccountsAggregate {

    @AggregateIdentifier
    private Long accountNumber;

    private String mobileNumber;
    private  String branchAddress;
    private AccountType accountType;
    private  boolean activeSw;
    private BigDecimal balance;
    private AccountStatus accountStatus;
    private AccountCategory accountCategory;

    public AccountsAggregate() {
    }

    @CommandHandler
    public AccountsAggregate(CreateAccountCommand createAccountCommand){
        AccountCreatedEvent accountCreatedEvent=new AccountCreatedEvent();
        BeanUtils.copyProperties(createAccountCommand,accountCreatedEvent);
        AggregateLifecycle.apply(accountCreatedEvent);
        log.info(" 'AccountCreatedEvent' from accounts aggregate commandHandler completed");
    }

    @EventSourcingHandler
    public  void on(AccountCreatedEvent accountCreatedEvent){
        this.accountNumber=accountCreatedEvent.getAccountNumber();
        this.mobileNumber=accountCreatedEvent.getMobileNumber();
        this.branchAddress=accountCreatedEvent.getBranchAddress();
        this.accountType=accountCreatedEvent.getAccountType();
        this.activeSw=accountCreatedEvent.isActiveSw();
        this.balance=accountCreatedEvent.getBalance();
        this.accountStatus=accountCreatedEvent.getAccountStatus();
        this.accountCategory=accountCreatedEvent.getAccountCategory();
        log.info("'AccountCreatedEvent' from accounts aggregate @EventSourcingHandler completed");
    }

    @CommandHandler
    public void handle(UpdateAccountCommand updateCommand) {
        AccountUpdatedEvent accountUpdatedEvent = new AccountUpdatedEvent();
        BeanUtils.copyProperties(updateCommand, accountUpdatedEvent);
        AggregateLifecycle.apply(accountUpdatedEvent);
    }

    @EventSourcingHandler
    public void on(AccountUpdatedEvent accountUpdatedEvent) {
        this.accountType = accountUpdatedEvent.getAccountType();
        this.branchAddress = accountUpdatedEvent.getBranchAddress();
    }

    @CommandHandler
    public void handle(DeleteAccountCommand deleteCommand) {
        AccountDeletedEvent accountDeletedEvent = new AccountDeletedEvent();
        BeanUtils.copyProperties(deleteCommand, accountDeletedEvent);
        AggregateLifecycle.apply(accountDeletedEvent);
    }

    @EventSourcingHandler
    public void on(AccountDeletedEvent accountDeletedEvent) {
        this.activeSw = accountDeletedEvent.isActiveSw();
    }

}
