package com.rewabank.accounts.exception;

import lombok.Getter;

@Getter
public class AccountException extends RuntimeException {
    private final String errorCode;
    public AccountException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
