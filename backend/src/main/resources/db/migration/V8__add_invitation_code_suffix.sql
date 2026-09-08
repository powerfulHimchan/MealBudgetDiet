ALTER TABLE invitations
    ADD COLUMN code_suffix varchar(4) NOT NULL DEFAULT '????';

ALTER TABLE invitations
    ALTER COLUMN code_suffix DROP DEFAULT;
