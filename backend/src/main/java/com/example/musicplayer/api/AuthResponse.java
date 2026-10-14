package com.example.musicplayer.api;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds) { }
