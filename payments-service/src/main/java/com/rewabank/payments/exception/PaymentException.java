package com.rewabank.payments.exception;

import lombok.Getter;

@Getter
public class PaymentException extends RuntimeException {

    private final String errorCode;

    public PaymentException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}