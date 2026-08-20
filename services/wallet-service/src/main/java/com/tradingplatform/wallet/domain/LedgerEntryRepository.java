package com.tradingplatform.wallet.domain;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /**
     * First page of an account's history. Uses idx_ledger_account_created directly, including its
     * DESC ordering, so no sort step is needed.
     */
    List<LedgerEntry> findByAccountIdOrderByCreatedAtDescIdDesc(UUID accountId, Limit limit);

    /**
     * Subsequent pages, seeking past the last entry the caller saw. The id comparison is what makes
     * this exact: entries can share a timestamp, and comparing on time alone would skip or repeat
     * them at a page boundary.
     *
     * <p>Cost is constant at any depth, unlike OFFSET which walks and discards every skipped row.
     */
    @Query("""
            SELECT e FROM LedgerEntry e
            WHERE e.accountId = :accountId
              AND (e.createdAt < :cursorCreatedAt
                   OR (e.createdAt = :cursorCreatedAt AND e.id < :cursorId))
            ORDER BY e.createdAt DESC, e.id DESC
            """)
    List<LedgerEntry> findPageAfter(@Param("accountId") UUID accountId,
                                    @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                    @Param("cursorId") UUID cursorId,
                                    Limit limit);

    /** Replays the ledger to verify accounts.balance has not drifted from its entries. */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e WHERE e.accountId = :accountId")
    BigDecimal sumAmountByAccountId(@Param("accountId") UUID accountId);

    List<LedgerEntry> findByTransactionId(UUID transactionId);
}
