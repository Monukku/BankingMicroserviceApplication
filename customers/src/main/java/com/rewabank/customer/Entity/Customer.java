package com.rewabank.customer.Entity;

import com.rewabank.customer.Utility.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import jakarta.validation.constraints.Pattern;


@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "Customer_Table")
public class Customer extends BaseEntity {

    @Id
    @Column(name = "customer_Id",nullable = false)
    private String customerId;

    @NotNull
    @Size(min = 2, max = 100)
    private String name;

    @NotNull
    @Enumerated
    private Role role;

    @Email
    @NotNull
    private String email;

    @Pattern(regexp = "\\d{10}")
    private String mobileNumber;

    private boolean activeSw;

}

