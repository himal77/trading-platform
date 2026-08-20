package com.tradingplatform.trade.wallet;

/**
 * The wallet has already posted this transaction id, so the operation succeeded on an earlier
 * attempt. Callers treat this as success — it is the reason a retry after an unknown outcome is safe.
 */
public class AlreadyAppliedException extends RuntimeException {

    public AlreadyAppliedException(String detail) {
        super(detail);
    }
}
