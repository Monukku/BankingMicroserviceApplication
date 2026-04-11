package com.rewabank.fraud.exception;

import lombok.Getter;

@Getter
public class FraudException extends RuntimeException {
    private final String errorCode;
    public FraudException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
