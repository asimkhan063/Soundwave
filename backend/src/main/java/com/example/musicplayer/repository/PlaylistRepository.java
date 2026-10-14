package com.example.musicplayer.repository;

import com.example.musicplayer.model.Playlist;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {
    List<Playlist> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<Playlist> findByIdAndUserId(UUID id, UUID userId);
}
