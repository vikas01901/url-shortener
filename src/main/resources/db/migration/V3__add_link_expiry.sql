-- Optional expiry. NULL means the link never expires, which keeps every
-- existing link working exactly as before.
ALTER TABLE links ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;

-- Supports the purge job's range scan.
CREATE INDEX idx_links_expires_at ON links (expires_at);
