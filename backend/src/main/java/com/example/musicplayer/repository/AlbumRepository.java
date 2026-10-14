package com.example.musicplayer.repository;

import com.example.musicplayer.model.Album;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlbumRepository extends JpaRepository<Album, UUID> { }
