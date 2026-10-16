package com.example.musicplayer.api;

import com.example.musicplayer.service.JamendoCatalogService;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/samples")
public class SampleCatalogController {
    private static final Set<String> GENRES = Set.of("pop", "rock", "electronic", "hiphop", "jazz", "chillout", "classical");
    private final JamendoCatalogService catalog;

    public SampleCatalogController(JamendoCatalogService catalog) { this.catalog = catalog; }

    @GetMapping
    public SampleCatalogResponse samples(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) String genre) {
        String query = q == null ? "" : q.trim();
        if (query.length() > 100) query = query.substring(0, 100);
        String selectedGenre = genre == null ? "" : genre.trim().toLowerCase(Locale.ROOT);
        if (!selectedGenre.isBlank() && !GENRES.contains(selectedGenre)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Unsupported music genre");
        }
        List<SampleTrack> tracks = catalog.search(query, selectedGenre);
        return new SampleCatalogResponse(catalog.isEnabled(), tracks);
    }
}
