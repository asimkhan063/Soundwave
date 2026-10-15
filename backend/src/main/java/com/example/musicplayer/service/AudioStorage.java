package com.example.musicplayer.service;

import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface AudioStorage {
    String save(MultipartFile file);
    String saveCover(MultipartFile file);
    void delete(String key);
    Resource load(String key);
    String contentType(String key);

    /** A private, short-lived URL for remote storage; empty for local files. */
    default Optional<String> playbackUrl(String key) {
        return Optional.empty();
    }
}
