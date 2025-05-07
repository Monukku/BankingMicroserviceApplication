package com.service.verification_service.Dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class VerificationRequestDto {

    private String userId;
    private String code;

    public VerificationRequestDto() {
    }

    public VerificationRequestDto(String userId, String code) {
        this.userId = userId;
        this.code = code;
    }
}