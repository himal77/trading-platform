package com.tradingplatform.wallet.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByUserIdAndCurrency(UUID userId, String currency);

    List<Account> findByUserId(UUID userId);
}
