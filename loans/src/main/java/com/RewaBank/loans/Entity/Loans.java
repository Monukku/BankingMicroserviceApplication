package com.RewaBank.loans.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Loans extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO,generator = "native")
    @GenericGenerator(name= "native",strategy = "native")
    @Column(name = "loan_id")
    private Long loanId;

    @Column(name = "Mobile_Number")
    private String mobileNumber;

    @Column(name = "Loan_Number")
    private String loanNumber;

    @Column(name = "Loan_Type")
    private String loanType;

    @Column(name = "Total_Loan")
    private int totalLoan;

    @Column(name = "Amount_Paid")
    private int amountPaid;

    @Column(name = "outstanding_Amount")
    private  int outstandingAmount;

}
