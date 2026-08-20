-- Saga state for a trade. Each status is a point at which the orchestrator may crash and must be
-- able to determine, on restart, what work remains or what needs undoing.
CREATE TABLE trades (
    id              UUID           PRIMARY KEY,
    user_id         UUID           NOT NULL,
    side            VARCHAR(4)     NOT NULL,
    asset           VARCHAR(10)    NOT NULL,
    quote_currency  VARCHAR(10)    NOT NULL,
    quantity        DECIMAL(20, 8) NOT NULL,
    price           DECIMAL(20, 8) NOT NULL,
    total           DECIMAL(20, 8) NOT NULL,
    status          VARCHAR(25)    NOT NULL,
    failure_reason  TEXT,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT trades_side_check CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT trades_quantity_positive CHECK (quantity > 0),
    CONSTRAINT trades_price_positive CHECK (price > 0),
    CONSTRAINT trades_total_positive CHECK (total > 0),
    CONSTRAINT trades_status_check CHECK (status IN (
        'PENDING',              -- created; no balance touched yet
        'DEBITED',              -- funds taken; the other leg still owed. Crash here => compensate.
        'COMPLETED',            -- both legs applied
        'COMPENSATING',         -- refund in flight
        'FAILED',               -- compensated successfully; user made whole
        'COMPENSATION_FAILED'   -- funds taken AND refund failed. Requires human intervention.
    ))
);

-- Trade history for one user, newest first. Equality on user_id, range on (created_at, id);
-- the id breaks ties so cursor paging cannot skip or repeat an entry.
CREATE INDEX idx_trades_user_created ON trades (user_id, created_at DESC, id DESC);

-- Recovery scan on startup: find sagas abandoned mid-flight. Partial, because only a handful of
-- rows are ever in these states while the vast majority are COMPLETED and must not be indexed.
CREATE INDEX idx_trades_unresolved ON trades (created_at)
    WHERE status IN ('PENDING', 'DEBITED', 'COMPENSATING');

-- Operational alerting: this set must always be empty. Partial index keeps it free to poll.
CREATE INDEX idx_trades_compensation_failed ON trades (created_at)
    WHERE status = 'COMPENSATION_FAILED';
