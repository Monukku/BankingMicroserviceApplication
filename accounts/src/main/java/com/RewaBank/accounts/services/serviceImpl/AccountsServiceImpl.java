package com.RewaBank.accounts.services.serviceImpl;

import com.RewaBank.accounts.Utility.AccountCategory;
import com.RewaBank.accounts.Utility.AccountStatus;
import com.RewaBank.accounts.Utility.AccountType;
import com.RewaBank.accounts.command.event.AccountUpdatedEvent;
import com.RewaBank.accounts.exception.AccountAlreadyExistsException;
import com.RewaBank.accounts.mapper.AccountsMapper;
import com.RewaBank.accounts.constants.AccountsConstants;
import com.RewaBank.accounts.dto.AccountsDto;
import com.RewaBank.accounts.entity.Accounts;
import com.RewaBank.accounts.exception.ResourceNotFoundException;
import com.RewaBank.accounts.repository.AccountsRepository;
import com.RewaBank.accounts.services.IAccountsService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Random;

@Transactional
@Service
@Slf4j
public class AccountsServiceImpl implements IAccountsService {

    private final AccountsRepository accountsRepository;
//    private final KafkaProducerService kafkaProducerService;
    public AccountsServiceImpl(AccountsRepository accountsRepository) {
        this.accountsRepository = accountsRepository;
//        this.kafkaProducerService=kafkaProducerService;
    }

    @Override
    public void createAccount(Accounts accounts, AccountType accountType) {

        Optional<Accounts> optionalAccounts = accountsRepository.findByMobileNumberAndActiveSw(accounts.getMobileNumber(),
                AccountsConstants.ACTIVE_SW);
        if (optionalAccounts.isPresent()) {
            log.info("Account already registered with given mobileNumber message from ServiceImpl layer");
            throw new AccountAlreadyExistsException("Account already registered with given mobileNumber " + accounts.getMobileNumber());
        }
        accountsRepository.save(accounts);
        log.info("Account created from message from ServiceImpl layer");
    }

    private AccountCategory determineCategoryForAccount(AccountType accountType) {
        return switch (accountType) {
            case SAVINGS, CHECKING -> AccountCategory.PERSONAL;
            case BUSINESS -> AccountCategory.BUSINESS;
            case JOINT -> AccountCategory.JOINT;
            default -> AccountCategory.DEFAULT; // Default category if not specified
        };
    }

    private Accounts createNewAccount(String mobileNumber, AccountType accountType) {
        Accounts newAccount = new Accounts();

        // Generate a random account number
        long randomAccountNumber = 1000000000L + new Random().nextInt(900000000);
        newAccount.setAccountNumber(randomAccountNumber);
        newAccount.setBalance(BigDecimal.TEN);
        newAccount.setBranchAddress(AccountsConstants.ADDRESS);
        newAccount.setActiveSw(AccountsConstants.ACTIVE_SW);

        // Determine account status based on the account type
        switch (accountType) {
            case SAVINGS, CHECKING -> newAccount.setAccountStatus(AccountStatus.ACTIVE);
            case FIXED_DEPOSIT, CREDIT_CARD -> newAccount.setAccountStatus(AccountStatus.INACTIVE);
            case RECURRING_DEPOSIT -> newAccount.setAccountStatus(AccountStatus.PENDING);
            case LOAN -> newAccount.setAccountStatus(AccountStatus.SUSPENDED);
            case BUSINESS, JOINT ->
                    newAccount.setAccountStatus(AccountStatus.ACTIVE); // Assuming active for these types
            default -> throw new IllegalArgumentException("Invalid account type provided");
        }

        // Set account category based on the account type
        AccountCategory category = determineCategoryForAccount(accountType);
        newAccount.setAccountCategory(category);
        newAccount.setAccountType(accountType);
        return newAccount;
    }

//    @Override
//    public List<Accounts> getAllAccounts() {
//        return accountsRepository.findAll();
//    }

    @Override
    public boolean deactivateAccount(Long accountNumber) {
        // Initialize the deactivation status to false
        boolean isDeactivated = false;

        // Fetch the account by the mobile number, throw exception if not found
        Accounts account = accountsRepository.findByAccountNumberAndActiveSw(accountNumber,AccountsConstants.ACTIVE_SW)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "Mobile Number", accountNumber.toString()));

        // Check if the account is already inactive to avoid redundant operations
        if (account.getAccountStatus() == AccountStatus.INACTIVE) {
            throw new IllegalStateException("Account is already inactive");
        }
        // Set the account status to INACTIVE
        account.setAccountStatus(AccountStatus.INACTIVE);

        // Save the updated account back to the repository
        accountsRepository.save(account);

        // Set the deactivation status to true as the operation succeeded
        isDeactivated = true;
        return isDeactivated;
    }

    @Override
    public AccountsDto fetchAccount(String mobileNumber) {
        // Fetch the account by the mobile number, throw exception if not found
        Accounts account = accountsRepository.findByMobileNumberAndActiveSw(mobileNumber,AccountsConstants.ACTIVE_SW)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "Mobile Number", mobileNumber));
        // Map the Account entity to an AccountsDto
        return AccountsMapper.mapToAccountsDto(account,new AccountsDto());
    }

    @Override
    public boolean updateAccount(AccountUpdatedEvent event) {
        Accounts account = accountsRepository.findByMobileNumberAndActiveSw(event.getMobileNumber(),
                AccountsConstants.ACTIVE_SW).orElseThrow(() -> new ResourceNotFoundException("Account", "mobileNumber",
                event.getMobileNumber()));
        AccountsMapper.mapEventToAccount(event, account);
        accountsRepository.save(account);
        return true;
    }

    @Override
    public boolean deleteAccount(Long accountNumber) {

        Accounts existingAccount = accountsRepository.findByAccountNumberAndActiveSw(accountNumber,AccountsConstants.ACTIVE_SW)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "Account Number",accountNumber.toString()));

        if(existingAccount!=null){
           existingAccount.setActiveSw(AccountsConstants.IN_ACTIVE_SW);
           accountsRepository.save(existingAccount);
        }
        return true;
    }
}
