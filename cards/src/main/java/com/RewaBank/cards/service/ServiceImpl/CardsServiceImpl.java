package com.RewaBank.cards.service.ServiceImpl;

import com.RewaBank.cards.Entity.Cards;
import com.RewaBank.cards.command.event.CardUpdatedEvent;
import com.RewaBank.cards.constants.CardsConstants;
import com.RewaBank.cards.dto.CardsDto;
import com.RewaBank.cards.exception.CardAlreadyExistsException;
import com.RewaBank.cards.exception.ResourceNotFoundException;
import com.RewaBank.cards.mapper.CardsMapper;
import com.RewaBank.cards.repository.CardsRepository;
import com.RewaBank.cards.service.ICardsService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
@AllArgsConstructor
public class CardsServiceImpl implements ICardsService {

    private CardsRepository cardsRepository;


    @Override
    public void createCard(Cards cards) {
        Optional<Cards> card = cardsRepository.findByMobileNumberAndActiveSw(cards.getMobileNumber(), CardsConstants.ACTIVE_SW);
        if (card.isPresent()) {
            throw new CardAlreadyExistsException("card with the registered mobile number already exist");
        }
        cardsRepository.save(cards);
    }

    @Override
    public CardsDto fetchCard(String mobileNumber) {
        Cards card = cardsRepository.findByMobileNumberAndActiveSw(mobileNumber, CardsConstants.ACTIVE_SW).orElseThrow(
                () -> new ResourceNotFoundException("cards", "mobileNumber", mobileNumber)
        );
        return CardsMapper.mapToCardsDto(card, new CardsDto());
    }

    @Override
    public boolean updateCard(CardUpdatedEvent event) {
            Cards existingCard = cardsRepository.findByCardNumberAndActiveSw(event.getCardNumber(), event.isActiveSw())
    .orElseThrow(() -> new ResourceNotFoundException("Cards", "CardNumber", event.getCardNumber().toString()));

//                cards.setCardType(cardsDto.getCardType());
//                cards.setAvailableAmount(cardsDto.getAvailableAmount());
//                cards.setAmountUsed(cardsDto.getAmountUsed());
//                cards.setTotalLimit(cardsDto.getTotalLimit());

//All set details replaced with mapToCard in single line.
            CardsMapper.mapEventToCard(event, existingCard);
            cardsRepository.save(existingCard);
            return true;
    }

    @Override
    public boolean deleteCard(Long cardNumber) {

        Cards existingCard = cardsRepository.findByCardNumberAndActiveSw(cardNumber, CardsConstants.ACTIVE_SW).orElseThrow(
                () -> new ResourceNotFoundException("Card", "mobile number", cardNumber.toString())
        );

        if (existingCard != null) {
            existingCard.setActiveSw(CardsConstants.IN_ACTIVE_SW);
            cardsRepository.save(existingCard);
        }
        return true;
    }
}
