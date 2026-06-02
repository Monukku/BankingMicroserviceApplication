package com.rewabank.loans.service;

import com.rewabank.loans.repository.LoanApplicationRepository;
import com.rewabank.loans.repository.OutboxEventRepository;
import com.rewabank.loans.client.AccountsFeignClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock
    private LoanApplicationRepository loanRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private AccountsFeignClient accountsFeignClient;

    @Mock
    private ObjectMapper objectMapper;

    private LoanService loanService;

    @BeforeEach
    void setUp() {
        loanService = new LoanService(loanRepository, outboxEventRepository,
                accountsFeignClient, objectMapper);
    }

    @Test
    void contextLoads() {
        // Test that LoanService initializes correctly with mocked dependencies
        assertNotNull(loanService);
    }
}
