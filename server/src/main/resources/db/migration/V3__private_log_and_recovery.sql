-- Account recovery: a code shown to the player once, stored only as a
-- SHA-256 hash like the API tokens. Using it issues a new token.
ALTER TABLE players ADD COLUMN recovery_hash BYTEA UNIQUE;

-- Each player's whole play log, private to them: what the app backs up and
-- restores on a new phone. Unlike scores (the public leaderboard) it keeps
-- every play, including ones the leaderboard rejects, FAST/SLOW and notes,
-- and deletions as tombstones. The server treats each entry as opaque JSON
-- apart from what it needs to merge and page.
CREATE SEQUENCE log_revision;

CREATE TABLE log_entries (
    player_id  UUID    NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    id         TEXT    NOT NULL,
    data       TEXT    NOT NULL,
    -- The app's edit time: the newer copy of an entry wins.
    updated_at BIGINT  NOT NULL,
    -- Server-assigned and increasing: restores page through it, so entries
    -- that share an updated_at are never skipped.
    revision   BIGINT  NOT NULL DEFAULT nextval('log_revision'),
    PRIMARY KEY (player_id, id)
);

CREATE INDEX log_entries_revision ON log_entries (player_id, revision);
