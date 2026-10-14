package com.example.musicplayer.service;

import com.example.musicplayer.api.PlaylistResponse;
import com.example.musicplayer.api.SongResponse;
import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.model.SongStatus;
import com.example.musicplayer.repository.PlaylistRepository;
import com.example.musicplayer.repository.SongRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LibraryService {
    private final JdbcTemplate jdbc;
    private final PlaylistRepository playlists;
    private final SongRepository songs;

    public LibraryService(JdbcTemplate jdbc, PlaylistRepository playlists, SongRepository songs) {
        this.jdbc = jdbc;
        this.playlists = playlists;
        this.songs = songs;
    }

    @Transactional
    public PlaylistResponse createPlaylist(UUID userId, String name) {
        Playlist playlist = new Playlist();
        playlist.setUserId(userId);
        playlist.setName(name.trim());
        return toResponse(playlists.save(playlist));
    }

    @Transactional(readOnly = true)
    public List<PlaylistResponse> getPlaylists(UUID userId) {
        return playlists.findAllByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public PlaylistResponse addSong(UUID userId, UUID playlistId, UUID songId) {
        Playlist playlist = getOwnedPlaylist(userId, playlistId);
        songs.findById(songId).filter(song -> song.getStatus() == SongStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found"));
        Integer position = jdbc.queryForObject(
                "SELECT COALESCE(MAX(position) + 1, 0) FROM playlist_songs WHERE playlist_id = ?",
                Integer.class, playlistId);
        jdbc.update("""
                INSERT INTO playlist_songs (playlist_id, song_id, position) VALUES (?, ?, ?)
                ON CONFLICT (playlist_id, song_id) DO NOTHING
                """, playlistId, songId, position == null ? 0 : position);
        return toResponse(playlist);
    }

    @Transactional
    public void removeSong(UUID userId, UUID playlistId, UUID songId) {
        getOwnedPlaylist(userId, playlistId);
        jdbc.update("DELETE FROM playlist_songs WHERE playlist_id = ? AND song_id = ?", playlistId, songId);
    }

    @Transactional
    public void like(UUID userId, UUID songId) {
        songs.findById(songId).filter(song -> song.getStatus() == SongStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found"));
        jdbc.update("INSERT INTO likes (user_id, song_id) VALUES (?, ?) ON CONFLICT DO NOTHING", userId, songId);
    }

    @Transactional
    public void unlike(UUID userId, UUID songId) {
        jdbc.update("DELETE FROM likes WHERE user_id = ? AND song_id = ?", userId, songId);
    }

    @Transactional(readOnly = true)
    public List<SongResponse> getLikedSongs(UUID userId) {
        List<UUID> ids = jdbc.query("SELECT song_id FROM likes WHERE user_id = ? ORDER BY created_at DESC",
                (rs, row) -> rs.getObject("song_id", UUID.class), userId);
        return ids.stream().map(songs::findById).flatMap(java.util.Optional::stream)
                .filter(song -> song.getStatus() == SongStatus.PUBLISHED).map(this::toResponse).toList();
    }

    private Playlist getOwnedPlaylist(UUID userId, UUID playlistId) {
        return playlists.findByIdAndUserId(playlistId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not found"));
    }

    private PlaylistResponse toResponse(Playlist playlist) {
        List<SongResponse> playlistSongs = jdbc.query(
                "SELECT song_id FROM playlist_songs WHERE playlist_id = ? ORDER BY position",
                (rs, row) -> rs.getObject("song_id", UUID.class), playlist.getId())
                .stream().map(songs::findById).flatMap(java.util.Optional::stream)
                .filter(song -> song.getStatus() == SongStatus.PUBLISHED).map(this::toResponse).toList();
        return new PlaylistResponse(playlist.getId(), playlist.getName(), playlist.getCreatedAt(), playlistSongs);
    }

    private SongResponse toResponse(Song song) {
        return new SongResponse(song.getId(), song.getTitle(), song.getArtistId(), song.getAlbumId(),
                song.getAudioKey(), song.getDurationSeconds());
    }
}
