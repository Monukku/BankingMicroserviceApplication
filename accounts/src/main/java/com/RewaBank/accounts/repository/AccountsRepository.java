package com.RewaBank.accounts.repository;

import com.RewaBank.accounts.entity.Accounts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountsRepository extends JpaRepository<Accounts,Long> {

    public Optional<Accounts> findByMobileNumberAndActiveSw(String mobileNumber,boolean activeSw);
    public Optional<Accounts> findByAccountNumberAndActiveSw(Long mobileNumber,boolean activeSw);
}
