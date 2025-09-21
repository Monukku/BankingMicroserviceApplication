package com.RewaBank.loans.query.controller;

import com.RewaBank.loans.dto.ErrorResponseDto;
import com.RewaBank.loans.dto.LoansDto;
import com.RewaBank.loans.query.FindLoansQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
public class LoansQueryController {

     private  final QueryGateway queryGateway;

    @Operation(
            summary = "Fetch Loans Details REST API",
            description = "REST API to fetch Loans details based on a mobile number"
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
    })
    @GetMapping("/fetch")
    public ResponseEntity<LoansDto> fetchLoansDetails(@RequestParam("mobileNumber")
                                                      @Pattern(regexp="(^|[0-9]{10}$)",message = "Mobile number must be 10 digits")
                                                      String mobileNumber) {
        log.debug("fetch loans details method starts");
        FindLoansQuery findLoansQuery=new FindLoansQuery(mobileNumber);
               LoansDto loansDto = queryGateway.query(findLoansQuery, ResponseTypes.instanceOf(LoansDto.class)).join();
        log.debug("fetch loans details method ends");
        return   ResponseEntity
                .status(HttpStatus.OK)
                .body(loansDto);
    }
}
