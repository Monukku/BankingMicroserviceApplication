package com.RewaBank.accounts.command.controller;

import com.RewaBank.accounts.Utility.AccountCategory;
import com.RewaBank.accounts.Utility.AccountStatus;
import com.RewaBank.accounts.Utility.AccountType;
import com.RewaBank.accounts.command.CreateAccountCommand;
import com.RewaBank.accounts.command.DeleteAccountCommand;
import com.RewaBank.accounts.command.UpdateAccountCommand;
import com.RewaBank.accounts.constants.AccountsConstants;
import com.RewaBank.accounts.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.Random;

@Tag(
        name = "CRUD APIs for Accounts in RewaBank",
        description = "CRUD APIs in RewaBank for CREATE,READ,UPDATE,DELETE"
)
@RestController
@RequestMapping(path="/api", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RefreshScope
@RequiredArgsConstructor
@Slf4j
public class AccountsCommandController{

    private final CommandGateway commandGateway;

    @Autowired
    private Environment environment;

    @Autowired
    private AccountsContactInfoDto accountsContactInfoDto;

    @Value("${build.version}")
    private String buildVersion;

    @Operation(
            summary = "create Accounts Rest Api",
            description = "Rest Api to create New Customer And Accounts inside RewaBank"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "HTTP Status CREATED"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "HTTP Status INTERNAL_SERVER_ERROR",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponseDto.class)
                    )
            )
    }
    )
    @PostMapping("/create/{accountType}")
    public ResponseEntity<ResponseDto> createaAccount(@Valid @RequestParam("mobileNumber")
                                                          @Pattern(regexp = "^\\d{10}$", message = "Mobile number must be exactly 10 digits") String mobileNumber, @PathVariable("accountType") AccountType accountType){
               Random random = new Random();
               long randomNumber = 100000000000L + (long)(random.nextDouble() * 900000000000L);

               CreateAccountCommand   createAccountCommand=CreateAccountCommand.builder()
                .accountNumber(randomNumber)
                .mobileNumber(mobileNumber)
                .branchAddress(AccountsConstants.ADDRESS)
                .accountType(accountType)
                .activeSw(AccountsConstants.ACTIVE_SW)
                .balance(AccountsConstants.BALANCE)
                                        .accountStatus(AccountStatus.ACTIVE)
                                                .accountCategory(AccountCategory.DEFAULT).build();
        commandGateway.sendAndWait(createAccountCommand);
        log.info("Account create command API call from AccountCommandController is completed.");
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ResponseDto(AccountsConstants.STATUS_201,AccountsConstants.MESSAGE_201));
    }



    @Operation(
            summary = "Update Accounts details Rest Api",
            description = "Rest Api to update  Customer & Accounts details based on account number"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "HTTP Status OK"
            ),
            @ApiResponse(
                    responseCode = "417",
                    description = "Exception Failed"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "HTTP Status INTERNAL_SERVER_ERROR",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponseDto.class)
                    )
            )
    }
    )
    @PutMapping("/updateAccount")
    public ResponseEntity<ResponseDto> updateAccountsDetails(@Valid @RequestBody AccountsDto accountsDto) {

        UpdateAccountCommand updateAccountCommand= UpdateAccountCommand.builder()
                .accountNumber(accountsDto.getAccountNumber())
                .mobileNumber(accountsDto.getMobileNumber())
                .branchAddress(accountsDto.getBranchAddress())
                .accountType(accountsDto.getAccountType())
                .activeSw(AccountsConstants.ACTIVE_SW)
                .balance(AccountsConstants.BALANCE)
                .accountStatus(AccountStatus.ACTIVE)
                .accountCategory(AccountCategory.DEFAULT).build();

        commandGateway.sendAndWait(updateAccountCommand);
        log.info("Account update command from AccountCommandController is sent.");
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(new ResponseDto(AccountsConstants.STATUS_200, AccountsConstants.MESSAGE_200));
    }
    @Operation(
            summary = "Delete Accounts details Rest Api",
            description = "Rest Api to delete  Customer & Accounts details based on account number"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "HTTP Status OK"
            ),
            @ApiResponse(
                    responseCode = "417",
                    description = "Exception Failed"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "HTTP Status INTERNAL_SERVER_ERROR",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponseDto.class)
                    )
            )
    }
    )
    @PatchMapping("/delete")
    public ResponseEntity<ResponseDto> deleteAccount(@RequestParam("accountNumber")   @Min(value = 100000000000L, message = "Account number must be 12 digits")
                                                         @Max(value = 999999999999L, message = "Account number must be 12 digits")
                                                         Long accountNumber){
        log.debug("delete account method start");
        DeleteAccountCommand deleteAccountCommand= DeleteAccountCommand.builder()
                .accountNumber(accountNumber)
                .activeSw(AccountsConstants.IN_ACTIVE_SW).build();
        commandGateway.sendAndWait(deleteAccountCommand);
        log.info("Account delete command from AccountCommandController is sent.");
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(new ResponseDto(AccountsConstants.STATUS_200,AccountsConstants.MESSAGE_200));
        }
    }

