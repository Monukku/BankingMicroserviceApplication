package com.RewaBank.accounts.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(
        name = "customer",
        description = "Schema to hold customer, accounts,loans and cards information"
)
public class CustomerDetailsDto {
    @Schema(
            description = "Name of the Customer"
    )
    @NotEmpty(message = "Name cannot be null or empty")
    @Size(min = 5,max = 30,message = "The length of the customer name should be between 5 and 30")
    private String name;

    @Schema(
            description = "Email of the Customer"
    )
    @NotEmpty(message = "Email Address cannot be a null or empty")
    @Email(message = "Email address should be valid value")
    private   String email;

    @Schema(
            description = "Mobile Number of the Customer"
    )

    @NotEmpty(message = "MobileNumber cannot be a null or empty")
    @Pattern(regexp = "(^[0-9]{10}$)",message = "mobile number must be 10 digits")
    private String mobileNumber;

    @Schema(
            description = "Accounts details of the Customer"
    )
    private  AccountsDto accountsDto;

    @Schema(
            description = "cards details of the Customer"
    )
    private  CardsDto cardsDto;

    @Schema(
            description = "loans details of the Customer"
    )
    private  LoansDto loansDto;
}
