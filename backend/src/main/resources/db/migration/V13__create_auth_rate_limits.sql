CREATE TABLE auth_rate_limits (
    scope varchar(40) NOT NULL,
    subject_hash varchar(64) NOT NULL,
    window_started_at timestamptz NOT NULL,
    attempt_count integer NOT NULL CHECK (attempt_count > 0),
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (scope, subject_hash)
);

CREATE INDEX idx_auth_rate_limits_updated_at
    ON auth_rate_limits (updated_at);
