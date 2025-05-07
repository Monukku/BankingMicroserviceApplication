package com.service.verification_service.Service;
import org.springframework.stereotype.Service;
@Service
public class VerificationService {

    public boolean verifyCode(String userId, String code) {
        // Implement the actual verification logic
        // This is just a placeholder implementation
        return "valid-code".equals(code);
    }
}

