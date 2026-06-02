package com.rewabank.cards.exception;

import lombok.Getter;

@Getter
public class CardException extends RuntimeException {
    private final String errorCode;
    public CardException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}