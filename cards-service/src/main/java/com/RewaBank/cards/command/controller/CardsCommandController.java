package com.RewaBank.cards.command.controller;

import com.RewaBank.cards.command.CreateCardCommand;
import com.RewaBank.cards.command.DeleteCardCommand;
import com.RewaBank.cards.command.UpdateCardCommand;
import com.RewaBank.cards.constants.CardsConstants;
import com.RewaBank.cards.dto.CardsContactInfoDto;
import com.RewaBank.cards.dto.CardsDto;
import com.RewaBank.cards.dto.ErrorResponseDto;
import com.RewaBank.cards.dto.ResponseDto;
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
        name = "CRUD REST APIs for Cards in RewaBank",
        description = "CRUD REST APIs in RewaBank to CREATE, UPDATE, FETCH AND DELETE card details"
)
@RestController
@RequestMapping(path = "/api", produces = {MediaType.APPLICATION_JSON_VALUE})
@Validated
@RefreshScope
@Slf4j
@RequiredArgsConstructor
public class CardsCommandController {

    private final CommandGateway commandGateway;

    @Autowired
    @Value("${build.version}")
    private String buildVersion;

    @Autowired
    private Environment environment;

    @Autowired
    private CardsContactInfoDto cardsContactInfoDto;

    @Operation(
            summary = "Create Card REST API",
            description = "REST API to create new Card inside RewaBank"
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
    public ResponseEntity<ResponseDto> createCard(@Valid @RequestParam(name = "mobileNumber")
                                                      @Pattern(regexp="(^|[0-9]{10}$)",message = "Mobile number must be 10 digits")
                                                      String mobileNumber) {
        long randomCardNumber = 1000000000L + new Random().nextInt(900000000);
        CreateCardCommand createCommand = CreateCardCommand.builder()
                        .cardNumber(randomCardNumber).mobileNumber(mobileNumber)
                        .cardType(CardsConstants.CREDIT_CARD).totalLimit(CardsConstants.NEW_CARD_LIMIT)
                        .amountUsed(0).availableAmount(CardsConstants.NEW_CARD_LIMIT)
                        .activeSw(CardsConstants.ACTIVE_SW)
                        .build();

            commandGateway.sendAndWait(createCommand);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(new ResponseDto(CardsConstants.STATUS_201, CardsConstants.MESSAGE_201));
    }

    @Operation(
            summary = "Update Card Details REST API",
            description = "REST API to update card details based on a card number"
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
    public ResponseEntity<ResponseDto> updateCardDetails(@Valid @RequestBody CardsDto cardsDto) {

        UpdateCardCommand updateCardCommand= UpdateCardCommand.builder()
                .cardNumber(cardsDto.getCardNumber()).mobileNumber(cardsDto.getMobileNumber())
                .cardType(CardsConstants.CREDIT_CARD).totalLimit(cardsDto.getTotalLimit())
                .amountUsed(cardsDto.getAmountUsed()).availableAmount(cardsDto.getAvailableAmount())
                .activeSw(CardsConstants.ACTIVE_SW)
                .build();

        commandGateway.sendAndWait(updateCardCommand);
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(new ResponseDto(CardsConstants.STATUS_200, CardsConstants.MESSAGE_200));
    }

    @Operation(
            summary = "Delete Card Details REST API",
            description = "REST API to delete Card details based on a mobile number"
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
    public ResponseEntity<ResponseDto> deleteCardDetails(@RequestParam
                      @Pattern(regexp="(^|[0-9]{10}$)",message = "Card number must be 10 digits") Long cardNumber) {
        DeleteCardCommand  deleteCardCommand=DeleteCardCommand.builder()
                .cardNumber(cardNumber)
                .activeSw(CardsConstants.IN_ACTIVE_SW)
                .build();
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(new ResponseDto(CardsConstants.STATUS_200, CardsConstants.MESSAGE_200));
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
    @GetMapping("/build-info")
    public ResponseEntity<String> getBuildInfo() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(buildVersion);
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
    public ResponseEntity<CardsContactInfoDto> getContactInfo() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(cardsContactInfoDto);
    }
}
