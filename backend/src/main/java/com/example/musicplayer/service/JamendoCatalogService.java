package com.example.musicplayer.service;

import com.example.musicplayer.api.SampleTrack;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class JamendoCatalogService {
    private final String clientId;
    private final RestClient client = RestClient.create();

    public JamendoCatalogService(@Value("${jamendo.client-id:}") String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
    }

    public boolean isEnabled() { return !clientId.isBlank(); }

    public List<SampleTrack> search(String query, String genre) {
        if (!isEnabled()) return List.of();
        var uriBuilder = UriComponentsBuilder.fromUriString("https://api.jamendo.com/v3.0/tracks/")
                .queryParam("client_id", clientId)
                .queryParam("format", "json")
                .queryParam("limit", 20)
                .queryParam("type", "single albumtrack")
                .queryParam("audioformat", "mp32")
                .queryParam("imagesize", 200)
                .queryParam("include", "licenses");
        if (query != null && !query.isBlank()) uriBuilder.queryParam("search", query.trim());
        else uriBuilder.queryParam("order", "popularity_week");
        if (genre != null && !genre.isBlank()) uriBuilder.queryParam("tags", genre.trim());

        try {
            JsonNode response = client.get().uri(uriBuilder.build().encode().toUri()).retrieve().body(JsonNode.class);
            JsonNode results = response == null ? null : response.path("results");
            List<SampleTrack> tracks = new ArrayList<>();
            if (results == null || !results.isArray()) return tracks;
            for (JsonNode item : results) {
                String audio = text(item, "audio");
                String id = text(item, "id");
                if (audio.isBlank() || id.isBlank()) continue;
                String trackUrl = text(item, "shareurl");
                if (trackUrl.isBlank()) trackUrl = "https://www.jamendo.com/track/" + id;
                String licenseUrl = text(item, "license_ccurl");
                tracks.add(new SampleTrack(id, text(item, "name"), text(item, "artist_name"),
                        firstText(item, "album_image", "image"), item.path("duration").asInt(0),
                        audio, trackUrl, licenseUrl));
            }
            return tracks;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Free music catalog is temporarily unavailable", exception);
        }
    }

    private static String firstText(JsonNode node, String first, String second) {
        String value = text(node, first);
        return value.isBlank() ? text(node, second) : value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }
}
