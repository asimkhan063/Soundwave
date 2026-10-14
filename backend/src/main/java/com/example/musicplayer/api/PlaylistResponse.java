package com.example.musicplayer.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlaylistResponse(UUID id, String name, Instant createdAt, List<SongResponse> songs) { }
