package com.RewaBank.cards.command.intercepter;

import com.RewaBank.cards.Entity.Cards;
import com.RewaBank.cards.command.CreateCardCommand;
import com.RewaBank.cards.command.DeleteCardCommand;
import com.RewaBank.cards.command.UpdateCardCommand;
import com.RewaBank.cards.constants.CardsConstants;
import com.RewaBank.cards.exception.CardAlreadyExistsException;
import com.RewaBank.cards.repository.CardsRepository;
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
public class CardsCommandInterceptor implements MessageDispatchInterceptor<CommandMessage<?>> {

    private final CardsRepository cardsRepository;

    @Nonnull
    @Override
    public BiFunction<Integer, CommandMessage<?>, CommandMessage<?>> handle(@Nonnull List<? extends CommandMessage<?>> messages) {
        return (index,command) -> {
            if(CreateCardCommand.class.equals(command.getPayload())) {
                CreateCardCommand createCardCommand = (CreateCardCommand) command.getPayload();
            Optional<Cards> optionalCards = cardsRepository.findByMobileNumberAndActiveSw(createCardCommand.getMobileNumber(), true);
                if (optionalCards.isPresent()) {
                    throw new CardAlreadyExistsException("Card already created with given mobileNumber :"
                            + createCardCommand.getMobileNumber());
                }
            }else if (UpdateCardCommand.class.equals(command.getPayload())){
              UpdateCardCommand updateCardCommand=(UpdateCardCommand) command.getPayload();
              Optional<Cards> optionalCards = cardsRepository.findByMobileNumberAndActiveSw(updateCardCommand.getMobileNumber(),true);
                     if (optionalCards.isPresent()){
                          throw new CardAlreadyExistsException("Card already created with given mobileNumber :"
                                  + updateCardCommand.getMobileNumber());
                     }
                    } else if (DeleteCardCommand.class.equals(command.getPayload())) {
                        DeleteCardCommand deleteCardCommand=(DeleteCardCommand) command.getPayload();
                      Optional<Cards> optionalCards =  cardsRepository.findByCardNumberAndActiveSw(deleteCardCommand.getCardNumber(), CardsConstants.ACTIVE_SW);
                      if (optionalCards.isPresent()){
                          throw new CardAlreadyExistsException("Card already created with given mobileNumber :"
                                  + deleteCardCommand.getCardNumber());
                      }
                 }
            return command;
        };
    }
}
