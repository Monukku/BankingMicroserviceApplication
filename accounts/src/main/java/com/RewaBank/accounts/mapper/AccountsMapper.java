//package com.RewaBank.accounts.mapper;
//import com.RewaBank.accounts.dto.AccountsDto;
//import com.RewaBank.accounts.entity.Account;
//
//public class AccountsMapper {
//
//    // Mapping Account entity to AccountsDto
//    public static AccountsDto mapToAccountsDto(Account account) {
//        if (account == null) return null;
//
//        AccountsDto dto = new AccountsDto();
//        dto.setAccountNumber(account.getAccountNumber());
//        dto.setAccountType(account.getAccountType());
//        dto.setBranchAddress(account.getBranchAddress());
//        dto.setAccountStatus(account.getAccountStatus());
//        dto.setAccountCategory(account.getAccountCategory());
//        dto.setBalance(account.getBalance());
//        return dto;
//
//    }
//    // Mapping DTO to existing Account entity (for updates)
//    public static Account mapToAccounts(AccountsDto accountsDto, Account account) {
//        if (accountsDto == null || account == null) return null;
//
//        // Map the fields from DTO to the existing entity
//        account.setAccountType(accountsDto.getAccountType());
//        account.setBranchAddress(accountsDto.getBranchAddress());
//        account.setAccountStatus(accountsDto.getAccountStatus());
//        account.setAccountCategory(accountsDto.getAccountCategory());
//        account.setBalance(accountsDto.getBalance());
//        // We don't set the account number again since it's an immutable field in updates
//   return  account;
//    }
//}

package com.RewaBank.accounts.mapper;

import com.RewaBank.accounts.dto.AccountsDto;
import com.RewaBank.accounts.entity.Accounts;

public class AccountsMapper {

    // Mapping Account entity to AccountsDto (for data transfer)
    public static AccountsDto mapToAccountsDto(Accounts accounts,AccountsDto dto) {
        if (accounts == null) return null;

        dto.setAccountType(accounts.getAccountType());
        dto.setBranchAddress(accounts.getBranchAddress());
        dto.setAccountStatus(accounts.getAccountStatus());
        dto.setAccountCategory(accounts.getAccountCategory());
        dto.setActiveSw(accounts.isActiveSw());
        dto.setBalance(accounts.getBalance());
        return dto;
    }

    // Mapping AccountsDto to a new Account entity (for creating new accounts)
//    public static Accounts mapToAccounts(AccountsDto accountsDto,Accounts accounts) {
//        if (accountsDto == null) return null;
//        Accounts account = new Accounts();
//        accounts.setAccountNumber(accountsDto.getAccountNumber());
//        accounts.setAccountType(accountsDto.getAccountType());
//        accounts.setBranchAddress(accountsDto.getBranchAddress());
//        accounts.setAccountStatus(accountsDto.getAccountStatus());
//        accounts.setAccountCategory(accountsDto.getAccountCategory());
//        accounts.setBalance(accountsDto.getBalance());
//        return account;
//    }

    // Mapping AccountsDto to an existing Account entity (for updates)
    public static Accounts mapToAccounts(AccountsDto accountsDto, Accounts account) {
//        if (accountsDto == null || account == null) return null;

        account.setAccountType(accountsDto.getAccountType());
        account.setBranchAddress(accountsDto.getBranchAddress());
    return  account;
    }
}
