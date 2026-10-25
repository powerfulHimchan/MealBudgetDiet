CREATE TABLE widget_connections (
    id uuid PRIMARY KEY,
    session_id char(36) NOT NULL REFERENCES spring_session(session_id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ledger_id uuid NOT NULL REFERENCES ledgers(id) ON DELETE CASCADE,
    challenge varchar(64) UNIQUE,
    token_hash varchar(64) UNIQUE,
    claim_until timestamptz NOT NULL,
    expires_at timestamptz NOT NULL
);
CREATE INDEX widget_connections_session ON widget_connections(session_id);
CREATE INDEX widget_connections_expiry ON widget_connections(expires_at);
