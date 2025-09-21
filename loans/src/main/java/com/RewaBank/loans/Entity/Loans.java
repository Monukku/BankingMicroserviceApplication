package com.RewaBank.loans.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class Loans extends BaseEntity{

    @Id
    @Column(name = "loanNumber")
    private Long loanNumber;

    @Column(name = "Mobile_Number")
    private String mobileNumber;

    @Column(name = "Loan_Type")
    private String loanType;

    @Column(name = "Total_Loan")
    private int totalLoan;

    @Column(name = "Amount_Paid")
    private int amountPaid;

    @Column(name = "outstanding_Amount")
    private  int outstandingAmount;

    @Column(name = "activeSw")
    private boolean activeSw;
}
