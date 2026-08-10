CREATE TABLE accounts (
    id         UUID           PRIMARY KEY,
    user_id    UUID           NOT NULL,
    currency   VARCHAR(10)    NOT NULL,
    balance    DECIMAL(20, 8) NOT NULL DEFAULT 0,
    version    BIGINT         NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT accounts_user_currency_unique UNIQUE (user_id, currency),
    CONSTRAINT accounts_balance_non_negative CHECK (balance >= 0)
);

CREATE INDEX idx_accounts_user ON accounts (user_id);

-- Append-only. No UPDATE, no DELETE — corrections are posted as new reversing entries.
CREATE TABLE ledger_entries (
    id             UUID           PRIMARY KEY,
    transaction_id UUID           NOT NULL,
    account_id     UUID           NOT NULL REFERENCES accounts (id),
    -- Negative = debit (money out), positive = credit (money in).
    amount         DECIMAL(20, 8) NOT NULL,
    balance_after  DECIMAL(20, 8) NOT NULL,
    entry_type     VARCHAR(30)    NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT ledger_amount_non_zero CHECK (amount <> 0),
    CONSTRAINT ledger_entry_type_check CHECK (
        entry_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRADE_BUY', 'TRADE_SELL', 'FEE', 'REVERSAL')
    )
);

-- Transaction history: equality on account, then a range over (created_at, id). The id breaks
-- ties between entries sharing a timestamp, so paging can never skip or repeat a row. DESC on
-- both columns matches the ORDER BY, so no sort step is needed.
CREATE INDEX idx_ledger_account_created
    ON ledger_entries (account_id, created_at DESC, id DESC);

-- Idempotency: a retried operation carries the same transaction_id, so the second attempt to
-- post against the same account is rejected by the database rather than double-counted.
-- Also serves as the index for fetching every entry belonging to one transaction.
CREATE UNIQUE INDEX idx_ledger_transaction_account
    ON ledger_entries (transaction_id, account_id);
