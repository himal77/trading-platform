package com.tradingplatform.wallet.service;

import com.tradingplatform.wallet.domain.Account;
import com.tradingplatform.wallet.domain.AccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /**
     * Accounts are created on first use rather than provisioned up front: the userId comes from
     * a gateway-verified JWT, so no call to auth-service is needed to confirm the user exists.
     */
    @Transactional
    public Account getOrCreate(UUID userId, String currency) {
        return accounts.findByUserIdAndCurrency(userId, currency)
                .orElseGet(() -> insertOrReadExisting(userId, currency));
    }

    /**
     * Two concurrent requests can both find no account and both attempt an insert. Rather than
     * trying to prevent that, we let the UNIQUE (user_id, currency) constraint reject the loser
     * and have it read the row the winner just committed.
     */
    private Account insertOrReadExisting(UUID userId, String currency) {
        try {
            return accounts.saveAndFlush(Account.open(userId, currency));
        } catch (DataIntegrityViolationException e) {
            return accounts.findByUserIdAndCurrency(userId, currency)
                    .orElseThrow(() -> e);
        }
    }
}
