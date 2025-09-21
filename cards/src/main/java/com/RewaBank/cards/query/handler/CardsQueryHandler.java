package com.RewaBank.cards.query.handler;

import com.RewaBank.cards.dto.CardsDto;
import com.RewaBank.cards.query.FindCardQuery;
import com.RewaBank.cards.service.ICardsService;
import lombok.RequiredArgsConstructor;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CardsQueryHandler {

    private final ICardsService iCardsService;

    @QueryHandler
    public CardsDto fetchCardDetails(FindCardQuery findCardQuery){
       return iCardsService.fetchCard(findCardQuery.getMobileNumber());
    }
}
