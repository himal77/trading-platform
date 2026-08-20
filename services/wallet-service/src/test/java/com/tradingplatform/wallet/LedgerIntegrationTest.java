package com.tradingplatform.wallet;

import com.tradingplatform.wallet.domain.InsufficientFundsException;
import com.tradingplatform.wallet.domain.LedgerEntry.EntryType;
import com.tradingplatform.wallet.domain.LedgerEntryRepository;
import com.tradingplatform.wallet.service.DuplicateTransactionException;
import com.tradingplatform.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against real Postgres. Mocking would hide the constraints and indexes that enforce most of
 * this service's guarantees — the unique idempotency index in particular.
 */
@SpringBootTest
@Testcontainers
class LedgerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    }

    @Autowired
    WalletService walletService;

    @Autowired
    LedgerEntryRepository ledger;

    @Test
    void creditWritesBalanceAndLedgerEntryTogether() {
        UUID user = UUID.randomUUID();

        var entry = walletService.credit(UUID.randomUUID(), user, "EUR",
                new BigDecimal("620.00"), EntryType.DEPOSIT);

        assertThat(entry.getAmount()).isEqualByComparingTo("620.00");
        assertThat(entry.getBalanceAfter()).isEqualByComparingTo("620.00");
        assertThat(entry.getEntryType()).isEqualTo(EntryType.DEPOSIT);
    }

    /** Debits are stored negated, so SUM(amount) replays the balance. */
    @Test
    void debitIsStoredAsANegativeAmount() {
        UUID user = UUID.randomUUID();
        walletService.credit(UUID.randomUUID(), user, "EUR",
                new BigDecimal("620.00"), EntryType.DEPOSIT);

        var entry = walletService.debit(UUID.randomUUID(), user, "EUR",
                new BigDecimal("120.00"), EntryType.TRADE_BUY);

        assertThat(entry.getAmount()).isEqualByComparingTo("-120.00");
        assertThat(entry.getBalanceAfter()).isEqualByComparingTo("500.00");
    }

    /**
     * The reason the ledger exists: the cached balance must always equal the sum of its entries.
     * A lost update would show up here as drift, where a single mutable balance column would fail
     * silently.
     */
    @Test
    void cachedBalanceAlwaysMatchesTheReplayedLedger() {
        UUID user = UUID.randomUUID();

        walletService.credit(UUID.randomUUID(), user, "EUR", new BigDecimal("1000.00"), EntryType.DEPOSIT);
        walletService.debit(UUID.randomUUID(), user, "EUR", new BigDecimal("250.50"), EntryType.TRADE_BUY);
        walletService.credit(UUID.randomUUID(), user, "EUR", new BigDecimal("75.25"), EntryType.TRADE_SELL);
        walletService.debit(UUID.randomUUID(), user, "EUR", new BigDecimal("10.00"), EntryType.FEE);

        var account = walletService.findAccounts(user).getFirst();
        BigDecimal replayed = ledger.sumAmountByAccountId(account.getId());

        assertThat(account.getBalance()).isEqualByComparingTo("814.75");
        assertThat(account.getBalance()).isEqualByComparingTo(replayed);
    }

    @Test
    void debitBeyondBalanceWritesNothing() {
        UUID user = UUID.randomUUID();
        walletService.credit(UUID.randomUUID(), user, "EUR",
                new BigDecimal("50.00"), EntryType.DEPOSIT);

        assertThatThrownBy(() -> walletService.debit(UUID.randomUUID(), user, "EUR",
                new BigDecimal("1000.00"), EntryType.TRADE_BUY))
                .isInstanceOf(InsufficientFundsException.class);

        var account = walletService.findAccounts(user).getFirst();
        assertThat(account.getBalance()).isEqualByComparingTo("50.00");
        assertThat(ledger.sumAmountByAccountId(account.getId())).isEqualByComparingTo("50.00");
    }

    /** Enforced by UNIQUE (transaction_id, account_id), so no application bug can double-credit. */
    @Test
    void replayingATransactionIdIsRejected() {
        UUID user = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        walletService.credit(transactionId, user, "EUR", new BigDecimal("100.00"), EntryType.DEPOSIT);

        assertThatThrownBy(() -> walletService.credit(transactionId, user, "EUR",
                new BigDecimal("100.00"), EntryType.DEPOSIT))
                .isInstanceOf(DuplicateTransactionException.class);
    }

    /** One transaction legitimately touches two accounts — the EUR and BTC legs of a trade. */
    @Test
    void oneTransactionCanPostToTwoDifferentAccounts() {
        UUID user = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        walletService.credit(UUID.randomUUID(), user, "EUR", new BigDecimal("1000.00"), EntryType.DEPOSIT);

        walletService.debit(transactionId, user, "EUR", new BigDecimal("620.00"), EntryType.TRADE_BUY);
        walletService.credit(transactionId, user, "BTC", new BigDecimal("0.01"), EntryType.TRADE_BUY);

        assertThat(ledger.findByTransactionId(transactionId)).hasSize(2);
    }

    @Test
    void accountsAreCreatedLazilyPerCurrency() {
        UUID user = UUID.randomUUID();

        walletService.credit(UUID.randomUUID(), user, "EUR", new BigDecimal("100"), EntryType.DEPOSIT);
        walletService.credit(UUID.randomUUID(), user, "BTC", new BigDecimal("0.5"), EntryType.DEPOSIT);

        assertThat(walletService.findAccounts(user))
                .hasSize(2)
                .extracting(a -> a.getCurrency())
                .containsExactlyInAnyOrder("EUR", "BTC");
    }
}
