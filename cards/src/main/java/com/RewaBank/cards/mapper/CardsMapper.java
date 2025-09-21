package com.RewaBank.cards.mapper;

import com.RewaBank.cards.Entity.Cards;
import com.RewaBank.cards.command.event.CardUpdatedEvent;
import com.RewaBank.cards.dto.CardsDto;

public class CardsMapper {
    public static Cards mapToCard(CardsDto cardsDto, Cards cards){

        cards.setMobileNumber(cardsDto.getMobileNumber());
        cards.setCardNumber(cardsDto.getCardNumber());
        cards.setCardType(cardsDto.getCardType());
        cards.setTotalLimit(cardsDto.getTotalLimit());
        cards.setAmountUsed(cardsDto.getAmountUsed());
        cards.setAvailableAmount(cardsDto.getAvailableAmount());

        return cards;
    }
    public static CardsDto mapToCardsDto(Cards cards, CardsDto cardsDto){

        cardsDto.setMobileNumber(cards.getMobileNumber());
        cardsDto.setCardNumber(cards.getCardNumber());
        cardsDto.setCardType(cards.getCardType());
        cardsDto.setTotalLimit(cards.getTotalLimit());
        cardsDto.setAmountUsed(cards.getAmountUsed());
        cardsDto.setAvailableAmount(cards.getAvailableAmount());

        return cardsDto;

    }

    public static Cards mapEventToCard(CardUpdatedEvent event,Cards card){
               card.setCardType(event.getCardType());
               card.setAmountUsed(event.getAmountUsed());
               card.setTotalLimit(event.getTotalLimit());
               card.setAvailableAmount(event.getAvailableAmount());
               return card;
    }

}
