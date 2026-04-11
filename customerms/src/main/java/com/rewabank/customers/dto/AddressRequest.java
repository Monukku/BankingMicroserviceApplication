package com.rewabank.customers.dto;

import jakarta.validation.constraints.*;

public record AddressRequest(

        @NotBlank(message = "Address line 1 is required")
        String addressLine1,

        String addressLine2,

        @NotBlank(message = "City is required")
        String city,

        @NotBlank(message = "State is required")
        String state,

        @NotBlank(message = "Pincode is required")
        @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Invalid Indian pincode")
        String pincode
) {}
