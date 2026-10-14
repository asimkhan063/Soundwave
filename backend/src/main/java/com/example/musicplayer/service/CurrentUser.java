package com.example.musicplayer.service;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    public UUID id(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return UUID.fromString(jwt.getToken().getClaimAsString("uid"));
        }
        throw new IllegalStateException("Expected a JWT-authenticated user");
    }
}
