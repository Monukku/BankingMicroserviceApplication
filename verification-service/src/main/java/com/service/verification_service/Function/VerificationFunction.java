package com.service.verification_service.Function;
import com.service.verification_service.Dto.VerificationRequestDto;
import com.service.verification_service.Service.VerificationService;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class VerificationFunction {

    private final VerificationService verificationService;

    public VerificationFunction(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @StreamListener("verification-in-0")
    public void handleVerificationRequest(@Payload VerificationRequestDto request) {
        boolean isVerified = verificationService.verifyCode(request.getUserId(), request.getCode());
        // Handle verification result, e.g., send response or notification
        if (isVerified) {
            // Verification successful
        } else {
            // Verification failed
        }
    }
}
