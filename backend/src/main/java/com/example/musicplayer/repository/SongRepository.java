package com.example.musicplayer.repository;

import com.example.musicplayer.model.Song;
import com.example.musicplayer.model.SongStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SongRepository extends JpaRepository<Song, UUID> {
    List<Song> findByStatusOrderByCreatedAtDesc(SongStatus status);
    List<Song> findByStatusAndTitleContainingIgnoreCaseOrderByTitleAsc(SongStatus status, String title);
}
