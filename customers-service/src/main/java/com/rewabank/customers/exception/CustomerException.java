package com.rewabank.customers.exception;

public class CustomerException extends RuntimeException {
    private final String errorCode;

    public CustomerException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}