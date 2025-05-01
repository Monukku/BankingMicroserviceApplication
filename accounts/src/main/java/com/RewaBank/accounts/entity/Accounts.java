package com.RewaBank.accounts.entity;

import com.RewaBank.accounts.Utility.AccountCategory;
import com.RewaBank.accounts.Utility.AccountStatus;
import com.RewaBank.accounts.Utility.AccountType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;
import lombok.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.math.BigDecimal;
//
//
//@Entity
//@Getter
//@Setter
//@ToString
//@AllArgsConstructor
//@EntityListeners(AuditingEntityListener.class)
//@Table(name = "Accounts_Table")
//public class Account extends BaseEntity {
//
//    public Account() {
//    }
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    @Column(name = "account_Id")
//    private Long Id;
//
//    @NotNull
//    @Column(unique = true)
//    private Long accountNumber;
//
//    @NotNull
//    private String branchAddress;
//
//    @Positive
//    private BigDecimal balance;
//
//    @Enumerated(EnumType.STRING)
//    @Column(nullable = false)
//    private AccountType accountType;
//
//    @Enumerated(EnumType.STRING)
//    @Column(nullable = false)
//    private AccountStatus accountStatus;
//
//    @Enumerated(EnumType.STRING)
//    @Column(nullable = false)
//    private AccountCategory accountCategory;
//
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "customer_id")
//    private Customer customer;
//
//    @Column(name="communication_sw")
//    private Boolean communicationSw;
//
//    public void addCustomer(Customer customer) {
//        this.customer = customer;
//        customer.getAccounts().add(this);
//    }
//
//    public void removeCustomer() {
//        if (this.customer != null) {
//            this.customer.getAccounts().remove(this);
//            this.customer = null;
//        }
//    }
//
//    // Enhanced equals and hashCode methods
//    @Override
//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//        Account account = (Account) o;
//        return Id != null && Objects.equals(Id, account.Id);
//    }
//
//    @Override
//    public int hashCode() {
//        return Objects.hash(Id);
//    }
//}
//

@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "Accounts_Table")
public class Accounts extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_Id")
    private Long accountId;

    @NotNull
    @Column(unique = true)
    private Long accountNumber;

    @NotNull
    private String branchAddress;

    @Positive
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus accountStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountCategory accountCategory;

    @Column(name="communication_sw")
    private boolean activeSw;

}
