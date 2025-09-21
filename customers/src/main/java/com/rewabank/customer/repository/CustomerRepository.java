package com.rewabank.customer.repository;


import com.rewabank.customer.Entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer,String> {
    Optional<Customer> findByMobileNumberAndActiveSw(String mobileNumber , boolean activeSw);
    Page<Customer> findByRole(String role, Pageable pageable);
    Optional<Customer> findByCustomerIdAndActiveSw(String customerId , boolean activeSw);
}
