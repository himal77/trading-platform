package com.tradingplatform.wallet.service;

import com.tradingplatform.wallet.api.dto.Cursor;
import com.tradingplatform.wallet.domain.Account;
import com.tradingplatform.wallet.domain.AccountRepository;
import com.tradingplatform.wallet.domain.LedgerEntry;
import com.tradingplatform.wallet.domain.LedgerEntry.EntryType;
import com.tradingplatform.wallet.domain.LedgerEntryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WalletService {

    private final AccountService accountService;
    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;

    public WalletService(AccountService accountService,
                         AccountRepository accounts,
                         LedgerEntryRepository ledger) {
        this.accountService = accountService;
        this.accounts = accounts;
        this.ledger = ledger;
    }

    /**
     * The transactionId is supplied by the caller so that a retry of the same logical operation
     * can be recognised rather than credited twice.
     *
     * <p>The balance update and the ledger entry share one transaction: either both are written or
     * neither is, which is what keeps {@code accounts.balance} equal to the sum of its entries.
     */
    @Transactional
    public LedgerEntry credit(UUID transactionId, UUID userId, String currency,
                              BigDecimal amount, EntryType entryType) {

        Account account = accountService.getOrCreate(userId, currency);
        account.credit(amount);
        accounts.save(account);

        return post(transactionId, account, amount, entryType);
    }

    /**
     * Throws {@link com.tradingplatform.wallet.domain.InsufficientFundsException} before anything
     * is written, so a rejected debit leaves no trace. The amount is stored negated, since the
     * ledger records direction in the sign.
     */
    @Transactional
    public LedgerEntry debit(UUID transactionId, UUID userId, String currency,
                             BigDecimal amount, EntryType entryType) {

        Account account = accountService.getOrCreate(userId, currency);
        account.debit(amount);
        accounts.save(account);

        return post(transactionId, account, amount.negate(), entryType);
    }

    @Transactional(readOnly = true)
    public List<Account> findAccounts(UUID userId) {
        return accounts.findByUserId(userId);
    }

    /**
     * @param cursor position of the last entry the caller saw, or null for the first page. Seeking
     *               on it keeps paging cost constant at any depth.
     */
    @Transactional(readOnly = true)
    public List<LedgerEntry> findTransactions(UUID userId, String currency,
                                              Cursor cursor, int size) {
        UUID accountId = accountService.getOrCreate(userId, currency).getId();
        Limit limit = Limit.of(size);

        return cursor == null
                ? ledger.findByAccountIdOrderByCreatedAtDescIdDesc(accountId, limit)
                : ledger.findPageAfter(accountId, cursor.createdAt(), cursor.id(), limit);
    }

    /**
     * The UNIQUE (transaction_id, account_id) index is what actually enforces idempotency — a
     * retry cannot post twice against the same account, whatever the calling code does.
     */
    private LedgerEntry post(UUID transactionId, Account account,
                             BigDecimal signedAmount, EntryType entryType) {
        try {
            return ledger.saveAndFlush(
                    LedgerEntry.post(transactionId, account, signedAmount, entryType));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateTransactionException(transactionId, account.getId());
        }
    }
}
