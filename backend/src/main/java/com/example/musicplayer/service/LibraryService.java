package com.example.musicplayer.service;

import com.example.musicplayer.api.PlaylistResponse;
import com.example.musicplayer.api.PlaylistSongResponse;
import com.example.musicplayer.api.SampleTrack;
import com.example.musicplayer.api.SongResponse;
import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.model.SongStatus;
import com.example.musicplayer.repository.PlaylistRepository;
import com.example.musicplayer.repository.SongRepository;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;
import java.net.URI;
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
    public PlaylistResponse addSong(UUID userId, UUID playlistId, String songId, SampleTrack sampleTrack) {
        Playlist playlist = getOwnedPlaylist(userId, playlistId);
        if (songId.startsWith("jamendo-")) {
            addJamendoTrack(playlistId, songId.substring("jamendo-".length()), sampleTrack);
            return toResponse(playlist);
        }
        if (sampleTrack != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unexpected catalog track data");
        UUID localSongId;
        try { localSongId = UUID.fromString(songId); }
        catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid songId", exception);
        }
        songs.findById(localSongId).filter(song -> song.getStatus() == SongStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found"));
        Integer position = jdbc.queryForObject(
                "SELECT COALESCE(MAX(position) + 1, 0) FROM (SELECT position FROM playlist_songs WHERE playlist_id = ? UNION ALL SELECT position FROM playlist_external_tracks WHERE playlist_id = ?) all_positions",
                Integer.class, playlistId, playlistId);
        jdbc.update("""
                INSERT INTO playlist_songs (playlist_id, song_id, position) VALUES (?, ?, ?)
                ON CONFLICT (playlist_id, song_id) DO NOTHING
                """, playlistId, localSongId, position == null ? 0 : position);
        return toResponse(playlist);
    }

    @Transactional
    public void removeSong(UUID userId, UUID playlistId, String songId) {
        getOwnedPlaylist(userId, playlistId);
        if (songId.startsWith("jamendo-")) {
            jdbc.update("DELETE FROM playlist_external_tracks WHERE playlist_id = ? AND track_id = ?",
                    playlistId, songId.substring("jamendo-".length()));
        } else {
            try { jdbc.update("DELETE FROM playlist_songs WHERE playlist_id = ? AND song_id = ?", playlistId, UUID.fromString(songId)); }
            catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid songId", exception);
            }
        }
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

    private void addJamendoTrack(UUID playlistId, String trackId, SampleTrack track) {
        if (track == null || track.id() == null || !trackId.equals(track.id())
                || !trackId.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Jamendo track data");
        }
        String title = requiredText(track.title(), 200, "title");
        String artist = requiredText(track.artistName(), 200, "artistName");
        if (track.durationSeconds() < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid track duration");
        String stream = allowedHttpsUrl(track.streamUrl(), "jamendo.com", true);
        String image = optionalHttpsUrl(track.imageUrl(), "jamendo.com");
        String trackUrl = allowedHttpsUrl(track.trackUrl(), "jamendo.com", true);
        String license = optionalHttpsUrl(track.licenseUrl(), "creativecommons.org");
        Integer position = jdbc.queryForObject(
                "SELECT COALESCE(MAX(position) + 1, 0) FROM (SELECT position FROM playlist_songs WHERE playlist_id = ? UNION ALL SELECT position FROM playlist_external_tracks WHERE playlist_id = ?) all_positions",
                Integer.class, playlistId, playlistId);
        jdbc.update("""
                INSERT INTO playlist_external_tracks
                    (playlist_id, track_id, title, artist_name, image_url, duration_seconds, stream_url, track_url, license_url, position)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (playlist_id, track_id) DO NOTHING
                """, playlistId, trackId, title, artist, image, track.durationSeconds(), stream, trackUrl, license,
                position == null ? 0 : position);
    }

    private static String requiredText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + field);
        }
        return value.trim();
    }

    private static String optionalHttpsUrl(String value, String allowedDomain) {
        if (value == null || value.isBlank()) return "";
        if ("creativecommons.org".equals(allowedDomain) && value.startsWith("http://creativecommons.org/")) {
            value = "https://" + value.substring("http://".length());
        }
        return allowedHttpsUrl(value, allowedDomain, false);
    }

    private static String allowedHttpsUrl(String value, String allowedDomain, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Required track URL is missing");
            return "";
        }
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !(host.equals(allowedDomain) || host.endsWith("." + allowedDomain))
                    || uri.getUserInfo() != null || value.length() > 2000) {
                throw new IllegalArgumentException("Disallowed URL");
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid catalog URL", exception);
        }
    }

    private PlaylistResponse toResponse(Playlist playlist) {
        record Positioned(int position, PlaylistSongResponse song) { }
        List<Positioned> positioned = new ArrayList<>();
        positioned.addAll(jdbc.query("""
                SELECT ps.position, s.id::text AS id, s.title, s.artist_id, s.album_id,
                       s.audio_key, s.duration_seconds
                FROM playlist_songs ps JOIN songs s ON s.id = ps.song_id
                WHERE ps.playlist_id = ? AND s.status = 'PUBLISHED'
                """, (rs, row) -> new Positioned(rs.getInt("position"), new PlaylistSongResponse(
                rs.getString("id"), rs.getString("title"), rs.getObject("artist_id", UUID.class),
                rs.getObject("album_id", UUID.class), rs.getString("audio_key"), rs.getInt("duration_seconds"),
                null, null, null, null, null, null)), playlist.getId()));
        positioned.addAll(jdbc.query("""
                SELECT position, track_id, title, artist_name, image_url, duration_seconds,
                       stream_url, track_url, license_url
                FROM playlist_external_tracks WHERE playlist_id = ?
                """, (rs, row) -> new Positioned(rs.getInt("position"), new PlaylistSongResponse(
                "jamendo-" + rs.getString("track_id"), rs.getString("title"), null, null, "",
                rs.getInt("duration_seconds"), "jamendo", rs.getString("stream_url"), rs.getString("image_url"),
                rs.getString("artist_name"), rs.getString("track_url"), rs.getString("license_url"))), playlist.getId()));
        List<PlaylistSongResponse> playlistSongs = positioned.stream().sorted(Comparator.comparingInt(Positioned::position))
                .map(Positioned::song).toList();
        return new PlaylistResponse(playlist.getId(), playlist.getName(), playlist.getCreatedAt(), playlistSongs);
    }

    private SongResponse toResponse(Song song) {
        return new SongResponse(song.getId(), song.getTitle(), song.getArtistId(), song.getAlbumId(),
                song.getAudioKey(), song.getDurationSeconds());
    }
}
