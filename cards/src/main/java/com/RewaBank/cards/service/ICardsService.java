package com.RewaBank.cards.service;

import com.RewaBank.cards.Entity.Cards;
import com.RewaBank.cards.command.event.CardUpdatedEvent;
import com.RewaBank.cards.dto.CardsDto;

public interface ICardsService {
    boolean deleteCard(Long cardNumber);

    boolean updateCard(CardUpdatedEvent cardUpdatedEvent);

    CardsDto fetchCard(String mobileNumber);

    void createCard(Cards card);
}
