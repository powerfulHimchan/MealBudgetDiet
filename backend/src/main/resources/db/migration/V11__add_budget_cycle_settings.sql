ALTER TABLE ledgers
    ADD COLUMN budget_cycle_start_day integer NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_ledgers_budget_cycle_start_day
        CHECK (budget_cycle_start_day BETWEEN 1 AND 31);

ALTER TABLE budget_alerts
    ADD COLUMN cycle_days integer NOT NULL DEFAULT 31;

