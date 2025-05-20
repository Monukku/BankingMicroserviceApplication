package com.RewaBank.accounts.mapper;

import com.RewaBank.accounts.command.event.AccountUpdatedEvent;
import com.RewaBank.accounts.dto.AccountsDto;
import com.RewaBank.accounts.entity.Accounts;

public class AccountsMapper {


    public static AccountsDto mapToAccountsDto(Accounts accounts, AccountsDto accountsDto) {
        accountsDto.setAccountNumber(accounts.getAccountNumber());
        accountsDto.setMobileNumber(accounts. getMobileNumber());
        accountsDto.setBranchAddress(accounts.getBranchAddress());
        accountsDto.setAccountType(accounts.getAccountType());
        accountsDto.setAccountStatus(accounts.getAccountStatus());
        accountsDto.setAccountCategory(accounts.getAccountCategory());
        accountsDto.setBalance(accounts.getBalance());
        accountsDto.setActiveSw(accounts.isActiveSw());
        return accountsDto;
    }

    public static Accounts mapToAccounts(AccountsDto accountsDto, Accounts accounts) {
        accounts.setAccountType(accountsDto.getAccountType());
        accounts.setBranchAddress(accountsDto.getBranchAddress());
        return accounts;
    }

    public static Accounts mapEventToAccount(AccountUpdatedEvent event, Accounts account) {
        account.setAccountType(event.getAccountType());
        account.setBranchAddress(event.getBranchAddress());
        return account;
    }
}
