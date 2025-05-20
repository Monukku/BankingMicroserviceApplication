package com.RewaBank.accounts.dto;

import com.RewaBank.accounts.Utility.AccountCategory;
import com.RewaBank.accounts.Utility.AccountStatus;
import com.RewaBank.accounts.Utility.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(name = "Accounts", description = "Schema to hold accounts details")
public class AccountsDto {

    @NotNull(message = "Account number cannot be null")
    @Min(value = 100000000000L, message = "Account number must be 12 digits")
    @Max(value = 999999999999L, message = "Account number must be 12 digits")
    private Long accountNumber;

    @NotBlank(message = "Mobile number cannot be blank")
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits")
    private String mobileNumber;

    @NotNull(message = "Branch address cannot be null or empty")
    private String branchAddress;

    @NotNull(message = "Account type cannot be null or empty")
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    @NotNull(message = "Account status cannot be null or empty")
    @Enumerated(EnumType.STRING)
    private AccountStatus accountStatus;

    @NotNull(message = "Account category cannot be null or empty")
    @Enumerated(EnumType.STRING)
    private AccountCategory accountCategory;

    @NotNull(message = "Balance cannot be null")
    private BigDecimal balance;

    private  boolean activeSw;
}
