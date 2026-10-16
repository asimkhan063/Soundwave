package com.example.musicplayer.api;

import java.util.UUID;

public record PlaylistSongResponse(
        String id,
        String title,
        UUID artistId,
        UUID albumId,
        String audioKey,
        int durationSeconds,
        String source,
        String streamUrl,
        String artworkUrl,
        String artistName,
        String trackUrl,
        String licenseUrl) { }
