package com.RewaBank.accounts.repository;

import com.RewaBank.accounts.entity.Accounts;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AccountsRepository extends JpaRepository<Accounts,Long> {

    public List<Accounts> findAllByCustomerId(Long customerId);
    public Optional<Accounts> findByMobileNumberAndActiveSw(String mobileNumber,boolean activeSw);
    public Optional<Accounts> findByAccountNumber(Long mobileNumber);
    public Optional<Accounts> findByAccountId(Long accountId);
}
