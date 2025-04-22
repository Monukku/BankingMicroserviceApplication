package com.RewaBank.cards.service.ServiceImpl;

import com.RewaBank.cards.Entity.Cards;
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
import java.util.Random;

@Service
@AllArgsConstructor
public class CardsServiceImpl implements ICardsService{

    private CardsRepository cardsRepository;


    @Override
    public void createCard(String mobileNumber) {
          Optional<Cards> cards=cardsRepository.findByMobileNumber(mobileNumber);
          if(cards.isPresent()){
              throw new CardAlreadyExistsException("card with the registered mobile number already exist");
          }else{
              cardsRepository.save(createNewCard(mobileNumber));
          }

    }

    private static Cards createNewCard(String mobileNumber){
          Cards card=new Cards();

          long randomCardNumber = 100000000000L + new Random().nextInt(900000000);

          card.setCardNumber(Long.toString(randomCardNumber));
          card.setMobileNumber(mobileNumber);
          card.setCardType(CardsConstants.CREDIT_CARD);
          card.setAvailableAmount(CardsConstants.NEW_CARD_LIMIT);
          card.setAmountUsed(0);
          card.setTotalLimit(CardsConstants.NEW_CARD_LIMIT);
          System.out.println(card);


         return card;
    }

    @Override
    public CardsDto fetchCard(String mobileNumber) {

           Cards card=cardsRepository.findByMobileNumber(mobileNumber).orElseThrow(
                   ()-> new ResourceNotFoundException("cards","mobileNumber",mobileNumber)
           );

        return CardsMapper.mapToCardsDto(card,new CardsDto());
    }


    @Override
    public boolean updateCard(CardsDto cardsDto) {
            boolean isUpdated=false;
            if(!isUpdated) {
                Cards cards = cardsRepository.findByCardNumber(cardsDto.getCardNumber()).orElseThrow(
                        () -> new ResourceNotFoundException("Cards", "CardNumber", cardsDto.getCardNumber())
                );

//                cards.setMobileNumber(cardsDto.getMobileNumber());
//                cards.setCardNumber(cardsDto.getCardNumber());
//                cards.setCardType(cardsDto.getCardType());
//                cards.setAvailableAmount(cardsDto.getAvailableAmount());
//                cards.setAmountUsed(cardsDto.getAmountUsed());
//                cards.setTotalLimit(cardsDto.getTotalLimit());

//All set details replaced with mapToCard in single line.
               CardsMapper.mapToCard(cardsDto,cards);

                cardsRepository.save(cards);

                isUpdated = true;
            }

        return isUpdated;
    }
    @Override
    public boolean deleteCard(String mobileNumber) {
        boolean isDeleted=false;
        if(!isDeleted) {
            Cards card = cardsRepository.findByMobileNumber(mobileNumber).orElseThrow(
                    () -> new ResourceNotFoundException("Card", "mobile number", mobileNumber)
            );

            cardsRepository.delete(card);

            isDeleted =true;
        }

        return isDeleted;
    }





}
