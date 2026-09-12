ALTER TABLE ledgers
    ADD COLUMN budget_cycle_unit varchar(20) NOT NULL DEFAULT 'MONTHLY',
    ADD COLUMN budget_week_start_day integer NOT NULL DEFAULT 1,
    ADD COLUMN default_weekly_budget bigint,
    ADD CONSTRAINT ck_ledgers_budget_cycle_unit CHECK (budget_cycle_unit IN ('MONTHLY', 'WEEKLY')),
    ADD CONSTRAINT ck_ledgers_budget_week_start_day CHECK (budget_week_start_day BETWEEN 1 AND 7),
    ADD CONSTRAINT ck_ledgers_default_weekly_budget CHECK (default_weekly_budget IS NULL OR default_weekly_budget > 0),
    ADD CONSTRAINT ck_ledgers_weekly_budget_required CHECK (budget_cycle_unit <> 'WEEKLY' OR default_weekly_budget IS NOT NULL);

ALTER TABLE budget_alerts DROP CONSTRAINT ck_budget_alerts_type;
ALTER TABLE budget_alerts ADD CONSTRAINT ck_budget_alerts_type
    CHECK (alert_type IN ('MONTHLY_BUDGET_SURPLUS', 'MONTHLY_BUDGET_OVERRUN_RISK', 'WEEKLY_BUDGET_OVERRUN_RISK'));
