package com.example.musicplayer.repository;

import com.example.musicplayer.model.Artist;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtistRepository extends JpaRepository<Artist, UUID> { }
