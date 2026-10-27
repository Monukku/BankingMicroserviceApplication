package com.rewabank.accounts.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditAwareImplTest {

    private final AuditAwareImpl auditor = new AuditAwareImpl();

    @Test
    void getCurrentAuditor_returnsNonEmptyOptional() {
        // Kills NullReturn (return null) and EmptyObjectReturn (return Optional.empty())
        assertThat(auditor.getCurrentAuditor()).isPresent();
    }

    @Test
    void getCurrentAuditor_returnsCorrectServiceName() {
        assertThat(auditor.getCurrentAuditor()).contains("ACCOUNTS_MS");
    }
}