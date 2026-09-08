CREATE TABLE users (
    id uuid PRIMARY KEY,
    email varchar(320) NOT NULL UNIQUE,
    password_hash varchar(255),
    display_name varchar(50) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT ck_users_display_name CHECK (btrim(display_name) <> ''),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'WITHDRAWN'))
);

CREATE TABLE ledgers (
    id uuid PRIMARY KEY,
    name varchar(100) NOT NULL,
    default_monthly_budget bigint NOT NULL,
    status varchar(20) NOT NULL,
    version integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT ck_ledgers_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_ledgers_budget CHECK (default_monthly_budget > 0),
    CONSTRAINT ck_ledgers_status CHECK (status IN ('ACTIVE', 'TERMINATING'))
);

CREATE TABLE ledger_members (
    ledger_id uuid NOT NULL REFERENCES ledgers(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    role varchar(20) NOT NULL,
    status varchar(20) NOT NULL,
    joined_at timestamptz NOT NULL DEFAULT current_timestamp,
    left_at timestamptz,
    PRIMARY KEY (ledger_id, user_id),
    CONSTRAINT ck_ledger_members_role CHECK (role IN ('MEMBER', 'ADMIN')),
    CONSTRAINT ck_ledger_members_status CHECK (status IN ('ACTIVE', 'LEFT'))
);

CREATE INDEX idx_members_ledger_status ON ledger_members (ledger_id, status);
CREATE INDEX idx_members_user_status ON ledger_members (user_id, status);

CREATE TABLE invitations (
    id uuid PRIMARY KEY,
    ledger_id uuid NOT NULL REFERENCES ledgers(id) ON DELETE CASCADE,
    created_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
    token_hash varchar(64) NOT NULL UNIQUE,
    revoked_at timestamptz,
    use_count bigint NOT NULL DEFAULT 0,
    last_used_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT ck_invitations_use_count CHECK (use_count >= 0)
);
