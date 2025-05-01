package com.RewaBank.accounts.services;

import com.RewaBank.accounts.Utility.AccountType;
import com.RewaBank.accounts.dto.AccountsDto;
import com.RewaBank.accounts.dto.CustomerDto;
import com.RewaBank.accounts.entity.Accounts;
import java.util.List;

public interface IAccountsService {

public void createAccount(String mobileNumber,AccountType accountType);

public List<Accounts> getAllAccounts();

public boolean deactivateAccount(Long mobileNumber);

public AccountsDto fetchAccount(String mobileNumber);

public boolean updateAccount(AccountsDto accountsDto);

public boolean deleteAccount(Long accountNumber);

public boolean updateCommunicationStatus(Long accountNumber);

}
