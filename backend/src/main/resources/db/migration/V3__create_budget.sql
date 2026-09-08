CREATE TABLE monthly_budgets (
    id uuid PRIMARY KEY,
    ledger_id uuid NOT NULL REFERENCES ledgers(id) ON DELETE CASCADE,
    budget_month date NOT NULL,
    amount bigint NOT NULL,
    version integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT ck_monthly_budgets_amount CHECK (amount > 0),
    CONSTRAINT ck_monthly_budgets_month_start
        CHECK (budget_month = date_trunc('month', budget_month)::date),
    CONSTRAINT uk_monthly_budgets_ledger_month UNIQUE (ledger_id, budget_month)
);
