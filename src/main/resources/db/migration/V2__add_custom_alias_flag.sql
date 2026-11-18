-- Marks links whose code was chosen by the caller instead of generated.
-- Additive and backward compatible: existing rows default to FALSE, and the
-- previous application version keeps working because it never reads the column.
ALTER TABLE links ADD COLUMN custom_alias BOOLEAN DEFAULT FALSE NOT NULL;
