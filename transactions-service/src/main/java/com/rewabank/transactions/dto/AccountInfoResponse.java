package com.rewabank.transactions.dto;

import java.util.UUID;

public record AccountInfoResponse(
        UUID   id,
        String accountNumber,
        String keycloakUserId,
        String status
) {}
