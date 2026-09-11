ALTER TABLE users
    ADD COLUMN service_role varchar(20) NOT NULL DEFAULT 'USER';

UPDATE users
SET service_role = 'SERVICE_ADMIN'
WHERE id = (
    SELECT id
    FROM users
    WHERE status = 'ACTIVE'
    ORDER BY created_at, id
    LIMIT 1
);

ALTER TABLE users
    ADD CONSTRAINT ck_users_service_role
    CHECK (service_role IN ('USER', 'SERVICE_ADMIN'));

-- The serialized authentication principal changes with this migration.
-- Existing users sign in again with the new, separated service and ledger roles.
DELETE FROM spring_session;
