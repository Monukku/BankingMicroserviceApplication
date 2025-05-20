package com.RewaBank.accounts.query.projection;

import com.RewaBank.accounts.command.event.AccountCreatedEvent;
import com.RewaBank.accounts.command.event.AccountDeletedEvent;
import com.RewaBank.accounts.command.event.AccountUpdatedEvent;
import com.RewaBank.accounts.entity.Accounts;
import com.RewaBank.accounts.services.IAccountsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ProcessingGroup("account-group")
public class AccountProjection {

    private final IAccountsService iAccountsService;

    @EventHandler
    public void on(AccountCreatedEvent event) {
        Accounts accountEntity = new Accounts();
        BeanUtils.copyProperties(event, accountEntity);
        iAccountsService.createAccount(accountEntity,accountEntity.getAccountType());
        log.info("AccountCreatedEvent from AccountProject EventHandler");
    }

    @EventHandler
    public void on(AccountUpdatedEvent event) {
        iAccountsService.updateAccount(event);
    }

    @EventHandler
    public void on(AccountDeletedEvent event) {
        iAccountsService.deleteAccount(event.getAccountNumber());
    }

}
