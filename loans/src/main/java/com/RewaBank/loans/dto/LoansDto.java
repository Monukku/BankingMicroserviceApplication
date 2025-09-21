package com.RewaBank.loans.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Schema(
        name = "Loans",
        description = "Loans Microservice for RewaBank"
)
public class LoansDto {

    @Schema(

            description = "Mobile number for getting Loans"
    )
    @Pattern(regexp = "(^[0-9]{10}$)",message = "mobile Number must be 10 digits")
    @NotEmpty(message = "mobileNumber should not be empty or null")
    private String mobileNumber;

    @Schema(
            description = "Loan number for the Loans"
    )
    @NotEmpty(message = "loanNumber should not be empty or null")
    private Long loanNumber;

    @Schema(
            description = "Type of the Loan"
    )
    @NotEmpty(message = "loan Type should not be empty or null")
    private String loanType;


    @PositiveOrZero(message = "Total loan amount paid should be equal or greater than zero")
    @Schema(
            description = "Total loan amount paid", example = "1000"
    )
    private int totalLoan;


    @PositiveOrZero(message = "Total loan amount paid should be equal or greater than zero")
    @Schema(
            description = "Total loan amount paid", example = "1000"
    )
    private int  amountPaid;

    @PositiveOrZero(message = "Total outstanding amount should be equal or greater than zero")
    @Schema(
            description = "Total outstanding amount against a loan", example = "99000"
    )
    private  int outstandingAmount;

}
