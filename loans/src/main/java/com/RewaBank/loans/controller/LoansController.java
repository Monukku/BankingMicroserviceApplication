package com.RewaBank.loans.controller;


import com.RewaBank.loans.constants.LoansConstants;
import com.RewaBank.loans.dto.ErrorResponseDto;
import com.RewaBank.loans.dto.LoansContactInfoDto;
import com.RewaBank.loans.dto.LoansDto;
import com.RewaBank.loans.dto.ResponseDto;
import com.RewaBank.loans.services.ILoansService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.env.Environment;
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
public class LoansController {

    private static final Logger logger= LoggerFactory.getLogger(LoansController.class);
    private final ILoansService iLoansService;

    public LoansController(ILoansService iLoansService) {
        this.iLoansService = iLoansService;
    }
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
        public ResponseEntity<ResponseDto> createLoans(@Valid @RequestParam
                                                      @Pattern(regexp="(^|[0-9]{10}$)",message = "Mobile number must be 10 digits")
                                                      String mobileNumber) {
            iLoansService.createLoans(mobileNumber);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(new ResponseDto(LoansConstants.STATUS_201, LoansConstants.MESSAGE_201));
        }

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
        public ResponseEntity<LoansDto> fetchLoansDetails(@RequestHeader("rewabank-correlation-id") String correlationId,
                                                         @RequestParam("mobileNumber")
                                                         @Pattern(regexp="(^|[0-9]{10}$)",message = "Mobile number must be 10 digits")
                                                         String mobileNumber) {
            logger.debug("fetch loans details method starts");
            LoansDto loansDto = iLoansService.fetchLoansDetails(mobileNumber);
            logger.debug("fetch loans details method ends");
            return ResponseEntity.status(HttpStatus.OK).body(loansDto);
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
            boolean isUpdated = iLoansService.update(loansDto);
            if(isUpdated) {
                return ResponseEntity
                        .status(HttpStatus.OK)
                        .body(new ResponseDto(LoansConstants.STATUS_200, LoansConstants.MESSAGE_200));
            }else{
                return ResponseEntity
                        .status(HttpStatus.EXPECTATION_FAILED)
                        .body(new ResponseDto(LoansConstants.STATUS_417, LoansConstants.MESSAGE_417_UPDATE));
            }
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
        @DeleteMapping("/delete")
        public ResponseEntity<ResponseDto> deleteLoansDetails(@RequestParam
                                                             @Pattern(regexp="(^|[0-9]{10}$)",message = "Mobile number must be 10 digits")
                                                             String loansNumber) {
            boolean isDeleted = iLoansService.delete(loansNumber);
            if(isDeleted) {
                return ResponseEntity
                        .status(HttpStatus.OK)
                        .body(new ResponseDto(LoansConstants.STATUS_200, LoansConstants.MESSAGE_200));
            }else{
                return ResponseEntity
                        .status(HttpStatus.EXPECTATION_FAILED)
                        .body(new ResponseDto(LoansConstants.STATUS_417, LoansConstants.MESSAGE_417_DELETE));
            }
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
            logger.debug("invoked loans contact-info api");
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

