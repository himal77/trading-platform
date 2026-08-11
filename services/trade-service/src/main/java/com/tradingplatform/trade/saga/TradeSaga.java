package com.tradingplatform.trade.saga;

import com.tradingplatform.trade.domain.Trade;
import com.tradingplatform.trade.wallet.AlreadyAppliedException;
import com.tradingplatform.trade.wallet.InsufficientFundsException;
import com.tradingplatform.trade.wallet.WalletClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Orchestrates a trade across two wallet postings.
 *
 * <p>There is no distributed transaction: the two balance changes live in another service's
 * database, so consistency is achieved by compensation instead of rollback. Every state change is
 * committed before the next remote call, so a crash always leaves a durable record of what has
 * been applied and what is still owed.
 *
 * <p>Both wallet calls carry the trade id as their transaction id, which makes them idempotent —
 * that is what allows a call with an unknown outcome to be safely retried.
 */
@Service
public class TradeSaga {

    private static final Logger log = LoggerFactory.getLogger(TradeSaga.class);

    private final TradeStateWriter state;
    private final WalletClient wallet;

    TradeSaga(TradeStateWriter state, WalletClient wallet) {
        this.state = state;
        this.wallet = wallet;
    }

    public Trade execute(Trade trade) {
        state.initiate(trade);

        if (!debit(trade)) {
            return trade;
        }
        credit(trade);
        return trade;
    }

    /** @return true when funds were taken and the saga may proceed */
    private boolean debit(Trade trade) {
        Leg out = outgoing(trade);
        try {
            wallet.debit(trade.getId(), trade.getUserId(), out.currency(), out.amount(), out.entryType());

        } catch (AlreadyAppliedException e) {
            // A previous attempt already took the funds; treat as done and continue.
            log.info("Debit for trade {} was already applied", trade.getId());

        } catch (InsufficientFundsException e) {
            // Nothing was taken, so there is nothing to unwind.
            state.markFailed(trade, "Insufficient funds: " + e.getMessage());
            return false;

        } catch (RuntimeException e) {
            // Outcome unknown after the retry budget was exhausted. Funds may or may not have
            // moved, so the safe assumption is that they did — hence compensate rather than fail.
            log.error("Debit for trade {} ended in an unknown state", trade.getId(), e);
            compensate(trade, "Debit outcome unknown: " + e.getMessage());
            return false;
        }

        state.markDebited(trade);
        return true;
    }

    private void credit(Trade trade) {
        Leg in = incoming(trade);
        try {
            wallet.credit(trade.getId(), trade.getUserId(), in.currency(), in.amount(), in.entryType());

        } catch (AlreadyAppliedException e) {
            log.info("Credit for trade {} was already applied", trade.getId());

        } catch (RuntimeException e) {
            // The user has paid and not received the asset — the funds must go back.
            log.error("Credit failed for trade {}; compensating", trade.getId(), e);
            compensate(trade, "Credit failed: " + e.getMessage());
            return;
        }

        state.markCompleted(trade);
    }

    /**
     * Returns the debited funds. A failure here means money was taken and could not be returned,
     * which no further automation can resolve: the trade is parked in COMPENSATION_FAILED for
     * operational alerting rather than left looking merely unfinished.
     */
    private void compensate(Trade trade, String reason) {
        state.markCompensating(trade, reason);

        Leg out = outgoing(trade);
        UUID compensationId = compensationIdFor(trade);
        try {
            wallet.credit(compensationId, trade.getUserId(), out.currency(), out.amount(), "REVERSAL");

        } catch (AlreadyAppliedException e) {
            log.info("Compensation for trade {} was already applied", trade.getId());

        } catch (RuntimeException e) {
            log.error("COMPENSATION FAILED for trade {} — funds taken and not returned",
                    trade.getId(), e);
            state.markCompensationFailed(trade, reason + " | compensation failed: " + e.getMessage());
            return;
        }

        state.markFailed(trade, reason);
    }

    /**
     * The refund is a distinct posting from the debit, so it needs its own transaction id — reusing
     * the trade id would be rejected by the wallet's idempotency constraint as a duplicate.
     * Deriving it from the trade id keeps the refund itself idempotent across retries.
     */
    private UUID compensationIdFor(Trade trade) {
        return UUID.nameUUIDFromBytes(("compensate:" + trade.getId()).getBytes());
    }

    private Leg outgoing(Trade trade) {
        return switch (trade.getSide()) {
            case BUY -> new Leg(trade.getQuoteCurrency(), trade.getTotal(), "TRADE_BUY");
            case SELL -> new Leg(trade.getAsset(), trade.getQuantity(), "TRADE_SELL");
        };
    }

    private Leg incoming(Trade trade) {
        return switch (trade.getSide()) {
            case BUY -> new Leg(trade.getAsset(), trade.getQuantity(), "TRADE_BUY");
            case SELL -> new Leg(trade.getQuoteCurrency(), trade.getTotal(), "TRADE_SELL");
        };
    }

    private record Leg(String currency, java.math.BigDecimal amount, String entryType) {
    }
}
