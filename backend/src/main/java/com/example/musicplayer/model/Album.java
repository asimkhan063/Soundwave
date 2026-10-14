package com.example.musicplayer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "albums")
@Getter @Setter @NoArgsConstructor
public class Album {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(name = "artist_id")
    private UUID artistId;
    @Column(name = "cover_key")
    private String coverKey;
    @Column(name = "release_date")
    private LocalDate releaseDate;
}
