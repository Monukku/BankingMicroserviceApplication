package com.rewabank.accounts.dto;

public record AccountsMessageDto(
       Long accountId, Long accountNumber, String name, String email, String mobileNumber
){}
