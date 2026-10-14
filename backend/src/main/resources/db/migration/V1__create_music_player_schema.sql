CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE artists (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    image_url TEXT
);

CREATE TABLE albums (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    artist_id UUID REFERENCES artists(id) ON DELETE SET NULL,
    cover_key TEXT,
    release_date DATE
);

CREATE TABLE songs (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    artist_id UUID REFERENCES artists(id) ON DELETE SET NULL,
    album_id UUID REFERENCES albums(id) ON DELETE SET NULL,
    audio_key TEXT NOT NULL,
    duration_seconds INTEGER NOT NULL CHECK (duration_seconds >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX songs_title_idx ON songs (lower(title));

CREATE TABLE playlists (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE playlist_songs (
    playlist_id UUID NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
    song_id UUID NOT NULL REFERENCES songs(id) ON DELETE CASCADE,
    position INTEGER NOT NULL CHECK (position >= 0),
    PRIMARY KEY (playlist_id, song_id),
    UNIQUE (playlist_id, position)
);

CREATE TABLE likes (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    song_id UUID NOT NULL REFERENCES songs(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, song_id)
);
