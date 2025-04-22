package com.RewaBank.accounts.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

//
//@Data
//@Schema(
//        name = "customer",
//        description = "Schema to hold customer and accounts details"
//)
//public class CustomerDto {
//
//    @Schema(
//            description = "Name of the Customer"
//    )
//    @NotEmpty(message = "Name cannot be null or empty")
//    @Size(min = 5,max = 30,message = "The length of the customer name should be between 5 and 30")
//    private String name;
//
//    @Schema(
//            description = "Email of the Customer"
//    )
//    @NotEmpty(message = "Email Address cannot be a null or empty")
//    @Email(message = "Email address should be valid value")
//    private   String email;
//
//    @Schema(
//            description = "Mobile Number of the Customer"
//    )
//
//    @NotEmpty(message = "MobileNumber cannot be a null or empty")
//    @Pattern(regexp = "(^[0-9]{10}$)",message = "mobile number must be 10 digits")
//    private String mobileNumber;
//
//    @Schema(
//            description = "Accounts details of the Customer"
//    )
//    private  AccountsDto accountsDto;
//
//}




@Getter
@Setter
@Schema(name = "Customer", description = "Schema to hold customer and account details")
public class CustomerDto {

    @NotEmpty(message = "Name cannot be null or empty")
    @Size(min = 5, max = 30, message = "The length of the customer name should be between 5 and 30")
    private String name;

    @NotEmpty(message = "Email Address cannot be null or empty")
    @Email(message = "Email address should be valid")
    private String email;

    @NotEmpty(message = "MobileNumber cannot be null or empty")
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits")
    private String mobileNumber;

    private AccountsDto accountsDto;
}
