package com.example.musicplayer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "songs")
@Getter @Setter @NoArgsConstructor
public class Song {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(name = "artist_id")
    private UUID artistId;
    @Column(name = "album_id")
    private UUID albumId;
    @Column(name = "audio_key", nullable = false)
    private String audioKey;
    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SongStatus status = SongStatus.PUBLISHED;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @jakarta.persistence.PrePersist
    void onCreate() { createdAt = Instant.now(); }
}
