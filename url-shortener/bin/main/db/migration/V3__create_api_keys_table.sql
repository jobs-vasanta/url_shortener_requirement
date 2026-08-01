-- Backs the Premium Users feature: a caller presents a raw key via the X-Api-Key header, which
-- is resolved (by its SHA-256 hash - the raw key itself is never stored) to a tier that gates
-- rate limits and link-expiration rules. No key, or a key that doesn't match any active row
-- here, resolves to FREE tier - today's unchanged prior behavior.
CREATE TABLE api_keys (
    id BIGSERIAL PRIMARY KEY,
    key_hash VARCHAR(64) NOT NULL,
    tier VARCHAR(16) NOT NULL,
    label VARCHAR(128),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX idx_api_keys_key_hash ON api_keys (key_hash);
