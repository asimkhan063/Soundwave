CREATE TABLE song_play_counts (
    song_id UUID PRIMARY KEY REFERENCES songs(id) ON DELETE CASCADE,
    play_count BIGINT NOT NULL DEFAULT 0 CHECK (play_count >= 0)
);
