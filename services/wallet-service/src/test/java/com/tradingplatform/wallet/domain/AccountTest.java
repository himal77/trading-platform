package com.tradingplatform.wallet.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static final UUID USER = UUID.randomUUID();

    @Test
    void newAccountStartsAtZero() {
        Account account = Account.open(USER, "EUR");

        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.getCurrency()).isEqualTo("EUR");
        assertThat(account.getId()).isNotNull();
    }

    @Test
    void creditIncreasesBalance() {
        Account account = Account.open(USER, "EUR");

        account.credit(new BigDecimal("620.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("620.00");
    }

    @Test
    void debitDecreasesBalance() {
        Account account = Account.open(USER, "EUR");
        account.credit(new BigDecimal("620.00"));

        account.debit(new BigDecimal("120.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    void debitBeyondBalanceIsRejectedAndLeavesBalanceUntouched() {
        Account account = Account.open(USER, "EUR");
        account.credit(new BigDecimal("50.00"));

        assertThatThrownBy(() -> account.debit(new BigDecimal("1000.00")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(account.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void debitOfExactBalanceIsAllowed() {
        Account account = Account.open(USER, "EUR");
        account.credit(new BigDecimal("50.00"));

        account.debit(new BigDecimal("50.00"));

        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** Direction must come from the method called, never from the sign of the argument. */
    @Test
    void negativeAndZeroAmountsAreRejected() {
        Account account = Account.open(USER, "EUR");

        assertThatThrownBy(() -> account.credit(new BigDecimal("-100")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.debit(new BigDecimal("-100")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.credit(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Satoshi precision: a value this small must survive arithmetic intact. */
    @Test
    void handlesEightDecimalPlaces() {
        Account account = Account.open(USER, "BTC");

        account.credit(new BigDecimal("0.00000001"));

        assertThat(account.getBalance()).isEqualByComparingTo("0.00000001");
    }
}
