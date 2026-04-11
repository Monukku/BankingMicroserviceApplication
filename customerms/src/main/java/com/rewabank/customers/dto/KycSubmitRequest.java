package com.rewabank.customers.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record KycSubmitRequest(

        @NotBlank(message = "Aadhaar number is required")
        @Pattern(regexp = "^[2-9]{1}[0-9]{11}$", message = "Invalid Aadhaar number format")
        String aadhaarNumber,

        @NotBlank(message = "PAN number is required")
        @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]{1}$", message = "Invalid PAN number format")
        String panNumber,

        @NotNull(message = "Date of birth is required")
        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        @NotNull(message = "Address is required")
        @Valid
        AddressRequest address
) {}
