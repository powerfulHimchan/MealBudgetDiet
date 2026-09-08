CREATE TABLE push_subscriptions (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint text NOT NULL UNIQUE,
    p256dh_key text NOT NULL,
    auth_key text NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT ck_push_subscriptions_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'DISABLED'))
);

CREATE TABLE budget_alerts (
    id uuid PRIMARY KEY,
    ledger_id uuid NOT NULL REFERENCES ledgers(id) ON DELETE CASCADE,
    triggered_by_expense_id uuid REFERENCES expenses(id) ON DELETE SET NULL,
    alert_month date NOT NULL,
    alert_type varchar(40) NOT NULL,
    monthly_budget bigint NOT NULL,
    total_spent bigint NOT NULL,
    remaining_days integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT ck_budget_alerts_type CHECK (alert_type IN ('MONTHLY_BUDGET_SURPLUS')),
    CONSTRAINT uk_budget_alerts_month_type UNIQUE (ledger_id, alert_month, alert_type)
);

CREATE TABLE push_deliveries (
    id uuid PRIMARY KEY,
    budget_alert_id uuid NOT NULL REFERENCES budget_alerts(id) ON DELETE CASCADE,
    push_subscription_id uuid NOT NULL REFERENCES push_subscriptions(id) ON DELETE CASCADE,
    status varchar(20) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz,
    sent_at timestamptz,
    last_error text,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT ck_push_deliveries_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED')),
    CONSTRAINT uk_push_deliveries_target UNIQUE (budget_alert_id, push_subscription_id)
);

CREATE INDEX idx_push_deliveries_pending
    ON push_deliveries (status, next_attempt_at)
    WHERE status IN ('PENDING', 'FAILED');
