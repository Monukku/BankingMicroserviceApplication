package com.RewaBank.accounts.controller;

import com.RewaBank.accounts.dto.CustomerDetailsDto;
import com.RewaBank.accounts.dto.ErrorResponseDto;
import com.RewaBank.accounts.services.ICustomersService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "Rest APIs for customer details in RewaBank",
        description = "Rest APIs in RewaBank to fetch cutomer details"
)
@RestController
@RequestMapping(path="/api", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
public class CustomerController {

    private static final Logger logger= LoggerFactory.getLogger(CustomerController.class);

    public CustomerController(ICustomersService iCustomersService) {
        this.iCustomersService = iCustomersService;
    }

    private final ICustomersService iCustomersService;

    @Operation(
            summary = "Fetch customer details Rest Api",
            description = "Rest Api to fetch  Customer details inside RewaBank"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "HTTP Status OK"
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
    @GetMapping(value = "/fetchCustomerDetails")
    public ResponseEntity<CustomerDetailsDto> fetchCustomerDetails(@RequestHeader("rewabank-correlation-id")
                                                                       String correlationId,
                                                                       @RequestParam
                                                                       @Pattern(regexp = "(^[0-9]{10}$)",message = "mobile number must be 10 digits")
                                                                       String mobileNumber){

         logger.debug("fetch customer details method start");
         CustomerDetailsDto  customerDetailsDto=iCustomersService.fetchCustomerDetails(correlationId, mobileNumber);
        logger.debug("fetch customer details method end");
        return  ResponseEntity
                .status(HttpStatus.OK)
                .body(customerDetailsDto);
    }
}
