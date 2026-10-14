package com.example.musicplayer.api;

import com.example.musicplayer.service.CurrentUser;
import com.example.musicplayer.service.LibraryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api")
public class LibraryController {
    private final LibraryService library;
    private final CurrentUser currentUser;

    public LibraryController(LibraryService library, CurrentUser currentUser) {
        this.library = library;
        this.currentUser = currentUser;
    }

    public record CreatePlaylistRequest(@NotBlank @Size(max = 100) String name) { }
    public record AddSongRequest(UUID songId) { }

    @PostMapping("/playlists")
    @ResponseStatus(HttpStatus.CREATED)
    public PlaylistResponse createPlaylist(@Valid @RequestBody CreatePlaylistRequest request, Authentication auth) {
        return library.createPlaylist(currentUser.id(auth), request.name());
    }

    @GetMapping("/playlists")
    public List<PlaylistResponse> getPlaylists(Authentication auth) {
        return library.getPlaylists(currentUser.id(auth));
    }

    @PostMapping("/playlists/{playlistId}/songs")
    public PlaylistResponse addSong(@PathVariable UUID playlistId, @Valid @RequestBody AddSongRequest request,
                                    Authentication auth) {
        if (request.songId() == null) throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.BAD_REQUEST, "songId is required");
        return library.addSong(currentUser.id(auth), playlistId, request.songId());
    }

    @DeleteMapping("/playlists/{playlistId}/songs/{songId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSong(@PathVariable UUID playlistId, @PathVariable UUID songId, Authentication auth) {
        library.removeSong(currentUser.id(auth), playlistId, songId);
    }

    @GetMapping("/likes")
    public List<SongResponse> getLikedSongs(Authentication auth) {
        return library.getLikedSongs(currentUser.id(auth));
    }

    @PutMapping("/likes/{songId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void like(@PathVariable UUID songId, Authentication auth) {
        library.like(currentUser.id(auth), songId);
    }

    @DeleteMapping("/likes/{songId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlike(@PathVariable UUID songId, Authentication auth) {
        library.unlike(currentUser.id(auth), songId);
    }
}
