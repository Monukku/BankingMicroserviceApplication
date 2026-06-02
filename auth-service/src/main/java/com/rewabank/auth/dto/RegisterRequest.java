package com.rewabank.auth.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(

        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be 2-100 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = "^\\+91-?[0-9]{10}$", message = "Invalid Indian mobile number")
        String mobileNumber,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 64, message = "Password must be 8-64 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
                message = "Password must have uppercase, lowercase, digit, and special character"
        )
        String password
) {}
