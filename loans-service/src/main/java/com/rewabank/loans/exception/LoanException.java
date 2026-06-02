package com.rewabank.loans.exception;

import lombok.Getter;

@Getter
public class LoanException extends RuntimeException {
    private final String errorCode;
    public LoanException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}