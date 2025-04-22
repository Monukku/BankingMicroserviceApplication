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
    public static AccountsDto mapToAccountsDto(Accounts account) {
        if (account == null) return null;

        AccountsDto dto = new AccountsDto();
        dto.setAccountNumber(account.getAccountNumber());
        dto.setAccountType(account.getAccountType());
        dto.setBranchAddress(account.getBranchAddress());
        dto.setAccountStatus(account.getAccountStatus());
        dto.setAccountCategory(account.getAccountCategory());
        dto.setBalance(account.getBalance());
        return dto;
    }

    // Mapping AccountsDto to a new Account entity (for creating new accounts)
    public static Accounts mapToAccounts(AccountsDto accountsDto) {
        if (accountsDto == null) return null;

        Accounts account = new Accounts();
        account.setAccountNumber(accountsDto.getAccountNumber());
        account.setAccountType(accountsDto.getAccountType());
        account.setBranchAddress(accountsDto.getBranchAddress());
        account.setAccountStatus(accountsDto.getAccountStatus());
        account.setAccountCategory(accountsDto.getAccountCategory());
        account.setBalance(accountsDto.getBalance());

        return account;
    }

    // Mapping AccountsDto to an existing Account entity (for updates)
    public static Accounts mapToAccounts(AccountsDto accountsDto, Accounts account) {
        if (accountsDto == null || account == null) return null;

        // Account number should be immutable during an update
        // account.setAccountNumber(accountsDto.getAccountNumber()); // Do NOT update account number

        // Update mutable fields only
        account.setAccountType(accountsDto.getAccountType());
        account.setBranchAddress(accountsDto.getBranchAddress());
        account.setAccountStatus(accountsDto.getAccountStatus());
        account.setAccountCategory(accountsDto.getAccountCategory());
        account.setBalance(accountsDto.getBalance());
    return  account;
    }
}
