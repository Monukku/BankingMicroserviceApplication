package com.RewaBank.accounts.Utility;

public enum AccountStatus {
    ACTIVE,           // Account is active and operational
    INACTIVE,         // Account is inactive but can be reactivated
    PENDING,          // Account creation is pending and needs further action
    SUSPENDED,        // Account is suspended, usually due to issues or violations
    CLOSED,           // Account has been closed and can no longer be used
    OVERDRAWN         // Account is overdrawn (for accounts that allow overdrafts)
}
