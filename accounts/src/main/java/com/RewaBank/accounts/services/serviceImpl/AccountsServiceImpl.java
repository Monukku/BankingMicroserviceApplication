package com.RewaBank.accounts.services.serviceImpl;

import com.RewaBank.accounts.Utility.AccountCategory;
import com.RewaBank.accounts.Utility.AccountStatus;
import com.RewaBank.accounts.Utility.AccountType;
import com.RewaBank.accounts.exception.AccountAlreadyExistsException;
import com.RewaBank.accounts.mapper.AccountsMapper;
import com.RewaBank.accounts.constants.AccountsConstants;
import com.RewaBank.accounts.dto.AccountsDto;
import com.RewaBank.accounts.entity.Accounts;
import com.RewaBank.accounts.exception.ResourceNotFoundException;
import com.RewaBank.accounts.repository.AccountsRepository;
import com.RewaBank.accounts.services.IAccountsService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Transactional
@Service
public class AccountsServiceImpl implements IAccountsService {

    private static final Logger log = LoggerFactory.getLogger(AccountsServiceImpl.class);
    private final AccountsRepository accountsRepository;
//    private final KafkaProducerService kafkaProducerService;
    public AccountsServiceImpl(AccountsRepository accountsRepository) {
        this.accountsRepository = accountsRepository;
//        this.kafkaProducerService=kafkaProducerService;
    }

    @Override
    public void createAccount(String mobileNumber,AccountType accountType) {


        // Check if customer already exists
        Optional<Accounts> optionalAccounts = accountsRepository.findByMobileNumberAndActiveSw(mobileNumber,AccountsConstants.ACTIVE_SW);
        if (optionalAccounts.isPresent()) {
            throw new AccountAlreadyExistsException("Account already exists with the given mobile number: " + mobileNumber);
        }

        Accounts savedAccount = accountsRepository.save(createNewAccount(mobileNumber,accountType));
        log.info("✅ Account created with Account ID...: {}", savedAccount.getAccountId());

        // Send communication regarding the new account
        // 🔥 Ensure accountId is NOT null before sending Kafka message
//        if (savedAccount.getAccountId() == null) {
//            throw new IllegalStateException("Account ID is null after save! Cannot send Kafka message.");
//        }
//        boolean isSent =false;
//        if (savedAccount.getAccountId() != null) {
//        isSent = kafkaProducerService.sendCommunication(savedAccount, savedAccount);
//        }else
//        {
//            log.warn("⚠️ Account ID is null after save! Cannot send Kafka message.");
//        }
//        if (!isSent) {
//            log.warn("⚠️ Communication request failed for Account ID: {}", savedAccount.getAccountId());
//        }
//        kafkaProducerService.sendCommunication(savedAccount, savedCustomer);
    }

    // Helper method to determine account category based on account type
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

    @Override
    public List<Accounts> getAllAccounts() {
        return accountsRepository.findAll();
    }

    @Override
    public boolean deactivateAccount(Long accountNumber) {
        // Initialize the deactivation status to false
        boolean isDeactivated = false;

        // Fetch the account by the mobile number, throw exception if not found
        Accounts account = accountsRepository.findByAccountNumber(accountNumber)
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
    public boolean updateAccount(AccountsDto accountsDto) {
        boolean isUpdated = false;

        if (accountsDto != null) {
            // Log account number from payload
            System.out.println("Account Number from Payload: " + accountsDto.getAccountNumber());

            // Fetch the account by account number (using a custom query)
            Accounts existingAccount = accountsRepository.findByMobileNumberAndActiveSw(accountsDto.getMobileNumber(),AccountsConstants.ACTIVE_SW)
                    .orElseThrow(() -> new ResourceNotFoundException("Account", "Mobile Number",accountsDto.getMobileNumber()));

            // Log account number from the database
            System.out.println("Account Number from Database: " + existingAccount.getAccountNumber());

            // Ensure the account numbers match before updating
            if (!existingAccount.getAccountNumber().equals(accountsDto.getAccountNumber())) {
                throw new IllegalArgumentException("Account number mismatch. Cannot update with a different account number.");
            }

            // Update the account entity
            AccountsMapper.mapToAccounts(accountsDto, existingAccount);
            accountsRepository.save(existingAccount);
            isUpdated = true;  // Mark update as successful
        }
        return isUpdated;
    }

    @Override
    public boolean deleteAccount(Long accountNumber) {

        Accounts existingAccount = accountsRepository.findById(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "Account Number",accountNumber.toString()));

        if(existingAccount!=null){
           existingAccount.setActiveSw(AccountsConstants.IN_ACTIVE_SW);
           accountsRepository.save(existingAccount);
        }
        return true;  // Return true indicating successful deletion
    }

    @Override
    public boolean updateCommunicationStatus(Long accountId) {
        final int MAX_RETRIES = 3;
        final long RETRY_DELAY_MS = 2000; // 2 seconds delay
        int retryCount = 0;

        while (retryCount < MAX_RETRIES) {
            try {
                // Fetch account details
                Accounts account = accountsRepository.findByAccountId(accountId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Account", "Account ID", accountId.toString()));

                // Update communication status
                account.setActiveSw(true);
                accountsRepository.save(account);

                log.info("✅ Communication status updated successfully for Account ID: {}", accountId);
                return true; // Successful update
            } catch (ResourceNotFoundException e) {
                retryCount++;
                log.warn("⚠️ Account not found with Account ID: {}. Retrying... (Attempt {}/{})",
                        accountId, retryCount, MAX_RETRIES);

                // Delay before retrying
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    log.error("❌ Retry process interrupted for Account ID: {}", accountId, ex);
                    return false; // Return false since retrying was interrupted
                }
            }
        }

        // Log failure after max retries
        log.error("❌ Failed to update communication status after {} retries for Account ID: {}", MAX_RETRIES, accountId);
        return false; // Return false if all retries failed
    }

}
