package com.RewaBank.loans.command.controller;

import com.RewaBank.loans.command.CreateLoanCommand;
import com.RewaBank.loans.command.DeleteLoanCommand;
import com.RewaBank.loans.command.UpdateLoanCommand;
import com.RewaBank.loans.constants.LoansConstants;
import com.RewaBank.loans.dto.ErrorResponseDto;
import com.RewaBank.loans.dto.LoansContactInfoDto;
import com.RewaBank.loans.dto.LoansDto;
import com.RewaBank.loans.dto.ResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
        name = "CRUD REST APIs for Loans in RewaBank",
        description = "CRUD REST APIs in RewaBank to CREATE, UPDATE, FETCH AND DELETE Loans details"
)
@RefreshScope
@RestController
@RequestMapping(path = "/api",produces = {MediaType.APPLICATION_JSON_VALUE})
@Validated
@Slf4j
@RequiredArgsConstructor
public class LoansCommandController {

    private final CommandGateway commandGateway;

    @Autowired
    private Environment environment;
    @Autowired
    private LoansContactInfoDto loansContactInfoDto;
    @Autowired
    @Value("${build.version}")
    private String buildVersion;

        @Operation(
                summary = "Create Loans REST API",
                description = "REST API to create new Loans inside RewaBank"
        )
        @ApiResponses({
                @ApiResponse(
                        responseCode = "201",
                        description = "HTTP Status CREATED"
                ),
                @ApiResponse(
                        responseCode = "500",
                        description = "HTTP Status Internal Server Error",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponseDto.class)
                        )
                )
        }
        )
        @PostMapping("/create")
        public ResponseEntity<ResponseDto> createLoans(@Valid @RequestParam(name = "mobileNumber")
                                                      @Pattern(regexp="(^|[0-9]{10}$)",message = "Mobile number must be 10 digits")
                                                      String mobileNumber) {
            long randomLoanNumber = 1000000000L + new Random().nextInt(900000000);
            CreateLoanCommand createLoanCommand=CreateLoanCommand.builder()
                    .loanNumber(randomLoanNumber).mobileNumber(mobileNumber)
                    .loanType(LoansConstants.HOME_LOAN).totalLoan(LoansConstants.NEW_LOAN_LIMIT)
                    .amountPaid(0).outstandingAmount(LoansConstants.NEW_LOAN_LIMIT)
                    .activeSw(LoansConstants.ACTIVE_SW)
                    .build();
            commandGateway.sendAndWait(createLoanCommand);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(new ResponseDto(LoansConstants.STATUS_201, LoansConstants.MESSAGE_201));
        }

        @Operation(
                summary = "Update loans Details REST API",
                description = "REST API to update loans details based on a LoanNumber"
        )
        @ApiResponses({
                @ApiResponse(
                        responseCode = "200",
                        description = "HTTP Status OK"
                ),
                @ApiResponse(
                        responseCode = "417",
                        description = "Expectation Failed"
                ),
                @ApiResponse(
                        responseCode = "500",
                        description = "HTTP Status Internal Server Error",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponseDto.class)
                        )
                )
        })
        @PutMapping("/update")
        public ResponseEntity<ResponseDto> updateLoansDetails(@Valid @RequestBody LoansDto loansDto) {

            UpdateLoanCommand updateLoanCommand=UpdateLoanCommand.builder()
                    .loanNumber(loansDto.getLoanNumber()).mobileNumber(loansDto.getMobileNumber())
                    .loanType(LoansConstants.HOME_LOAN).totalLoan(loansDto.getTotalLoan())
                    .outstandingAmount(loansDto.getOutstandingAmount()).amountPaid(loansDto.getAmountPaid())
                    .activeSw(LoansConstants.ACTIVE_SW)
                    .build();
                commandGateway.sendAndWait(updateLoanCommand);
                return ResponseEntity
                        .status(HttpStatus.OK)
                        .body(new ResponseDto(LoansConstants.STATUS_200, LoansConstants.MESSAGE_200));
        }

        @Operation(
                summary = "Delete Loans Details REST API",
                description = "REST API to delete Loans details based on a mobile number"
        )
        @ApiResponses({
                @ApiResponse(
                        responseCode = "200",
                        description = "HTTP Status OK"
                ),
                @ApiResponse(
                        responseCode = "417",
                        description = "Expectation Failed"
                ),
                @ApiResponse(
                        responseCode = "500",
                        description = "HTTP Status Internal Server Error",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponseDto.class)
                        )
                )
        })
        @PatchMapping("/delete")
        public ResponseEntity<ResponseDto> deleteLoansDetails(@RequestParam
                                                             @Pattern(regexp="(^|[0-9]{10}$)",message = "Mobile number must be 10 digits")
                                                             Long loansNumber) {
            DeleteLoanCommand deleteLoanCommand=DeleteLoanCommand.builder()
                    .loanNumber(loansNumber).activeSw(LoansConstants.IN_ACTIVE_SW)
                    .build();
                commandGateway.sendAndWait(deleteLoanCommand);
                return ResponseEntity
                        .status(HttpStatus.OK)
                        .body(new ResponseDto(LoansConstants.STATUS_200, LoansConstants.MESSAGE_200));
        }



    @Operation(
            summary = "Get Java version",
            description = "Get Java versions details that is installed into cards microservice"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "HTTP Status OK"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "HTTP Status Internal Server Error",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponseDto.class)
                    )
            )
    }
    )
    @GetMapping("/java-version")
    public ResponseEntity<String> getJavaVersion() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(environment.getProperty("JAVA_HOME"));
    }

    @Operation(
            summary = "Get Contact Info",
            description = "Contact Info details that can be reached out in case of any issues"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "HTTP Status OK"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "HTTP Status Internal Server Error",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponseDto.class)
                    )
            )
    }
    )
    @GetMapping("/contact-info")
    public ResponseEntity<LoansContactInfoDto> getContactInfo() {
            log.debug("invoked loans contact-info api");
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(loansContactInfoDto);
    }


    @Operation(
            summary = "Get Build information",
            description = "Get Build information that is deployed into cards microservice"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "HTTP Status OK"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "HTTP Status Internal Server Error",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponseDto.class)
                    )
            )
    }
    )

    @GetMapping("/build-version")
    public String getBuildVersion() {
        return "Build Version: " + buildVersion;
    }


    }

