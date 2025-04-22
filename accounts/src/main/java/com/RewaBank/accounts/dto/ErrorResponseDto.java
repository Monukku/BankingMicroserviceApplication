package com.RewaBank.accounts.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Data @AllArgsConstructor
@Schema(
        name = "Error Response",
        description = "Schema to hold Error Response information"
)
public class ErrorResponseDto {

    @Schema(
            description = "Api path  invoked by Client"
    )
    private String apiPath;
    @Schema(
            description = "Error Code representing error happened "
    )
    private HttpStatus errorCode;
    @Schema(
            description = "Error Message representing error happened "
    )
    private String errorMessage;
    @Schema(
            description = "Time representing When error happened "
    )
    private LocalDateTime errorTime;
}
