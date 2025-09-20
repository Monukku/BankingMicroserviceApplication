package com.RewaBank.cards.command.aggregate;

import com.RewaBank.cards.command.CreateCardCommand;
import com.RewaBank.cards.command.event.CardCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;
import org.springframework.beans.BeanUtils;

@Aggregate
@Slf4j
public class CardsAggregate {

    @AggregateIdentifier
    private Long cardNumber;

    private String mobileNumber;

    private String cardType;

    private int totalLimit;

    private int amountUsed;

    private int availableAmount;

    private boolean activeSw;

    public CardsAggregate() {

    }

    @CommandHandler
    public CardsAggregate(CreateCardCommand createCardCommand){
        CardCreatedEvent cardCreatedEvent=new CardCreatedEvent();
        BeanUtils.copyProperties(createCardCommand,cardCreatedEvent);
        AggregateLifecycle.apply(cardCreatedEvent);
        log.info(" 'CreateCardCommand' Cards aggregate commandHandler completed");
    }

    @EventSourcingHandler
    public void on(CardCreatedEvent cardCreatedEvent){
                     this.cardNumber =cardCreatedEvent.getCardNumber();

    }
}
