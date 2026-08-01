-- Business rule change: every link must now expire within 30 days (previously, omitting a TTL
-- at creation meant "never expires", so expires_at could be NULL).
--
-- Grandfather pre-existing permanent links with a FRESH 30-day grace period starting now,
-- rather than retroactively computing created_at + 30 days: many of these rows are older than
-- 30 days, so a retroactive backfill would expire a large slice of the table the instant this
-- migration runs. A forward-looking grace window gives existing link owners genuine notice
-- instead of an immediate mass 410 Gone.
UPDATE links
SET expires_at = now() + INTERVAL '30 days',
    updated_at = now()
WHERE expires_at IS NULL
  AND status = 'ACTIVE';
