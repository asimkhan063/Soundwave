package com.example.musicplayer.api;

public record SampleTrack(
        String id,
        String title,
        String artistName,
        String imageUrl,
        int durationSeconds,
        String streamUrl,
        String trackUrl,
        String licenseUrl) { }
