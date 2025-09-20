package com.RewaBank.accounts.command.interceptor;

import com.RewaBank.accounts.command.CreateAccountCommand;
import com.RewaBank.accounts.command.DeleteAccountCommand;
import com.RewaBank.accounts.command.UpdateAccountCommand;
import com.RewaBank.accounts.constants.AccountsConstants;
import com.RewaBank.accounts.entity.Accounts;
import com.RewaBank.accounts.exception.AccountAlreadyExistsException;
import com.RewaBank.accounts.exception.ResourceNotFoundException;
import com.RewaBank.accounts.repository.AccountsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

@Component
@Slf4j
@RequiredArgsConstructor
public class AccountsCommandInterceptor implements MessageDispatchInterceptor<CommandMessage<?>> {
    private final AccountsRepository accountsRepository;

    @Nonnull
    @Override
    public BiFunction<Integer, CommandMessage<?>, CommandMessage<?>> handle(@Nonnull List<? extends CommandMessage<?>> messages) {
        return (index, command) -> {
            if (CreateAccountCommand.class.equals(command.getPayloadType())) {
                CreateAccountCommand createAccountCommand = (CreateAccountCommand) command.getPayload();

                Optional<Accounts> optionalAccounts = accountsRepository
                        .findByMobileNumberAndActiveSw(createAccountCommand.getMobileNumber(), true);
                if (optionalAccounts.isPresent()) {
                    log.info("exception thrown from Accounts command interceptor");
      throw new AccountAlreadyExistsException("Account already created with given mobileNumber :" + createAccountCommand.getMobileNumber());
                }
            }else if (UpdateAccountCommand.class.equals(command.getPayloadType())) {
                    UpdateAccountCommand updateAccountCommand = (UpdateAccountCommand) command.getPayload();
                    Accounts account = accountsRepository
                            .findByMobileNumberAndActiveSw(updateAccountCommand.getMobileNumber(), true)
                            .orElseThrow(() ->
                                    new ResourceNotFoundException("Account", "mobileNumber", updateAccountCommand.getMobileNumber())
                            );
                } else if (DeleteAccountCommand.class.equals(command.getPayloadType())) {
                    DeleteAccountCommand deleteAccountCommand = (DeleteAccountCommand) command.getPayload();
                    Accounts account = accountsRepository
                            .findByAccountNumberAndActiveSw(deleteAccountCommand.getAccountNumber(), AccountsConstants.ACTIVE_SW)
                            .orElseThrow(() -> new ResourceNotFoundException
                                    ("Account...inter", "accountNumber", deleteAccountCommand.getAccountNumber().toString()));
                }
                return command;
            };
        }
}
