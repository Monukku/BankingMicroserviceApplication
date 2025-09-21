package com.rewabank.customer.command.controller;

import com.rewabank.customer.DTO.CustomerDto;
import com.rewabank.customer.DTO.ErrorResponseDto;
import com.rewabank.customer.DTO.ResponseDto;
import com.rewabank.customer.Utility.Role;
import com.rewabank.customer.command.CreateCustomerCommand;
import com.rewabank.customer.command.DeleteCustomerCommand;
import com.rewabank.customer.command.UpdateCustomerCommand;
import com.rewabank.customer.constants.CustomerConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(path="/api", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RefreshScope
@RequiredArgsConstructor
@Slf4j
public class CustomerCommandController {

    private final CommandGateway commandGateway;

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
                .role(Role.CUSTOMER)
                .email(customerDto.getEmail())
        .activeSw(CustomerConstants.ACTIVE_SW)
                .build();
        log.info("Customer create command from CustomerCommandController is started...");
        commandGateway.sendAndWait(createCustomerCommand);
        log.info("Customer create command  from CustomerCommandController is completed...");
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

        DeleteCustomerCommand deleteCustomerCommand= DeleteCustomerCommand.builder()
                        .customerId(customerId)
                                .activeSw(CustomerConstants.IN_ACTIVE_SW).build();
         log.debug("delete account method start");
         commandGateway.sendAndWait(deleteCustomerCommand);
          log.debug("delete account method completed");
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(new ResponseDto(CustomerConstants.STATUS_200,CustomerConstants.MESSAGE_200));

    }
}
