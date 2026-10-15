package com.example.musicplayer.api;

import java.util.List;

public record SampleCatalogResponse(boolean enabled, List<SampleTrack> tracks) { }
