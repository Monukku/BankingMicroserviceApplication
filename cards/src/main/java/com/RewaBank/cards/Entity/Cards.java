package com.RewaBank.cards.Entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class Cards extends BaseEntity{

        @Id
        @Column(name = "cardNumber")
        private Long cardNumber;

        @Column(name="mobile_Number")
        private String mobileNumber;

        @Column(name = "card_type")
        private String cardType;

        @Column(name = "total_limit")
        private int totalLimit;

        @Column(name="amount_used")
        private int amountUsed;

        @Column(name="available_Amount")
        private int availableAmount;

        @Column(name = "activeSw")
        private boolean activeSw;

}
