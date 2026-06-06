package com.RewaBank.cards.command.aggregate;

import com.RewaBank.cards.command.CreateCardCommand;
import com.RewaBank.cards.command.DeleteCardCommand;
import com.RewaBank.cards.command.UpdateCardCommand;
import com.RewaBank.cards.command.event.CardCreatedEvent;
import com.RewaBank.cards.command.event.CardDeletedEvent;
import com.RewaBank.cards.command.event.CardUpdatedEvent;
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

    public CardsAggregate(){

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
                     this.mobileNumber =cardCreatedEvent.getMobileNumber();
                     this.amountUsed= cardCreatedEvent.getAmountUsed();
                     this.availableAmount=cardCreatedEvent.getAvailableAmount();
                     this.cardType=cardCreatedEvent.getCardType();
                     this.activeSw=cardCreatedEvent.isActiveSw();
                     this.totalLimit=cardCreatedEvent.getTotalLimit();
    }

    @CommandHandler
    public void handle(UpdateCardCommand updateCardCommand){
        CardUpdatedEvent cardUpdatedEvent=new CardUpdatedEvent();
        BeanUtils.copyProperties(updateCardCommand,cardUpdatedEvent);
        AggregateLifecycle.apply(cardUpdatedEvent);
    }
    @EventSourcingHandler
    public void on(CardUpdatedEvent cardUpdatedEvent){
               this.cardNumber = cardUpdatedEvent.getCardNumber();
               this.mobileNumber=cardUpdatedEvent.getMobileNumber();
               this.cardType = cardUpdatedEvent.getCardType();
               this.totalLimit= cardUpdatedEvent.getTotalLimit();
               this.amountUsed= cardUpdatedEvent.getAmountUsed();
               this.availableAmount= cardUpdatedEvent.getAvailableAmount();
               this.activeSw= cardUpdatedEvent.isActiveSw();
    }

    @CommandHandler
    public void handle(DeleteCardCommand deleteCardCommand){
        CardDeletedEvent cardDeletedEvent=new CardDeletedEvent();
        BeanUtils.copyProperties(deleteCardCommand,cardDeletedEvent);
        AggregateLifecycle.apply(cardDeletedEvent);
    }

    @EventSourcingHandler
    public void on(CardDeletedEvent cardDeletedEvent){
        this.activeSw =cardDeletedEvent.isActiveSw();
    }
}
