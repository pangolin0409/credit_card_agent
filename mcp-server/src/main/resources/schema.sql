DROP TABLE IF EXISTS audit_logs, approval_drafts, transactions, credit_cards, customers CASCADE;

CREATE TABLE customers (
    customer_id             VARCHAR(20)  NOT NULL,
    full_name               VARCHAR(50)  NOT NULL,
    monthly_income          INT          NOT NULL,
    existing_unsecured_debt INT          NOT NULL DEFAULT 0,
    credit_score            INT          NOT NULL,
    is_aml_blacklist        BOOLEAN      NOT NULL DEFAULT FALSE,
    has_travel_notice       BOOLEAN      NOT NULL DEFAULT FALSE,
    travel_country          VARCHAR(10)  NULL,
    CONSTRAINT pk_customers PRIMARY KEY (customer_id)
);

CREATE TABLE credit_cards (
    card_number   VARCHAR(20) NOT NULL,
    customer_id   VARCHAR(20) NOT NULL,
    card_type     VARCHAR(30) NOT NULL,
    credit_limit  INT         NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    freeze_reason TEXT        NULL,
    CONSTRAINT pk_credit_cards PRIMARY KEY (card_number),
    CONSTRAINT fk_credit_cards_customers FOREIGN KEY (customer_id) REFERENCES customers (customer_id),
    CONSTRAINT ck_credit_cards_card_type CHECK (card_type IN ('INFINITE', 'SIGNATURE')),
    CONSTRAINT ck_credit_cards_status CHECK (status IN ('ACTIVE', 'TEMP_FROZEN', 'CLOSED'))
);

CREATE TABLE transactions (
    transaction_id   VARCHAR(30)  NOT NULL,
    card_number      VARCHAR(20)  NOT NULL,
    transaction_time TIMESTAMP    NOT NULL,
    amount           INT          NOT NULL,
    merchant_name    VARCHAR(100) NOT NULL,
    country_code     VARCHAR(10)  NOT NULL,
    is_online        BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_transactions PRIMARY KEY (transaction_id),
    CONSTRAINT fk_transactions_credit_cards FOREIGN KEY (card_number) REFERENCES credit_cards (card_number)
);

CREATE INDEX idx_transactions_card_time ON transactions (card_number, transaction_time);

CREATE TABLE approval_drafts (
    draft_id           SERIAL      NOT NULL,
    customer_id        VARCHAR(20) NOT NULL,
    card_type          VARCHAR(30) NOT NULL,
    credit_limit       INT         NOT NULL,
    assessment_summary TEXT        NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    created_by         VARCHAR(50) NOT NULL DEFAULT 'AI_AGENT_MCP',
    created_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by        VARCHAR(50) NULL,
    reviewed_at        TIMESTAMP   NULL,
    CONSTRAINT pk_approval_drafts PRIMARY KEY (draft_id),
    CONSTRAINT fk_approval_drafts_customers FOREIGN KEY (customer_id) REFERENCES customers (customer_id),
    CONSTRAINT ck_approval_drafts_card_type CHECK (card_type IN ('INFINITE', 'SIGNATURE')),
    CONSTRAINT ck_approval_drafts_status CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED'))
);

CREATE TABLE audit_logs (
    log_id      SERIAL      NOT NULL,
    target_id   VARCHAR(30) NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    reason      TEXT        NOT NULL,
    operator    VARCHAR(50) NOT NULL DEFAULT 'AI_AGENT_MCP',
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_audit_logs PRIMARY KEY (log_id),
    CONSTRAINT ck_audit_logs_action_type CHECK (action_type IN (
        'APPROVAL_DRAFT', 'APPLICATION_DECLINE', 'TEMP_FREEZE', 'ESCALATION',
        'GUARDRAIL_BLOCKED', 'DRAFT_APPROVED', 'DRAFT_REJECTED'))
);
