package com.RewaBank.accounts.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import java.util.HashSet;
import java.util.Set;

//@Entity
//@Getter@Setter@ToString
//@AllArgsConstructor
//@NoArgsConstructor
//@Table(name = "Customer_Table")
//public class Customer extends  BaseEntity {
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.AUTO,generator = "native")
//    @GenericGenerator(name="native")
//    private Long Id;
//
//    @NotNull
//    @Size(min = 2, max = 100)
//    private String name;
//
//    @Email
//    @NotNull
//    private String email;
//
//    @Pattern(regexp = "\\d{10}")
//    private String mobileNumber;
//
//    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
//    private Set<Account> accounts = new HashSet<>();
//
//    public void addAccount(Account account) {
//        account.setCustomer(this);
//        accounts.add(account);
//    }
//
//    public void removeAccount(Account account) {
//        accounts.remove(account);
//        account.setCustomer(null);
//    }
//
//
//}


@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "Customer_Table")
public class Customer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "native")
    @GenericGenerator(name = "native", strategy = "native")
    private Long Id;

    @NotNull
    @Size(min = 2, max = 100)
    private String name;

    @Email
    @NotNull
    private String email;

    @Pattern(regexp = "\\d{10}")
    private String mobileNumber;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Set<Accounts> accounts = new HashSet<>();

    public void addAccount(Accounts account) {
        account.setCustomer(this);
        accounts.add(account);
    }

    public void removeAccount(Accounts account) {
        accounts.remove(account);
        account.setCustomer(null);
    }
}

