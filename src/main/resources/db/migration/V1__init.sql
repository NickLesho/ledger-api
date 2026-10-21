CREATE TABLE account (
    id        UUID           PRIMARY KEY,
    owner     VARCHAR(100)   NOT NULL,
    currency  CHAR(3)        NOT NULL,
    balance   NUMERIC(19, 2) NOT NULL DEFAULT 0,
    version   BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_account_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT chk_account_currency_format     CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE TABLE transfer (
    id               UUID           PRIMARY KEY,
    idempotency_key  VARCHAR(100)   NOT NULL,
    from_account     UUID           NOT NULL REFERENCES account (id),
    to_account       UUID           NOT NULL REFERENCES account (id),
    amount           NUMERIC(19, 2) NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_transfer_idempotency_key    UNIQUE (idempotency_key),
    CONSTRAINT chk_transfer_amount_positive   CHECK (amount > 0),
    CONSTRAINT chk_transfer_distinct_accounts CHECK (from_account <> to_account),
    CONSTRAINT chk_transfer_status            CHECK (status IN ('COMPLETED', 'REJECTED'))
);

CREATE INDEX idx_transfer_from_account_created ON transfer (from_account, created_at DESC);
CREATE INDEX idx_transfer_to_account_created   ON transfer (to_account, created_at DESC);