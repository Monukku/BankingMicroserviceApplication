package com.rewabank.customer.command.controller;

import com.rewabank.customer.DTO.CustomerDto;
import com.rewabank.customer.DTO.ErrorResponseDto;
import com.rewabank.customer.DTO.ResponseDto;
import com.rewabank.customer.command.CreateCustomerCommand;
import com.rewabank.customer.command.DeleteCustomerCommand;
import com.rewabank.customer.command.UpdateCustomerCommand;
import com.rewabank.customer.constants.CustomerConstants;
import com.rewabank.customer.services.ICustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(path="/rewabank/customers/api", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
public class CustomerCommandController {

    private final ICustomerService iCustomerService;
    private final CommandGateway commandGateway;
    private static final Logger logger = LoggerFactory.getLogger(CustomerCommandController.class);

    @Operation(
            summary = "create Customer Rest Api",
            description = "Rest Api to create New Customer  inside RewaBank"
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
    @PostMapping("/create")
    public ResponseEntity<ResponseDto> createCustomer(@Valid @RequestBody CustomerDto customerDto){

        CreateCustomerCommand createCustomerCommand=CreateCustomerCommand.builder()
        .customerId(UUID.randomUUID().toString())
        .name(customerDto.getName())
        .mobileNumber(customerDto.getMobileNumber())
        .email(customerDto.getEmail())
        .activeSw(CustomerConstants.ACTIVE_SW).build();


        commandGateway.sendAndWait(createCustomerCommand);
        logger.info("Account created successfully");
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ResponseDto(CustomerConstants.STATUS_201,CustomerConstants.MESSAGE_201));
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
    @PutMapping("/update")
    public ResponseEntity<ResponseDto> updateCustomer(@Valid @RequestBody CustomerDto customerDto){

        UpdateCustomerCommand updateCustomerCommand=UpdateCustomerCommand.builder()
                .customerId(customerDto.getCustomerId())
                .name(customerDto.getName())
                .mobileNumber(customerDto.getMobileNumber())
                .email(customerDto.getEmail())
                .activeSw(CustomerConstants.ACTIVE_SW).build();

        commandGateway.sendAndWait( updateCustomerCommand);
        logger.info("Account updated successfully");
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ResponseDto(CustomerConstants.STATUS_200,CustomerConstants.MESSAGE_200));
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
    public ResponseEntity<ResponseDto> deleteAccount(@RequestParam("customerId")  @Pattern(regexp = "(^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$)",
            message = "mobile number must be 10 digits") String customerId){

        DeleteCustomerCommand deleteCustomerCommand=DeleteCustomerCommand.builder()
                        .customerId(customerId)
                                .activeSw(CustomerConstants.IN_ACTIVE_SW).build();
        logger.debug("delete account method start");
         commandGateway.sendAndWait(deleteCustomerCommand);
          logger.debug("delete account method completed");
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(new ResponseDto(CustomerConstants.STATUS_200,CustomerConstants.MESSAGE_200));

    }
}
