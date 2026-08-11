package com.tradingplatform.trade.domain;

import java.util.UUID;

/**
 * Always a bug, never a user error: it means the orchestrator attempted a step out of sequence.
 */
public class IllegalTradeTransitionException extends RuntimeException {

    public IllegalTradeTransitionException(UUID tradeId, TradeStatus from, TradeStatus to) {
        super("Trade %s cannot move from %s to %s".formatted(tradeId, from, to));
    }
}
