CREATE TABLE IF NOT EXISTS highscore_http_backoff_state (
    id SMALLINT PRIMARY KEY DEFAULT 1,
    cooldown_until TIMESTAMP WITH TIME ZONE NULL,
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    current_cooldown_ms BIGINT NOT NULL DEFAULT 0,
    last_status VARCHAR(50) NULL,
    last_reason TEXT NULL,
    last_failure_at TIMESTAMP WITH TIME ZONE NULL,
    last_success_at TIMESTAMP WITH TIME ZONE NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_highscore_http_backoff_singleton CHECK (id = 1),
    CONSTRAINT chk_highscore_http_backoff_failures_non_negative CHECK (consecutive_failures >= 0),
    CONSTRAINT chk_highscore_http_backoff_cooldown_non_negative CHECK (current_cooldown_ms >= 0)
);

INSERT INTO highscore_http_backoff_state (id, updated_at)
VALUES (1, now())
ON CONFLICT (id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_highscore_http_backoff_cooldown_until
    ON highscore_http_backoff_state (cooldown_until);
