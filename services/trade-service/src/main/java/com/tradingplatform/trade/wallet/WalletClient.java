package com.tradingplatform.trade.wallet;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The saga depends on this abstraction rather than on HTTP, so the transport can later move to gRPC
 * by replacing the implementation alone.
 */
public interface WalletClient {

    /**
     * @param transactionId identifies the logical operation; the wallet rejects a second posting
     *                      with the same id, so a retry cannot double-apply
     * @throws InsufficientFundsException      the balance cannot cover the amount — the saga must fail
     * @throws AlreadyAppliedException         this posting already happened — treat as success
     * @throws WalletUnavailableException      the wallet could not be reached or failed; outcome unknown
     */
    void debit(UUID transactionId, UUID userId, String currency, BigDecimal amount, String entryType);

    void credit(UUID transactionId, UUID userId, String currency, BigDecimal amount, String entryType);
}
