package com.rewabank.cards.service;

import com.rewabank.cards.dto.CardsDto;

public interface ICardsService {
    boolean deleteCard(String mobileNumber);

    boolean updateCard(CardsDto cardsDto);

    CardsDto fetchCard(String mobileNumber);

    void createCard(String mobileNumber);
}
