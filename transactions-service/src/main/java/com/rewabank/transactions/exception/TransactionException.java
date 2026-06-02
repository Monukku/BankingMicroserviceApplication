package com.rewabank.transactions.exception;

import lombok.Getter;

@Getter
public class TransactionException extends RuntimeException {
    private final String errorCode;
    public TransactionException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
