CREATE TABLE images (
    id uuid PRIMARY KEY,
    ledger_id uuid NOT NULL REFERENCES ledgers(id) ON DELETE CASCADE,
    uploaded_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
    purpose varchar(20) NOT NULL,
    storage_key varchar(500) NOT NULL UNIQUE,
    mime_type varchar(50) NOT NULL,
    size_bytes bigint NOT NULL,
    width integer NOT NULL,
    height integer NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    activated_at timestamptz,
    CONSTRAINT chk_images_purpose CHECK (purpose IN ('EXPENSE', 'PROFILE')),
    CONSTRAINT chk_images_status CHECK (status IN ('TEMP', 'ACTIVE')),
    CONSTRAINT chk_images_size CHECK (size_bytes > 0),
    CONSTRAINT chk_images_dimensions CHECK (width > 0 AND height > 0)
);

ALTER TABLE users ADD COLUMN profile_image_id uuid;
ALTER TABLE users ADD CONSTRAINT fk_users_profile_image
    FOREIGN KEY (profile_image_id) REFERENCES images(id) ON DELETE SET NULL;
CREATE UNIQUE INDEX uk_users_profile_image ON users (profile_image_id)
    WHERE profile_image_id IS NOT NULL;

CREATE TABLE expense_images (
    expense_id uuid NOT NULL REFERENCES expenses(id) ON DELETE CASCADE,
    image_id uuid NOT NULL REFERENCES images(id) ON DELETE CASCADE,
    sort_order integer NOT NULL,
    PRIMARY KEY (expense_id, image_id),
    CONSTRAINT uk_expense_images_image UNIQUE (image_id),
    CONSTRAINT uk_expense_images_order UNIQUE (expense_id, sort_order),
    CONSTRAINT chk_expense_images_order CHECK (sort_order BETWEEN 0 AND 2)
);

CREATE INDEX idx_images_temp_created ON images (created_at) WHERE status = 'TEMP';
