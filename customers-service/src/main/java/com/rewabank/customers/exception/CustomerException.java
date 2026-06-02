package com.rewabank.customers.exception;

import lombok.Getter;

@Getter
public class CustomerException extends RuntimeException {
    private final String errorCode;
    public CustomerException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
