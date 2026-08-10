package com.tradingplatform.wallet.service;

import java.util.UUID;

/**
 * A ledger entry for this transaction and account already exists, meaning the operation was
 * already applied. The caller should treat this as success, not as a failure to retry.
 */
public class DuplicateTransactionException extends RuntimeException {

    public DuplicateTransactionException(UUID transactionId, UUID accountId) {
        super("Transaction %s has already been posted to account %s"
                .formatted(transactionId, accountId));
    }
}
