CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(72)  NOT NULL,
    tier          VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT users_tier_check   CHECK (tier IN ('FREE', 'PREMIUM', 'PARTNER')),
    CONSTRAINT users_status_check CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED'))
);

-- Emails are compared case-insensitively, so uniqueness must be too.
CREATE UNIQUE INDEX users_email_lower_idx ON users (LOWER(email));
