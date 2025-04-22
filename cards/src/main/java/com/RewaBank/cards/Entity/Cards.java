package com.RewaBank.cards.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Cards extends BaseEntity{

        @Id
        @GeneratedValue(strategy = GenerationType.AUTO,generator = "native")
        @GenericGenerator(name = "native",strategy = "native")
        @Column(name = "card_Id")
        private Long cardId;

        @Column(name="mobile_Number")
        private String mobileNumber;

        @Column(name = "card_number")
        private String cardNumber;

        @Column(name = "card_type")
        private String cardType;

        @Column(name = "total_limit")
        private int totalLimit;

        @Column(name="amount_used")
        private int amountUsed;

        @Column(name="available_Amount")
        private int availableAmount;

}
