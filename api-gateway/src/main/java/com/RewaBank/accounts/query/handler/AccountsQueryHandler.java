package com.RewaBank.accounts.query.handler;

import com.RewaBank.accounts.dto.AccountsDto;
import com.RewaBank.accounts.query.FindAccountQuery;
import com.RewaBank.accounts.services.IAccountsService;
import lombok.RequiredArgsConstructor;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountsQueryHandler {

    private final IAccountsService iAccountsService;

    @QueryHandler
    public AccountsDto findAccount(FindAccountQuery query) {
        return iAccountsService.fetchAccount(query.getMobileNumber());
    }
}
