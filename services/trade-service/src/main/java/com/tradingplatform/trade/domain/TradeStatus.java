package com.tradingplatform.trade.domain;

import java.util.Set;

/**
 * Saga state. Legal transitions are declared here rather than checked ad hoc at call sites, so an
 * illegal move — a compensated trade being marked COMPLETED, for instance — cannot be expressed.
 */
public enum TradeStatus {

    /** Created; no balance touched. A crash here needs no compensation. */
    PENDING,

    /** Funds taken, the opposite leg still owed. A crash here MUST compensate. */
    DEBITED,

    /** Both legs applied. Terminal. */
    COMPLETED,

    /** Refund in flight. A crash here must retry the refund. */
    COMPENSATING,

    /** Compensated successfully; the user is whole again. Terminal. */
    FAILED,

    /** Funds taken and the refund also failed. Terminal only in the sense that no automated path
     *  remains — requires human intervention. */
    COMPENSATION_FAILED;

    private static final Set<TradeStatus> FROM_PENDING = Set.of(DEBITED, FAILED);
    private static final Set<TradeStatus> FROM_DEBITED = Set.of(COMPLETED, COMPENSATING);
    private static final Set<TradeStatus> FROM_COMPENSATING = Set.of(FAILED, COMPENSATION_FAILED);

    boolean canTransitionTo(TradeStatus target) {
        return switch (this) {
            case PENDING -> FROM_PENDING.contains(target);
            case DEBITED -> FROM_DEBITED.contains(target);
            case COMPENSATING -> FROM_COMPENSATING.contains(target);
            case COMPLETED, FAILED, COMPENSATION_FAILED -> false;
        };
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == COMPENSATION_FAILED;
    }

    /** True when funds have been taken but the trade has not been completed or unwound. */
    public boolean requiresCompensation() {
        return this == DEBITED;
    }
}
