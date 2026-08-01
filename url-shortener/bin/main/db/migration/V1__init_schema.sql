-- Initial schema for links and click_events (see Architecture.md, Section 2.4).

CREATE TABLE links (
    id BIGSERIAL PRIMARY KEY,
    short_code VARCHAR(32) NOT NULL,
    original_url VARCHAR(2048) NOT NULL,
    status VARCHAR(16) NOT NULL,
    click_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_links_short_code ON links (short_code);
CREATE INDEX idx_links_status_expires_at ON links (status, expires_at);

CREATE TABLE click_events (
    id BIGSERIAL PRIMARY KEY,
    link_id BIGINT NOT NULL REFERENCES links (id),
    occurred_at TIMESTAMPTZ NOT NULL,
    referrer VARCHAR(2048),
    user_agent_hash VARCHAR(64),
    ip_hash VARCHAR(64)
);

CREATE INDEX idx_click_events_link_id_occurred_at ON click_events (link_id, occurred_at);
CREATE INDEX idx_click_events_occurred_at ON click_events (occurred_at);

