package com.RewaBank.cards.query.controller;

import com.RewaBank.cards.dto.CardsDto;
import com.RewaBank.cards.dto.ErrorResponseDto;
import com.RewaBank.cards.query.FindCardQuery;
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
        name = "Query APIs for Accounts in RewaBank",
        description = "Query APIs in RewaBank for CREATE,READ,UPDATE,DELETE"
)
@RestController
@RequestMapping(path="/api", produces = {MediaType.APPLICATION_JSON_VALUE})
@Validated
@RefreshScope
@RequiredArgsConstructor
@Slf4j
public class CardsQueryController {

    private final QueryGateway queryGateway;

    @Operation(
            summary = "Fetch Card Details REST API",
            description = "REST API to fetch card details based on a mobile number"
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
    public ResponseEntity<CardsDto> fetchCardDetails(@RequestParam("mobileNumber")
    @Pattern(regexp="(^|[0-9]{10}$)",message = "Mobile number must be 10 digits")
    String mobileNumber) {

        FindCardQuery findCardQuery=new FindCardQuery(mobileNumber);
         CardsDto  cardsDto = queryGateway.query(findCardQuery, ResponseTypes.instanceOf(CardsDto.class)).join();
        return   ResponseEntity
                .status(HttpStatus.OK)
                .body(cardsDto);
    }
}

