package com.RewaBank.cards.query.projection;

import com.RewaBank.cards.Entity.Cards;
import com.RewaBank.cards.command.event.CardCreatedEvent;
import com.RewaBank.cards.command.event.CardDeletedEvent;
import com.RewaBank.cards.command.event.CardUpdatedEvent;
import com.RewaBank.cards.service.ICardsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ProcessingGroup("card-group")
public class CardsProjection {
    private final ICardsService iCardsService;

    @EventHandler
    public void on(CardCreatedEvent cardCreatedEvent){
        Cards cardsEntity=new Cards();
        BeanUtils.copyProperties(cardCreatedEvent,cardsEntity);
        iCardsService.createCard(cardsEntity);
        log.info("CardCreatedEvent from AccountProject EventHandler");
    }

    @EventHandler
    public void on(CardUpdatedEvent event){
        iCardsService.updateCard(event);
    }

    @EventHandler
    public void on(CardDeletedEvent event){
        iCardsService.deleteCard(event.getCardNumber());
    }
}
