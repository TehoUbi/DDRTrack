-- Players and their API tokens. Only a SHA-256 of each token is stored;
-- the token itself is shown to the client once, at registration.

CREATE TABLE players (
    id           UUID PRIMARY KEY,
    display_name TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT display_name_length CHECK (char_length(display_name) BETWEEN 2 AND 24)
);

-- Display names are how rivals find each other, so they are unique
-- regardless of case.
CREATE UNIQUE INDEX players_display_name_key ON players (lower(display_name));

CREATE TABLE api_tokens (
    token_hash   BYTEA PRIMARY KEY,
    player_id    UUID        NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ
);

CREATE INDEX api_tokens_player ON api_tokens (player_id);
