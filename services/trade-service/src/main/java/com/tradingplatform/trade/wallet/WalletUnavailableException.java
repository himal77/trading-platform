package com.tradingplatform.trade.wallet;

/**
 * The outcome is unknown: the call timed out, the connection failed, or the wallet returned an
 * error that does not distinguish applied from not-applied.
 *
 * <p>This is the hard case in a distributed transaction — the operation may or may not have taken
 * effect. Because the wallet enforces idempotency on the transaction id, retrying is safe: if the
 * posting did land, the retry raises {@link AlreadyAppliedException} instead of applying twice.
 */
public class WalletUnavailableException extends RuntimeException {

    public WalletUnavailableException(String detail, Throwable cause) {
        super(detail, cause);
    }

    public WalletUnavailableException(String detail) {
        super(detail);
    }
}
