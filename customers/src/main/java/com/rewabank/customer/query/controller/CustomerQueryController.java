package com.rewabank.customer.query.controller;


import com.rewabank.customer.DTO.CustomerDto;
import com.rewabank.customer.DTO.ErrorResponseDto;
import com.rewabank.customer.query.FindCustomerQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path="/api", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
public class CustomerQueryController {

    private static final Logger logger = LoggerFactory.getLogger(CustomerQueryController.class);

    private final QueryGateway queryGateway;
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
    @GetMapping("/fetch")
    public ResponseEntity<CustomerDto> fetchCustomerDetails(@RequestParam("mobileNumber")
                                                            @Pattern(regexp = "(^$|[0-9]{10})", message = "Mobile number must be 10 digits")
                                                            String mobileNumber) {
        FindCustomerQuery findCustomerQuery = new FindCustomerQuery(mobileNumber);
        CustomerDto customerDto = queryGateway.query(findCustomerQuery, ResponseTypes.instanceOf(CustomerDto.class)).join();
        return ResponseEntity.status(HttpStatus.OK).body(customerDto);
    }

}
