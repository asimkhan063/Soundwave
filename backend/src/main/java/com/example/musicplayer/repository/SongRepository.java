package com.example.musicplayer.repository;

import com.example.musicplayer.model.Song;
import com.example.musicplayer.model.SongStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SongRepository extends JpaRepository<Song, UUID> {
    List<Song> findByStatusOrderByCreatedAtDesc(SongStatus status);
    List<Song> findByStatusAndTitleContainingIgnoreCaseOrderByTitleAsc(SongStatus status, String title);

    @Query(value = "SELECT s.* FROM songs s LEFT JOIN song_play_counts p ON p.song_id = s.id " +
            "WHERE s.status = 'PUBLISHED' ORDER BY COALESCE(p.play_count, 0) DESC, s.created_at DESC LIMIT :limit",
            nativeQuery = true)
    List<Song> findTopPlayed(@Param("limit") int limit);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO song_play_counts (song_id, play_count) VALUES (:songId, 1) " +
            "ON CONFLICT (song_id) DO UPDATE SET play_count = song_play_counts.play_count + 1", nativeQuery = true)
    void recordPlay(@Param("songId") UUID songId);
}
