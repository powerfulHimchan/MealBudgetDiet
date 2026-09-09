ALTER TABLE ledgers
    ADD COLUMN push_usage_threshold integer NOT NULL DEFAULT 80,
    ADD CONSTRAINT ck_ledgers_push_usage_threshold
        CHECK (push_usage_threshold BETWEEN 1 AND 100);

ALTER TABLE budget_alerts
    ADD COLUMN usage_threshold integer NOT NULL DEFAULT 80,
    ADD CONSTRAINT ck_budget_alerts_usage_threshold
        CHECK (usage_threshold BETWEEN 1 AND 100);
