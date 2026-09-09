ALTER TABLE budget_alerts
    DROP CONSTRAINT ck_budget_alerts_type;

ALTER TABLE budget_alerts
    ADD CONSTRAINT ck_budget_alerts_type
        CHECK (alert_type IN ('MONTHLY_BUDGET_SURPLUS', 'MONTHLY_BUDGET_OVERRUN_RISK'));
