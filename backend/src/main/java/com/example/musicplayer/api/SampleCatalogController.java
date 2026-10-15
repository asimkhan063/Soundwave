package com.example.musicplayer.api;

import com.example.musicplayer.service.JamendoCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/samples")
public class SampleCatalogController {
    private final JamendoCatalogService catalog;

    public SampleCatalogController(JamendoCatalogService catalog) { this.catalog = catalog; }

    @GetMapping
    public SampleCatalogResponse samples(@RequestParam(required = false) String q) {
        String query = q == null ? "" : q.trim();
        if (query.length() > 100) query = query.substring(0, 100);
        List<SampleTrack> tracks = catalog.search(query);
        return new SampleCatalogResponse(catalog.isEnabled(), tracks);
    }
}
