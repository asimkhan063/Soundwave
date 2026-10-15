package com.example.musicplayer.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(name = "media.storage-provider", havingValue = "local", matchIfMissing = true)
public class LocalAudioStorage implements AudioStorage {
    private static final long MAX_FILE_SIZE = 25L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp3", "wav", "ogg", "m4a", "aac");
    private static final Set<String> COVER_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private final Path root;

    public LocalAudioStorage(@Value("${media.storage-dir}") String storageDir) {
        this.root = Paths.get(storageDir).toAbsolutePath().normalize();
    }

    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an audio file to upload");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Audio file must be 25 MB or smaller");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = originalName.lastIndexOf('.');
        String extension = dot < 0 ? "" : originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supported audio formats: MP3, WAV, OGG, M4A, AAC");
        }

        String key = UUID.randomUUID() + "." + extension;
        Path destination = resolveKey(key);
        try {
            Files.createDirectories(root);
            try (var input = file.getInputStream(); var output = Files.newOutputStream(destination)) {
                input.transferTo(output);
            }
            return key;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save uploaded audio", exception);
        }
    }

    @Override
    public String saveCover(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a cover image");
        if (file.getSize() > 5L * 1024 * 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Cover image must be 5 MB or smaller");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!COVER_EXTENSIONS.contains(extension)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cover must be JPG, PNG, or WEBP");
        String key = "covers/" + UUID.randomUUID() + "." + extension;
        Path destination = root.resolve(key).normalize();
        if (!destination.startsWith(root)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cover key");
        try {
            Files.createDirectories(destination.getParent());
            try (var input = file.getInputStream(); var output = Files.newOutputStream(destination)) { input.transferTo(output); }
            return key;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save cover image", exception);
        }
    }

    public Resource load(String key) {
        Path audioFile = resolveKey(key);
        try {
            Resource resource = new UrlResource(audioFile.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio file not found");
            }
            return resource;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio file not found", exception);
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || !key.matches("(?i)[0-9a-f-]{36}\\.(mp3|wav|ogg|m4a|aac)")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid audio key");
        }
        try {
            Files.deleteIfExists(resolveKey(key));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not delete stored audio", exception);
        }
    }

    public String contentType(String key) {
        String extension = key.substring(key.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "ogg" -> "audio/ogg";
            case "m4a" -> "audio/mp4";
            case "aac" -> "audio/aac";
            default -> "application/octet-stream";
        };
    }

    private Path resolveKey(String key) {
        if (key == null || !(key.matches("(?i)[0-9a-f-]{36}\\.(mp3|wav|ogg|m4a|aac)") ||
                key.matches("(?i)covers/[0-9a-f-]{36}\\.(jpg|jpeg|png|webp)"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid media key");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid audio key");
        return resolved;
    }
}
