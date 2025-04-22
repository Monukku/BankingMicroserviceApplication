package com.RewaBank.cards.service;

import com.RewaBank.cards.dto.CardsDto;

public interface ICardsService {
    boolean deleteCard(String mobileNumber);

    boolean updateCard(CardsDto cardsDto);

    CardsDto fetchCard(String mobileNumber);

    void createCard(String mobileNumber);
}
