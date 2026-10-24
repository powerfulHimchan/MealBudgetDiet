ALTER TABLE ledgers ADD COLUMN carry_surplus boolean NOT NULL DEFAULT false;
ALTER TABLE ledgers ADD COLUMN carry_deficit boolean NOT NULL DEFAULT false;

CREATE TABLE budget_rollover_rules (
    ledger_id uuid NOT NULL REFERENCES ledgers(id) ON DELETE CASCADE,
    effective_from date NOT NULL,
    seed_from date NOT NULL,
    cycle_unit varchar(20) NOT NULL CHECK (cycle_unit IN ('MONTHLY', 'WEEKLY')),
    month_start_day integer NOT NULL CHECK (month_start_day BETWEEN 1 AND 31),
    week_start_day integer NOT NULL CHECK (week_start_day BETWEEN 1 AND 7),
    carry_surplus boolean NOT NULL,
    carry_deficit boolean NOT NULL,
    reset_chain boolean NOT NULL,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    PRIMARY KEY (ledger_id, effective_from),
    CHECK (seed_from < effective_from)
);

CREATE TABLE budget_rollover_bases (
    ledger_id uuid NOT NULL REFERENCES ledgers(id) ON DELETE CASCADE,
    effective_from date NOT NULL,
    cycle_unit varchar(20) NOT NULL,
    month_start_day integer NOT NULL,
    week_start_day integer NOT NULL,
    amount bigint NOT NULL CHECK (amount > 0),
    PRIMARY KEY (ledger_id, cycle_unit, month_start_day, week_start_day, effective_from)
);
