package com.example.musicplayer.api;

import java.util.UUID;

public record SongResponse(
        UUID id,
        String title,
        UUID artistId,
        UUID albumId,
        String audioKey,
        int durationSeconds) { }
