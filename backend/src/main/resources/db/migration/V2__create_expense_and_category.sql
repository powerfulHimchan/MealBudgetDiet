CREATE TABLE categories (
    id uuid PRIMARY KEY,
    ledger_id uuid NOT NULL REFERENCES ledgers(id) ON DELETE CASCADE,
    name varchar(50) NOT NULL,
    sort_order integer NOT NULL,
    version integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT ck_categories_name CHECK (btrim(name) <> '')
);

CREATE UNIQUE INDEX uk_categories_ledger_name ON categories (ledger_id, lower(name));

CREATE TABLE expenses (
    id uuid PRIMARY KEY,
    ledger_id uuid NOT NULL REFERENCES ledgers(id) ON DELETE CASCADE,
    category_id uuid REFERENCES categories(id) ON DELETE SET NULL,
    amount bigint NOT NULL,
    spent_on date NOT NULL,
    merchant varchar(100),
    memo text,
    version integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT ck_expenses_amount CHECK (amount > 0)
);

CREATE INDEX idx_expenses_ledger_spent_created
    ON expenses (ledger_id, spent_on DESC, created_at DESC, id DESC);
CREATE INDEX idx_expenses_ledger_category_spent
    ON expenses (ledger_id, category_id, spent_on);
