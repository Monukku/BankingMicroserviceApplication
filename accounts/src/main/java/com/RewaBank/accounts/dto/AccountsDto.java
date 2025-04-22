package com.RewaBank.accounts.dto;

import com.RewaBank.accounts.Utility.AccountCategory;
import com.RewaBank.accounts.Utility.AccountStatus;
import com.RewaBank.accounts.Utility.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
//
//import java.math.BigDecimal;
//
//@Getter
//@Setter
//@Schema(
//        name = "Accounts",
//        description = "Schema to hold accounts details"
//)
//public class AccountsDto {
//
//    @Pattern(regexp = "^[0-9]{10}$", message = "Account number must be 10 digits")
//    @NotNull(message = "Account number cannot be null")
//    @Schema(description = "Account number for RewaBank Account")
//    private Long accountNumber;
//
//    @NotEmpty(message = "Branch address cannot be null or empty")
//    @Schema(description = "Branch Address for RewaBank Account")
//    private String branchAddress;
//
//    @NotEmpty(message = "Account type cannot be a null or empty")
//    @Schema(
//            description = "Account Type for RewaBank Account"
//    )
//    @Enumerated(EnumType.STRING)
//    @Column(name = "account_type")
//    private AccountType accountType;
//
//    @NotEmpty(message = "Account status cannot be a null or empty")
//    @Schema(
//            description = "Account status for RewaBank Account"
//    )
//    @Enumerated(EnumType.STRING)
//    @Column(name = "account_status")
//    private AccountStatus accountStatus;
//
//    @NotEmpty(message = "Account category cannot be a null or empty")
//    @Schema(
//            description = "Account category for RewaBank Account"
//    )
//    @Enumerated(EnumType.STRING)
//    @Column(name = "account_category")
//    private AccountCategory accountCategory;
//
//    @NotEmpty(message = "Current balance cannot be a null or empty")
//    @Schema(
//            description = "Current balance of the account"
//    )
//    @Enumerated(EnumType.STRING)
//    @Column(name = "account_category")
//    private BigDecimal balance;
//
////    @NotNull(message = "User ID cannot be null")
//    @Schema(description = "ID of the user associated with this account")
//    private Long userId;
//
//    @Size(max = 500, message = "Account description must be less than 500 characters")
//    @Schema(description = "Optional description for the account")
//    private String accountDescription;
//
//}


@Getter
@Setter
@Schema(name = "Accounts", description = "Schema to hold accounts details")
public class AccountsDto {

    @Pattern(regexp = "^[0-9]{10}$", message = "Account number must be 10 digits")
    @NotNull(message = "Account number cannot be null")
    private Long accountNumber;

    @NotEmpty(message = "Branch address cannot be null or empty")
    private String branchAddress;

    @NotEmpty(message = "Account type cannot be null or empty")
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    @NotEmpty(message = "Account status cannot be null or empty")
    @Enumerated(EnumType.STRING)
    private AccountStatus accountStatus;

    @NotEmpty(message = "Account category cannot be null or empty")
    @Enumerated(EnumType.STRING)
    private AccountCategory accountCategory;

    @NotNull(message = "Balance cannot be null")
    private BigDecimal balance;

    private Long userId;

    @Size(max = 500, message = "Account description must be less than 500 characters")
    private String accountDescription;
}
