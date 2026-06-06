package com.RewaBank.accounts.query.controller;

import com.RewaBank.accounts.dto.AccountsDto;
import com.RewaBank.accounts.dto.ErrorResponseDto;
import com.RewaBank.accounts.query.FindAccountQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Query APIs for Accounts in RewaBank",
        description = "Query APIs in RewaBank for CREATE,READ,UPDATE,DELETE"
)
@RestController
@RequestMapping(path="/api", produces = {MediaType.APPLICATION_JSON_VALUE})
@Validated
@RefreshScope
@RequiredArgsConstructor
public class AccountsQueryController {

    private final QueryGateway queryGateway;

    @Operation(
            summary = "Fetch Accounts details Rest Api",
            description = "Rest Api to fetch  Customer & Accounts inside RewaBank"
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
    public ResponseEntity<AccountsDto> fetchAccountDetails(
            @RequestParam("mobileNumber") @Pattern(regexp = "(^[0-9]{10}$)",
            message = "Mobile number must be 10 digits") String mobileNumber) {
        FindAccountQuery findAccountQuery=new FindAccountQuery(mobileNumber);
        AccountsDto accountsDto = queryGateway.query(findAccountQuery,
                ResponseTypes.instanceOf(AccountsDto.class)).join();
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(accountsDto);
    }
}