package com.rewabank.payments.client.dto;

import java.util.UUID;

public record AccountResponse(
        UUID   id,
        String accountNumber,
        String keycloakUserId,
        String status
) {}
