package com.example.musicplayer.service;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
@ConditionalOnProperty(name = "media.storage-provider", havingValue = "s3")
public class S3AudioStorage implements AudioStorage {
    private static final long MAX_FILE_SIZE = 25L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp3", "wav", "ogg", "m4a", "aac");
    private static final Set<String> COVER_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Duration PLAYBACK_URL_TTL = Duration.ofHours(1);

    private final String bucket;
    private final S3Client s3;
    private final S3Presigner presigner;

    public S3AudioStorage(@Value("${media.s3.endpoint}") String endpoint,
                          @Value("${media.s3.bucket}") String bucket,
                          @Value("${media.s3.access-key}") String accessKey,
                          @Value("${media.s3.secret-key}") String secretKey,
                          @Value("${media.s3.region:us-east-2}") String region) {
        if (endpoint.isBlank() || bucket.isBlank() || accessKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalStateException("AWS_ENDPOINT_URL_S3, S3_BUCKET, AWS_ACCESS_KEY_ID, and AWS_SECRET_ACCESS_KEY are required when AUDIO_STORAGE_PROVIDER=s3");
        }
        this.bucket = bucket;
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        var s3Configuration = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        URI endpointUri = URI.create(endpoint);
        this.s3 = S3Client.builder().endpointOverride(endpointUri).region(Region.of(region))
                .credentialsProvider(credentials).serviceConfiguration(s3Configuration).build();
        this.presigner = S3Presigner.builder().endpointOverride(endpointUri).region(Region.of(region))
                .credentialsProvider(credentials).serviceConfiguration(s3Configuration).build();
    }

    @Override
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
        try {
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                            .contentType(contentType(key)).build(),
                    RequestBody.fromBytes(file.getBytes()));
            return key;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not store audio in object storage", exception);
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
        try {
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType(key)).build(),
                    RequestBody.fromBytes(file.getBytes()));
            return key;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not store cover image", exception);
        }
    }

    @Override
    public Resource load(String key) {
        try {
            byte[] bytes = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
            return new ByteArrayResource(bytes);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio file not found", exception);
        }
    }

    @Override
    public Optional<String> playbackUrl(String key) {
        var request = GetObjectRequest.builder().bucket(bucket).key(key).build();
        var presignRequest = GetObjectPresignRequest.builder().signatureDuration(PLAYBACK_URL_TTL)
                .getObjectRequest(request).build();
        return Optional.of(presigner.presignGetObject(presignRequest).url().toString());
    }

    @Override
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
}
