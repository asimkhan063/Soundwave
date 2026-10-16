CREATE TABLE playlist_external_tracks (
    playlist_id UUID NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
    track_id VARCHAR(80) NOT NULL,
    title VARCHAR(200) NOT NULL,
    artist_name VARCHAR(200) NOT NULL,
    image_url TEXT NOT NULL DEFAULT '',
    duration_seconds INTEGER NOT NULL CHECK (duration_seconds >= 0),
    stream_url TEXT NOT NULL,
    track_url TEXT NOT NULL,
    license_url TEXT NOT NULL DEFAULT '',
    position INTEGER NOT NULL CHECK (position >= 0),
    PRIMARY KEY (playlist_id, track_id),
    UNIQUE (playlist_id, position)
);
