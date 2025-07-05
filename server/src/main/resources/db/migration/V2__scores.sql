-- Charts are identified the way the app identifies them: a slug of the
-- title (core's SongKey) plus style and difficulty. The title and level are
-- whatever was last submitted, since revivals sometimes re-rate a chart.

CREATE TABLE charts (
    id         BIGSERIAL PRIMARY KEY,
    song_key   TEXT     NOT NULL,
    song_title TEXT     NOT NULL,
    style      TEXT     NOT NULL CHECK (style IN ('SINGLE', 'DOUBLE')),
    difficulty TEXT     NOT NULL CHECK (difficulty IN ('BEGINNER', 'BASIC', 'DIFFICULT', 'EXPERT', 'CHALLENGE')),
    level      SMALLINT NOT NULL CHECK (level BETWEEN 1 AND 19),
    UNIQUE (song_key, style, difficulty)
);

-- Every submitted play. client_id is the app's play id, which makes
-- resubmission after a dropped connection idempotent.
CREATE TABLE scores (
    id           BIGSERIAL PRIMARY KEY,
    player_id    UUID        NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    chart_id     BIGINT      NOT NULL REFERENCES charts (id),
    client_id    TEXT        NOT NULL,
    score        INT         NOT NULL CHECK (score BETWEEN 0 AND 1000000 AND score % 10 = 0),
    ex_score     INT         NOT NULL CHECK (ex_score >= 0),
    grade        TEXT        NOT NULL,
    lamp         TEXT        NOT NULL,
    marvelous    INT         NOT NULL CHECK (marvelous >= 0),
    perfect      INT         NOT NULL CHECK (perfect >= 0),
    great        INT         NOT NULL CHECK (great >= 0),
    good         INT         NOT NULL CHECK (good >= 0),
    ok           INT         NOT NULL CHECK (ok >= 0),
    miss         INT         NOT NULL CHECK (miss >= 0),
    max_combo    INT         NOT NULL CHECK (max_combo >= 0),
    failed       BOOLEAN     NOT NULL,
    played_at    TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (player_id, client_id)
);

CREATE INDEX scores_chart_rank ON scores (chart_id, score DESC, ex_score DESC);
CREATE INDEX scores_player_chart ON scores (player_id, chart_id);

-- One row per player and chart: the best score, then the best EX score,
-- then the earliest play to reach it.
CREATE VIEW personal_bests AS
SELECT DISTINCT ON (player_id, chart_id) *
FROM scores
ORDER BY player_id, chart_id, score DESC, ex_score DESC, played_at ASC;
