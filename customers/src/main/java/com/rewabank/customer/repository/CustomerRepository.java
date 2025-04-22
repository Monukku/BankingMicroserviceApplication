package com.rewabank.customer.repository;


import com.rewabank.customer.Entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer,String> {
    Optional<Customer> findByMobileNumberAndActiveSw(String mobileNumber , boolean activeSw);

}
