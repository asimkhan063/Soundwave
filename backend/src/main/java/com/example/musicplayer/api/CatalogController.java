package com.example.musicplayer.api;

import com.example.musicplayer.model.Album;
import com.example.musicplayer.model.Artist;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.model.SongStatus;
import com.example.musicplayer.repository.AlbumRepository;
import com.example.musicplayer.repository.ArtistRepository;
import com.example.musicplayer.repository.SongRepository;
import com.example.musicplayer.service.AudioStorage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final SongRepository songs;
    private final ArtistRepository artists;
    private final AlbumRepository albums;
    private final AudioStorage audioStorage;

    public CatalogController(SongRepository songs, ArtistRepository artists, AlbumRepository albums, AudioStorage audioStorage) {
        this.songs = songs;
        this.artists = artists;
        this.albums = albums;
        this.audioStorage = audioStorage;
    }

    public record CreateSongRequest(@NotBlank @Size(max = 200) String title,
                                    UUID artistId, UUID albumId,
                                    @NotBlank @Size(max = 1000) String audioKey,
                                    @Min(0) int durationSeconds) { }
    public record CreateArtistRequest(@NotBlank @Size(max = 200) String name, String imageUrl) { }
    public record CreateAlbumRequest(@NotBlank @Size(max = 200) String title, UUID artistId,
                                     String coverKey, java.time.LocalDate releaseDate) { }

    @GetMapping("/songs")
    public List<SongResponse> listSongs() {
        return songs.findByStatusOrderByCreatedAtDesc(SongStatus.PUBLISHED).stream().map(this::toResponse).toList();
    }

    @GetMapping("/charts/top")
    public List<SongResponse> topPlayed(@RequestParam(defaultValue = "50") @Min(1) @jakarta.validation.constraints.Max(50) int limit) {
        return songs.findTopPlayed(limit).stream().map(this::toResponse).toList();
    }

    @PostMapping("/songs/{id}/play")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordPlay(@PathVariable UUID id) {
        if (!songs.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found");
        songs.recordPlay(id);
    }

    @GetMapping("/songs/{id}")
    public SongResponse getSong(@PathVariable UUID id) {
        Song song = songs.findById(id).filter(s -> s.getStatus() == SongStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found"));
        return toResponse(song);
    }

    @GetMapping("/search")
    public List<SongResponse> search(@RequestParam @NotBlank @Size(max = 100) String q) {
        return songs.findByStatusAndTitleContainingIgnoreCaseOrderByTitleAsc(SongStatus.PUBLISHED, q.trim())
                .stream().map(this::toResponse).toList();
    }

    @GetMapping("/artists")
    public List<Artist> listArtists() { return artists.findAll(); }

    @GetMapping("/albums")
    public List<Album> listAlbums() { return albums.findAll(); }

    @PostMapping("/admin/artists")
    public Artist createArtist(@Valid @RequestBody CreateArtistRequest request) {
        Artist artist = new Artist();
        artist.setName(request.name().trim());
        artist.setImageUrl(request.imageUrl());
        return artists.save(artist);
    }

    @PostMapping("/admin/albums")
    public Album createAlbum(@Valid @RequestBody CreateAlbumRequest request) {
        if (request.artistId() != null && !artists.existsById(request.artistId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Artist does not exist");
        }
        Album album = new Album();
        album.setTitle(request.title().trim());
        album.setArtistId(request.artistId());
        album.setCoverKey(request.coverKey());
        album.setReleaseDate(request.releaseDate());
        return albums.save(album);
    }

    @PostMapping("/admin/songs")
    public SongResponse createSong(@Valid @RequestBody CreateSongRequest request) {
        if (request.artistId() != null && !artists.existsById(request.artistId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Artist does not exist");
        }
        if (request.albumId() != null && !albums.existsById(request.albumId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Album does not exist");
        }
        Song song = new Song();
        song.setTitle(request.title().trim());
        song.setArtistId(request.artistId());
        song.setAlbumId(request.albumId());
        song.setAudioKey(request.audioKey().trim());
        song.setDurationSeconds(request.durationSeconds());
        song.setStatus(SongStatus.PUBLISHED);
        return toResponse(songs.save(song));
    }

    @PostMapping(value = "/admin/songs/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SongResponse uploadSong(@RequestParam String title,
                                   @RequestParam(defaultValue = "0") int durationSeconds,
                                   @RequestParam(required = false) UUID artistId,
                                   @RequestParam(required = false) UUID albumId,
                                   @RequestParam("audioFile") MultipartFile audioFile) {
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required and must be 200 characters or fewer");
        }
        if (durationSeconds < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duration cannot be negative");
        if (artistId != null && !artists.existsById(artistId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Artist does not exist");
        }
        if (albumId != null && !albums.existsById(albumId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Album does not exist");
        }

        Song song = new Song();
        song.setTitle(title.trim());
        song.setArtistId(artistId);
        song.setAlbumId(albumId);
        song.setAudioKey(audioStorage.save(audioFile));
        song.setDurationSeconds(durationSeconds);
        song.setStatus(SongStatus.PUBLISHED);
        return toResponse(songs.save(song));
    }

    @GetMapping("/songs/{id}/stream")
    public ResponseEntity<?> streamSong(@PathVariable UUID id) throws java.io.IOException {
        Song song = songs.findById(id).filter(s -> s.getStatus() == SongStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found"));
        var playbackUrl = audioStorage.playbackUrl(song.getAudioKey());
        if (playbackUrl.isPresent()) {
            return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                    .header(HttpHeaders.LOCATION, playbackUrl.get())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .build();
        }
        Resource audio = audioStorage.load(song.getAudioKey());
        return ResponseEntity.ok()
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(audioStorage.contentType(song.getAudioKey())))
                .contentLength(audio.contentLength())
                .body(audio);
    }

    private SongResponse toResponse(Song song) {
        return new SongResponse(song.getId(), song.getTitle(), song.getArtistId(), song.getAlbumId(),
                song.getAudioKey(), song.getDurationSeconds());
    }
}
