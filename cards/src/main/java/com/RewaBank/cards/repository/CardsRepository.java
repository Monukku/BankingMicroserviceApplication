package com.RewaBank.cards.repository;

import com.RewaBank.cards.Entity.Cards;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CardsRepository extends JpaRepository<Cards,Long> {

    Optional<Cards> findByMobileNumber(String mobileNumber);
    Optional<Cards>  findByCardNumber(String cardNumber);

}
