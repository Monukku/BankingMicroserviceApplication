package com.rewabank.customer.Entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import jakarta.validation.constraints.Pattern;
import org.hibernate.annotations.IdGeneratorType;
import org.hibernate.annotations.UuidGenerator;

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
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq")
    @SequenceGenerator(name = "user_seq", sequenceName = "user_sequence", allocationSize = 50)
    private String customerId;

    @NotNull
    @Size(min = 2, max = 100)
    private String name;

    @Email
    @NotNull
    private String email;

    @Pattern(regexp = "\\d{10}")
    private String mobileNumber;

    private boolean activeSw;

}

