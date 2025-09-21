package com.RewaBank.loans.command.intercepter;
import com.RewaBank.loans.Entity.Loans;
import com.RewaBank.loans.command.CreateLoanCommand;
import com.RewaBank.loans.command.DeleteLoanCommand;
import com.RewaBank.loans.command.UpdateLoanCommand;
import com.RewaBank.loans.constants.LoansConstants;
import com.RewaBank.loans.exception.LoanAlreadyExistsException;
import com.RewaBank.loans.repository.LoansRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.springframework.stereotype.Component;
import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

@RequiredArgsConstructor
@Slf4j
@Component
public class LoansCommandInterceptor implements MessageDispatchInterceptor<CommandMessage<?>> {

    private final LoansRepository loansRepository;

    @Nonnull
    @Override
    public BiFunction<Integer, CommandMessage<?>, CommandMessage<?>> handle(@Nonnull List<? extends CommandMessage<?>> messages) {
        return (index,command) -> {
            if(CreateLoanCommand.class.equals(command.getPayload())) {
                CreateLoanCommand createCardCommand = (CreateLoanCommand) command.getPayload();
            Optional<Loans> optionalCards = loansRepository.findByMobileNumberAndActiveSw(createCardCommand.getMobileNumber(), true);
                if (optionalCards.isPresent()) {
                    throw new LoanAlreadyExistsException("Card already created with given mobileNumber :"
                            + createCardCommand.getMobileNumber());
                }
            }else if (UpdateLoanCommand.class.equals(command.getPayload())){
              UpdateLoanCommand updateCardCommand=(UpdateLoanCommand) command.getPayload();
              Optional<Loans> optionalCards = loansRepository.findByMobileNumberAndActiveSw(updateCardCommand.getMobileNumber(),true);
                     if (optionalCards.isPresent()){
                          throw new LoanAlreadyExistsException("Card already created with given mobileNumber :"
                                  + updateCardCommand.getMobileNumber());
                     }
            }else if (DeleteLoanCommand.class.equals(command.getPayload())) {
                        DeleteLoanCommand deleteCardCommand=(DeleteLoanCommand) command.getPayload();
                      Optional<Loans> optionalCards =  loansRepository.findByLoanNumberAndActiveSw(deleteCardCommand.getLoanNumber(), LoansConstants.ACTIVE_SW);
                      if (optionalCards.isPresent()){
                          throw new LoanAlreadyExistsException("Card already created with given mobileNumber :"
                                  + deleteCardCommand.getLoanNumber());
                      }
                 }
            return command;
        };
    }
}
