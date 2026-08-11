package com.tradingplatform.trade.saga;

import com.tradingplatform.trade.domain.Trade;
import com.tradingplatform.trade.domain.TradeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists each saga state change in its own short transaction, committed before the orchestrator
 * makes its next network call.
 *
 * <p>Wrapping the whole saga in one transaction would be wrong twice over: it would hold a
 * connection open across remote calls, and a rollback would erase the record that funds had
 * already been taken — destroying the evidence that a refund is owed.
 *
 * <p>This is a separate bean because {@code @Transactional} is proxy-based: calling these methods
 * from within the orchestrator via {@code this.} would silently bypass the proxy and run with no
 * transaction at all.
 */
@Component
class TradeStateWriter {

    private final TradeRepository trades;

    TradeStateWriter(TradeRepository trades) {
        this.trades = trades;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Trade initiate(Trade trade) {
        return trades.saveAndFlush(trade);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markDebited(Trade trade) {
        trade.markDebited();
        trades.saveAndFlush(trade);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markCompleted(Trade trade) {
        trade.markCompleted();
        trades.saveAndFlush(trade);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markCompensating(Trade trade, String reason) {
        trade.markCompensating(reason);
        trades.saveAndFlush(trade);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markFailed(Trade trade, String reason) {
        trade.markFailed(reason);
        trades.saveAndFlush(trade);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markCompensationFailed(Trade trade, String reason) {
        trade.markCompensationFailed(reason);
        trades.saveAndFlush(trade);
    }
}