//    @Operation(
//            summary = "Get Build information",
//            description = "Get Build information that is deployed into accounts microservice"
//    )
//    @ApiResponses({
//            @ApiResponse(
//                    responseCode = "200",
//                    description = "HTTP Status OK"
//            ),
//            @ApiResponse(
//                    responseCode = "500",
//                    description = "HTTP Status Internal Server Error",
//                    content = @Content(
//                            schema = @Schema(implementation = ErrorResponseDto.class)
//                    )
//            )
//    }
//    )

//    @Retry(name = "getBuildInfo",fallbackMethod = "getBuildInfoFallback")
//    @GetMapping("/build-info")
//    public ResponseEntity<String> getBuildInfo() {
//        log.info .debug("getBuildInfo method invoked");
//        throw new NullPointerException();
////        return ResponseEntity
//                .status(HttpStatus.OK)
//                .body(buildVersion);
//    }

//    public ResponseEntity<String> getBuildInfoFallback(Throwable throwable){
//        logger.debug("getBuildInfoFallback method invoked");
//        return  ResponseEntity
//                .status(HttpStatus.OK)
//                .body("0.9");
//    }
//
//    @Operation(
//            summary = "Get Java version",
//            description = "Get Java versions details that is installed into accounts microservice"
//    )
//    @ApiResponses({
//            @ApiResponse(
//                    responseCode = "200",
//                    description = "HTTP Status OK"
//            ),
//            @ApiResponse(
//                    responseCode = "500",
//                    description = "HTTP Status Internal Server Error",
//                    content = @Content(
//                            schema = @Schema(implementation = ErrorResponseDto.class)
//                    )
//            )
//    }
//    )
//    @RateLimiter(name = "getJavaVersion",fallbackMethod = "getJavaVersionFallback")
//    @GetMapping("/java-version")
//    public ResponseEntity<String> getJavaVersion() {
//        return ResponseEntity
//                .status(HttpStatus.OK)
//                .body(environment.getProperty("JAVA_HOME"));
//    }
//
//    public ResponseEntity<String> getJavaVersionFallback(Throwable throwable){
//        return ResponseEntity
//                .status(HttpStatus.OK)
//                .body("Java 17");
//    }

//    @Operation(
//            summary = "Get Contact Info",
//            description = "Contact Info details that can be reached out in case of any issues"
//    )
//    @ApiResponses({
//            @ApiResponse(
//                    responseCode = "200",
//                    description = "HTTP Status OK"
//            ),
//            @ApiResponse(
//                    responseCode = "500",
//                    description = "HTTP Status Internal Server Error",
//                    content = @Content(
//                            schema = @Schema(implementation = ErrorResponseDto.class)
//                    )
//            )
//    }
//    )
//    @GetMapping("/contact-info")
//    public ResponseEntity<AccountsContactInfoDto> getContactInfo() {
//        return ResponseEntity
//                .status(HttpStatus.OK)
//                .body(accountsContactInfoDto);
//    }

