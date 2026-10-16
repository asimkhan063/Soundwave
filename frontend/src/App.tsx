import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { api, type Album, type Artist, type Playlist, type Song } from './api'

const tokenKey = 'soundwave.access-token'

export default function App() {
  const [songs, setSongs] = useState<Song[]>([])
  const [sampleSongs, setSampleSongs] = useState<Song[]>([])
  const [sampleCatalogEnabled, setSampleCatalogEnabled] = useState(false)
  const [topSongs, setTopSongs] = useState<Song[]>([])
  const [artists, setArtists] = useState<Artist[]>([])
  const [albums, setAlbums] = useState<Album[]>([])
  const [playlists, setPlaylists] = useState<Playlist[]>([])
  const [likedSongs, setLikedSongs] = useState<Song[]>([])
  const [selectedPlaylistId, setSelectedPlaylistId] = useState('')
  const [likedIds, setLikedIds] = useState<string[]>([])
  const [token, setToken] = useState(() => localStorage.getItem(tokenKey) ?? '')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [playlistName, setPlaylistName] = useState('')
  const [query, setQuery] = useState('')
  const [isSearching, setIsSearching] = useState(false)
  const searchRequestId = useRef(0)
  const [showLikedOnly, setShowLikedOnly] = useState(false)
  const [isRegister, setIsRegister] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [currentSong, setCurrentSong] = useState<Song | null>(null)
  const [queue, setQueue] = useState<Song[]>([])
  const [isPlaying, setIsPlaying] = useState(false)
  const [currentTime, setCurrentTime] = useState(0)
  const [duration, setDuration] = useState(0)
  const audioRef = useRef<HTMLAudioElement>(null)
  const isAdmin = tokenRole(token) === 'ADMIN'
  const visibleSongs = showLikedOnly
    ? likedSongs.filter((song) => `${song.title} ${song.artistName ?? artists.find((artist) => artist.id === song.artistId)?.name ?? ''}`.toLowerCase().includes(query.trim().toLowerCase()))
    : [...songs, ...sampleSongs]

  function browseCharts() {
    setShowLikedOnly(false)
    setQuery('')
    void loadCatalog()
    document.getElementById('catalog')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  function focusSearch() {
    setShowLikedOnly(false)
    document.getElementById('catalog')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    window.setTimeout(() => document.querySelector<HTMLInputElement>('.search-form input')?.focus(), 350)
  }

  const loadCatalog = useCallback(async (search = '') => {
    const requestId = ++searchRequestId.current
    setIsSearching(true)
    const [localResult, sampleResult] = await Promise.allSettled([api.songs(search), api.samples(search)])
    if (requestId !== searchRequestId.current) return
    if (localResult.status === 'fulfilled') setSongs(localResult.value)
    if (sampleResult.status === 'fulfilled') {
      setSampleCatalogEnabled(sampleResult.value.enabled)
      setSampleSongs(sampleResult.value.tracks.map((track) => ({
        id: `jamendo-${track.id}`, title: track.title, artistId: null, albumId: null, audioKey: '',
        durationSeconds: track.durationSeconds, source: 'jamendo' as const, streamUrl: track.streamUrl,
        artworkUrl: track.imageUrl, artistName: track.artistName, trackUrl: track.trackUrl, licenseUrl: track.licenseUrl,
      })))
    }
    if (localResult.status === 'rejected' && sampleResult.status === 'rejected') {
      setError(localResult.reason instanceof Error ? localResult.reason.message : 'Songs load nahi ho paaye')
    } else setError('')
    setIsSearching(false)
  }, [])

  const loadTopSongs = useCallback(async () => {
    try { setTopSongs(await api.topSongs()) } catch { /* Charts can load after the catalog. */ }
  }, [])

  const loadLibrary = useCallback(async (authToken: string) => {
    try {
      const [userPlaylists, likedSongs] = await Promise.all([api.playlists(authToken), api.likedSongs(authToken)])
      setPlaylists(userPlaylists)
      setLikedSongs(likedSongs)
      setLikedIds(likedSongs.map((song) => song.id))
    } catch {
      localStorage.removeItem(tokenKey)
      setToken('')
      setPlaylists([])
      setLikedSongs([])
      setLikedIds([])
    }
  }, [])

  useEffect(() => { void loadCatalog(); void loadTopSongs() }, [loadCatalog, loadTopSongs])
  useEffect(() => {
    const search = query.trim()
    if (!search || showLikedOnly) return
    const timer = window.setTimeout(() => { void loadCatalog(search) }, 350)
    return () => window.clearTimeout(timer)
  }, [query, showLikedOnly, loadCatalog])
  useEffect(() => {
    void Promise.all([api.artists(), api.albums()]).then(([artistList, albumList]) => {
      setArtists(artistList)
      setAlbums(albumList)
    }).catch(() => setError('Artist/album catalog load nahi hua'))
  }, [])
  useEffect(() => {
    if (token) void loadLibrary(token)
    else { setPlaylists([]); setLikedSongs([]); setLikedIds([]) }
  }, [token, loadLibrary])

  useEffect(() => {
    const audio = audioRef.current
    if (!audio) return
    if (isPlaying && currentSong) {
      void audio.play().catch(() => {
        setIsPlaying(false)
        setError('Audio play nahi hua. File format check karo.')
      })
    } else audio.pause()
  }, [isPlaying, currentSong])

  async function submitAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    try {
      const result = isRegister ? await api.register(email, password) : await api.login(email, password)
      localStorage.setItem(tokenKey, result.accessToken)
      setToken(result.accessToken)
      setPassword('')
      setNotice(isRegister ? 'Account ban gaya!' : 'Login ho gaya!')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login nahi ho paaya')
    }
  }

  async function createPlaylist(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || !playlistName.trim()) return
    try {
      const created = await api.createPlaylist(playlistName, token)
      setPlaylists((current) => [created, ...current])
      setPlaylistName('')
      setNotice('Playlist create ho gayi!')
      setError('')
    } catch (err) { setError(err instanceof Error ? err.message : 'Playlist create nahi hui') }
  }

  async function toggleLike(song: Song) {
    if (!token) { setError('Like karne ke liye pehle login karo.'); return }
    const isLiked = likedIds.includes(song.id)
    try {
      if (isLiked) await api.unlike(song.id, token)
      else await api.like(song.id, token)
      setLikedIds((ids) => isLiked ? ids.filter((id) => id !== song.id) : [...ids, song.id])
      setLikedSongs((items) => isLiked ? items.filter((item) => item.id !== song.id) : [song, ...items])
      setError('')
    } catch (err) { setError(err instanceof Error ? err.message : 'Like save nahi hua') }
  }

  async function addToPlaylist(playlistId: string, songId: string) {
    if (!token) return
    try {
      const updated = await api.addSong(playlistId, songId, token)
      setPlaylists((items) => items.map((item) => item.id === updated.id ? updated : item))
      setNotice('Song playlist mein add ho gaya!')
      setError('')
    } catch (err) { setError(err instanceof Error ? err.message : 'Song add nahi hua') }
  }

  async function removeFromPlaylist(playlist: Playlist, song: Song) {
    if (!token) return
    try {
      await api.removeSong(playlist.id, song.id, token)
      setPlaylists((items) => items.map((item) => item.id === playlist.id
        ? { ...item, songs: item.songs.filter((track) => track.id !== song.id) }
        : item))
      setQueue((items) => items.filter((track) => track.id !== song.id))
      setError('')
    } catch (err) { setError(err instanceof Error ? err.message : 'Song remove nahi hua') }
  }

  function playPlaylist(playlist: Playlist) {
    if (playlist.songs.length === 0) return
    setQueue(playlist.songs)
    setSelectedPlaylistId(playlist.id)
    setCurrentSong(playlist.songs[0])
    setCurrentTime(0)
    setDuration(playlist.songs[0].durationSeconds)
    setIsPlaying(true)
  }

  function playPlaylistSong(playlist: Playlist, song: Song) {
    setQueue(playlist.songs)
    setCurrentSong(song)
    setCurrentTime(0)
    setDuration(song.durationSeconds)
    setIsPlaying(true)
  }

  async function uploadTrack(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token) return
    const form = event.currentTarget
    try {
      const song = await api.uploadSong(new FormData(form), token)
      setSongs((items) => [song, ...items])
      form.reset()
      setNotice('Track upload ho gaya! Ab Play dabao.')
      setError('')
    } catch (err) { setError(err instanceof Error ? err.message : 'Upload nahi ho paaya') }
  }

  async function deleteSong(song: Song) {
    if (!token || !window.confirm(`“${song.title}” ko delete karna hai?`)) return
    try {
      await api.deleteSong(song.id, token)
      setSongs((items) => items.filter((item) => item.id !== song.id))
      setLikedSongs((items) => items.filter((item) => item.id !== song.id))
      setLikedIds((items) => items.filter((id) => id !== song.id))
      setPlaylists((items) => items.map((playlist) => ({ ...playlist, songs: playlist.songs.filter((item) => item.id !== song.id) })))
      setQueue((items) => items.filter((item) => item.id !== song.id))
      setTopSongs((items) => items.filter((item) => item.id !== song.id))
      if (currentSong?.id === song.id) {
        setIsPlaying(false)
        setCurrentSong(null)
        setCurrentTime(0)
        setDuration(0)
      }
      setNotice('Song aur uski audio file delete ho gayi.')
      setError('')
    } catch (err) { setError(err instanceof Error ? err.message : 'Song delete nahi hua') }
  }

  async function createArtist(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token) return
    const form = event.currentTarget
    const name = String(new FormData(form).get('name') ?? '').trim()
    try {
      const artist = await api.createArtist(name, token)
      setArtists((items) => [artist, ...items])
      form.reset()
      setNotice('Artist add ho gaya!')
      setError('')
    } catch (err) { setError(err instanceof Error ? err.message : 'Artist add nahi hua') }
  }

  async function createAlbum(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token) return
    const form = event.currentTarget
    try {
      const album = await api.createAlbum(new FormData(form), token)
      setAlbums((items) => [album, ...items])
      form.reset()
      setNotice('Album add ho gaya!')
      setError('')
    } catch (err) { setError(err instanceof Error ? err.message : 'Album add nahi hua') }
  }

  function playSong(song: Song) {
    if (currentSong?.id === song.id) setIsPlaying((playing) => !playing)
    else {
      setQueue(song.source ? [song] : songs.some((item) => item.id === song.id) ? songs : [song])
      setCurrentSong(song)
      setCurrentTime(0)
      setDuration(song.durationSeconds)
      setIsPlaying(true)
    }
  }

  function previousTrack() {
    if (!currentSong) return
    if (currentTime > 3 && audioRef.current) {
      audioRef.current.currentTime = 0
      setCurrentTime(0)
      return
    }
    const index = queue.findIndex((song) => song.id === currentSong.id)
    if (index < 0 || queue.length < 2) {
      if (audioRef.current) audioRef.current.currentTime = 0
      setCurrentTime(0)
      return
    }
    const previousIndex = (index - 1 + queue.length) % queue.length
    selectQueuedTrack(previousIndex)
  }

  function nextTrack(autoAdvance = false) {
    if (!currentSong) return
    const index = queue.findIndex((song) => song.id === currentSong.id)
    const nextIndex = index + 1
    if (index < 0 || nextIndex >= queue.length) {
      if (autoAdvance) {
        if (audioRef.current) audioRef.current.currentTime = 0
        setIsPlaying(false)
        setCurrentTime(0)
        return
      }
      if (queue.length < 2) {
        setIsPlaying(false)
        setCurrentTime(0)
        return
      }
      selectQueuedTrack(0)
      return
    }
    selectQueuedTrack(nextIndex)
  }

  function selectQueuedTrack(index: number) {
    const song = queue[index]
    if (!song) return
    setCurrentSong(song)
    setCurrentTime(0)
    setDuration(song.durationSeconds)
    setIsPlaying(true)
  }

  function logout() {
    localStorage.removeItem(tokenKey)
    setToken('')
    setShowLikedOnly(false)
    setNotice('Logout ho gaya.')
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#home"><span className="brand-mark">♪</span> soundwave</a>
        <span className="topbar-tag">MUSIC FOR EVERY MOOD</span>
        {token ? <button className="button button-quiet" onClick={logout}>Log out</button> : <a className="button button-quiet" href="#account">Log in</a>}
      </header>

      <div className="main-layout">
        <aside className="left-sidebar" aria-label="Soundwave navigation">
          <nav className="sidebar-primary">
            <a href="#home"><span aria-hidden="true">⌂</span> Home</a>
            <button onClick={focusSearch}><span aria-hidden="true">⌕</span> Search</button>
          </nav>
          <section className="sidebar-library">
            <div className="sidebar-library-heading"><a href="#catalog">▤ &nbsp; Your Library</a><button aria-label="Create playlist" onClick={() => document.getElementById('account')?.scrollIntoView({ behavior: 'smooth' })}>＋</button></div>
            {playlists.length === 0 ? <div className="sidebar-prompt"><strong>Create your first playlist</strong><p>Save the songs you love in one place.</p><a href="#account">Create playlist</a></div> : <div className="sidebar-playlists">{playlists.slice(0, 6).map((playlist) => <a href="#account" key={playlist.id}><span className="sidebar-playlist-icon">♫</span><span><strong>{playlist.name}</strong><small>Playlist · {playlist.songs.length} tracks</small></span></a>)}</div>}
            <a className="sidebar-liked" href="#catalog" onClick={(event) => { if (!token) { event.preventDefault(); setError('Liked songs dekhne ke liye login karo.'); return } setShowLikedOnly(true) }}>♡ &nbsp; Liked songs</a>
          </section>
          <div className="sidebar-languages"><button>◎ &nbsp; English</button></div>
        </aside>
        <main id="home">
        <section className="hero">
          <div>
            <p className="eyebrow">YOUR SOUNDTRACK STARTS HERE</p>
            <h1>Find your<br /><span>next favourite.</span></h1>
            <p className="hero-copy">Explore the catalog, save the tracks you love, and build a playlist for every version of you.</p>
          </div>
          <div className="hero-art" aria-hidden="true"><span>♫</span><i>♪</i><b>♫</b></div>
        </section>

        <section className="charts-section" aria-labelledby="charts-title">
          <div className="charts-heading">
            <div><p className="eyebrow">SOUNDWAVE CHARTS</p><h2 id="charts-title">Find what’s moving.</h2></div>
            <p>Global and India chart collections, inspired by the music people love.</p>
          </div>
          <div className="chart-grid">
            <button className="chart-card chart-purple" onClick={browseCharts}>
              <span className="chart-art"><strong>Top<br />Songs</strong><b>Global</b><small>↗ &nbsp; WEEKLY CHARTS</small></span>
              <span className="chart-description">Discover tracks from the Soundwave global collection.</span>
            </button>
            <button className="chart-card chart-red" onClick={browseCharts}>
              <span className="chart-art"><strong>Top<br />Songs</strong><b>India</b><small>↗ &nbsp; WEEKLY CHARTS</small></span>
              <span className="chart-description">Explore music featured in Soundwave India.</span>
            </button>
            <button className="chart-card chart-blue" onClick={browseCharts}>
              <span className="chart-art chart-top-fifty"><strong>Top 50</strong><b>GLOBAL</b></span>
              <span className="chart-description">A collection of tracks from across Soundwave.</span>
            </button>
            <button className="chart-card chart-green" onClick={browseCharts}>
              <span className="chart-art chart-top-fifty"><strong>Top 50</strong><b>INDIA</b></span>
              <span className="chart-description">Explore the Soundwave India collection.</span>
            </button>
          </div>
          <div className="chart-ranking" aria-live="polite">
            <h3>Most played on Soundwave</h3>
            {topSongs.length === 0 ? <p className="charts-empty-note">Play a track to start the Soundwave chart.</p> : topSongs.map((song, index) => (
              <button className="chart-ranking-row" key={song.id} onClick={() => playSong(song)}>
                <span>{String(index + 1).padStart(2, '0')}</span><strong>{song.title}</strong>
                <small>{artists.find((artist) => artist.id === song.artistId)?.name ?? 'Independent artist'}</small>
                <span>{currentSong?.id === song.id && isPlaying ? 'Ⅱ' : '▶'}</span>
              </button>
            ))}
          </div>
          {songs.length === 0 && <p className="charts-empty-note">Charts will come alive as songs are added to the Soundwave library.</p>}
        </section>

        {(error || notice) && <div className={error ? 'message error' : 'message success'} role="status">{error || notice}</div>}

        <section className="content-grid">
          <div className="catalog-section" id="catalog">
            <div className="section-heading">
              <div><p className="eyebrow">THE LIBRARY</p><h2>{showLikedOnly ? 'Liked songs' : 'Explore tracks'}</h2></div>
              <form className="search-form" onSubmit={(event) => { event.preventDefault(); if (!showLikedOnly) void loadCatalog(query.trim()) }}>
                <span aria-hidden="true">⌕</span>
                <input aria-label="Search songs or artists" placeholder="Search songs or artists" value={query} onChange={(event) => setQuery(event.target.value)} />
                {query && <button type="button" aria-label="Clear search" onClick={() => { setQuery(''); if (!showLikedOnly) void loadCatalog() }}>×</button>}
                <button type="submit" aria-label="Submit search">Search</button>
              </form>
            </div>

            <div className="catalog-tabs" role="tablist" aria-label="Song library">
              <button className={!showLikedOnly ? 'catalog-tab active' : 'catalog-tab'} role="tab" aria-selected={!showLikedOnly} onClick={() => setShowLikedOnly(false)}>All tracks</button>
              <button className={showLikedOnly ? 'catalog-tab active' : 'catalog-tab'} role="tab" aria-selected={showLikedOnly} onClick={() => {
                if (!token) { setError('Liked songs dekhne ke liye login karo.'); return }
                setShowLikedOnly(true)
              }}>Liked songs <span>{likedSongs.length}</span></button>
            </div>

            {visibleSongs.length === 0 ? <div className="empty-state"><span>{isSearching ? '⌕' : showLikedOnly ? '♡' : '♫'}</span><h3>{isSearching ? 'Searching…' : query.trim() ? 'No matching tracks' : showLikedOnly ? 'No liked songs yet' : 'No tracks yet'}</h3><p>{isSearching ? 'Songs aur artists dhoondh rahe hain…' : query.trim() ? 'Dusra song ya artist name try karo.' : showLikedOnly ? 'Catalog mein kisi track ko ♥ dabakar save karo.' : sampleCatalogEnabled ? 'Catalog mein songs add karne ke baad woh yahan dikhne lagenge.' : 'Soundwave catalog mein songs add karo, ya free music catalog connect karke yahan stream karo.'}</p></div> :
              <div className="track-list">
                {visibleSongs.map((song, index) => (
                  <article className="track-row" key={song.id}>
                    <span className="track-number">{String(index + 1).padStart(2, '0')}</span>
                    <button className="track-play" aria-label={`Play ${song.title}`} onClick={() => playSong(song)}>{currentSong?.id === song.id && isPlaying ? 'Ⅱ' : '▶'}</button>
                    <div className="cover-art">{song.artworkUrl ? <img src={song.artworkUrl} alt="" /> : (() => { const coverKey = albums.find((album) => album.id === song.albumId)?.coverKey; return coverKey ? <img src={api.coverUrl(coverKey)} alt="" /> : <span>♫</span> })()}</div>
                    <div className="track-info"><strong>{song.title}</strong><span>{song.source === 'jamendo' ? <>{song.artistName} · <a className="music-credit" href={song.trackUrl} target="_blank" rel="noreferrer">Free music via Jamendo</a>{song.licenseUrl && <> · <a className="music-credit" href={song.licenseUrl} target="_blank" rel="noreferrer">License</a></>}</> : [artists.find((artist) => artist.id === song.artistId)?.name, albums.find((album) => album.id === song.albumId)?.title].filter(Boolean).join(' · ') || 'Independent artist'}</span></div>
                    <span className="track-duration">{formatDuration(song.durationSeconds)}</span>
                    {!song.source && <button className={likedIds.includes(song.id) ? 'like-button liked' : 'like-button'} aria-label={likedIds.includes(song.id) ? 'Unlike song' : 'Like song'} onClick={() => void toggleLike(song)}>{likedIds.includes(song.id) ? '♥' : '♡'}</button>}
                    {isAdmin && !song.source && <button className="delete-song" aria-label={`Delete ${song.title}`} title="Delete song" onClick={() => void deleteSong(song)}>×</button>}
                    {!song.source && playlists.length > 0 && <select className="add-select" aria-label={`Add ${song.title} to playlist`} defaultValue="" onChange={(event) => { if (event.target.value) void addToPlaylist(event.target.value, song.id); event.target.value = '' }}><option value="" disabled>＋ Playlist</option>{playlists.map((playlist) => <option value={playlist.id} key={playlist.id}>{playlist.name}</option>)}</select>}
                  </article>
                ))}
              </div>}
          </div>

          <aside className="side-column">
            <section className="panel account-panel" id="account">
              {token ? <>
                <p className="eyebrow">YOUR SPACE</p><h2>Make it yours.</h2>
                <form className="inline-form" onSubmit={createPlaylist}>
                  <label htmlFor="playlist-name">New playlist</label>
                  <input id="playlist-name" maxLength={100} placeholder="e.g. Late night drive" value={playlistName} onChange={(event) => setPlaylistName(event.target.value)} required />
                  <button className="button button-primary" type="submit">Create playlist</button>
                </form>
                <div className="playlist-list"><h3>Your playlists <span>{playlists.length}</span></h3>
                  {playlists.length === 0 ? <p className="muted">Abhi koi playlist nahi. Pehli playlist banao.</p> : playlists.map((playlist) => <div className="playlist-item" key={playlist.id}>
                    <button className="playlist-open" onClick={() => setSelectedPlaylistId((id) => id === playlist.id ? '' : playlist.id)} aria-expanded={selectedPlaylistId === playlist.id}>
                      <span className="playlist-icon">♫</span><span className="playlist-label"><strong>{playlist.name}</strong><small>{playlist.songs.length} tracks</small></span>
                    </button>
                    <button className="mini-play" disabled={playlist.songs.length === 0} onClick={() => playPlaylist(playlist)} aria-label={`Play ${playlist.name}`}>▶</button>
                  </div>)}
                  {playlists.find((playlist) => playlist.id === selectedPlaylistId) && (() => {
                    const playlist = playlists.find((item) => item.id === selectedPlaylistId)!
                    return <div className="playlist-detail">
                      <div className="playlist-detail-heading"><strong>{playlist.name}</strong><button className="play-all" disabled={playlist.songs.length === 0} onClick={() => playPlaylist(playlist)}>Play playlist</button></div>
                      {playlist.songs.length === 0 ? <p className="muted">Playlist khaali hai—catalog se tracks add karo.</p> : playlist.songs.map((song, index) => <div className="playlist-track" key={song.id}>
                        <button className="playlist-track-play" onClick={() => playPlaylistSong(playlist, song)} aria-label={`Play ${song.title}`}>{currentSong?.id === song.id && isPlaying ? 'Ⅱ' : `${index + 1}.`}</button>
                        <span>{song.title}</span>
                        <button className="remove-track" onClick={() => void removeFromPlaylist(playlist, song)} aria-label={`Remove ${song.title}`}>×</button>
                      </div>)}
                    </div>
                  })()}
                </div>
              </> : <>
                <p className="eyebrow">JOIN THE LISTENING ROOM</p><h2>{isRegister ? 'Create your account.' : 'Good music awaits.'}</h2>
                <p className="panel-copy">Sign in to like tracks and create your own playlists.</p>
                <form className="inline-form" onSubmit={submitAuth}>
                  <label htmlFor="email">Email</label>
                  <input id="email" type="email" autoComplete="email" placeholder="you@example.com" value={email} onChange={(event) => setEmail(event.target.value)} required />
                  <label htmlFor="password">Password</label>
                  <input id="password" type="password" autoComplete={isRegister ? 'new-password' : 'current-password'} minLength={isRegister ? 8 : undefined} placeholder={isRegister ? 'At least 8 characters' : 'Your password'} value={password} onChange={(event) => setPassword(event.target.value)} required />
                  <button className="button button-primary" type="submit">{isRegister ? 'Create account' : 'Log in'}</button>
                </form>
                <button className="switch-auth" onClick={() => { setIsRegister((value) => !value); setError('') }}>{isRegister ? 'Already have an account? Log in' : 'New here? Create an account'}</button>
              </>}
            </section>
            {isAdmin && <>
              <section className="panel upload-panel">
                <p className="eyebrow">ADMIN TOOLS</p><h2>Add an artist</h2>
                <form className="inline-form" onSubmit={createArtist}>
                  <label htmlFor="artist-name">Artist name</label><input id="artist-name" name="name" maxLength={200} required placeholder="Artist name" />
                  <button className="button button-primary" type="submit">Add artist</button>
                </form>
              </section>
              <section className="panel upload-panel">
                <p className="eyebrow">ALBUM DETAILS</p><h2>Add an album</h2>
                <form className="inline-form" onSubmit={createAlbum}>
                  <label htmlFor="album-title">Album title</label><input id="album-title" name="title" maxLength={200} required placeholder="Album name" />
                  <label htmlFor="album-artist">Artist (optional)</label><select id="album-artist" name="artistId" defaultValue=""><option value="">No artist selected</option>{artists.map((artist) => <option key={artist.id} value={artist.id}>{artist.name}</option>)}</select>
                  <label htmlFor="album-cover">Cover image (JPG, PNG, or WEBP, up to 5 MB)</label><input id="album-cover" name="coverFile" type="file" accept="image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp" />
                  <button className="button button-primary" type="submit">Add album</button>
                </form>
              </section>
              <section className="panel upload-panel">
                <p className="eyebrow">AUDIO LIBRARY</p><h2>Upload a track</h2>
                <form className="inline-form" onSubmit={uploadTrack}>
                  <label htmlFor="track-title">Track title</label><input id="track-title" name="title" maxLength={200} required placeholder="Track name" />
                  <label htmlFor="track-artist">Artist (optional)</label><select id="track-artist" name="artistId" defaultValue=""><option value="">No artist selected</option>{artists.map((artist) => <option key={artist.id} value={artist.id}>{artist.name}</option>)}</select>
                  <label htmlFor="track-album">Album (optional)</label><select id="track-album" name="albumId" defaultValue=""><option value="">No album selected</option>{albums.map((album) => <option key={album.id} value={album.id}>{album.title}</option>)}</select>
                  <label htmlFor="track-duration">Duration in seconds</label><input id="track-duration" name="durationSeconds" type="number" min="0" defaultValue="0" />
                  <label htmlFor="track-file">Audio file (up to 25 MB)</label><input id="track-file" name="audioFile" type="file" accept="audio/mpeg,audio/wav,audio/ogg,audio/mp4,audio/aac,.mp3,.wav,.ogg,.m4a,.aac" required />
                  <button className="button button-primary" type="submit">Upload track</button>
                </form>
              </section>
            </>}
            <section className="player-panel">
              <audio ref={audioRef} src={currentSong ? currentSong.streamUrl ?? api.streamUrl(currentSong.id) : undefined}
                onPlay={() => { if (currentSong && !currentSong.source) void api.recordPlay(currentSong.id).then(loadTopSongs).catch(() => {}) }}
                onTimeUpdate={(event) => setCurrentTime(event.currentTarget.currentTime)}
                onLoadedMetadata={(event) => setDuration(event.currentTarget.duration || currentSong?.durationSeconds || 0)}
                onEnded={() => nextTrack(true)} />
              <div className="player-disc">{currentSong?.artworkUrl ? <img src={currentSong.artworkUrl} alt="" /> : (() => { const coverKey = albums.find((album) => album.id === currentSong?.albumId)?.coverKey; return coverKey ? <img src={api.coverUrl(coverKey)} alt="" /> : '♫' })()}</div>
              <div className="player-details"><p className="eyebrow">NOW PLAYING</p><strong>{currentSong?.title ?? 'Choose a track'}</strong>
                {currentSong?.source === 'jamendo' && <a className="player-credit" href={currentSong.trackUrl} target="_blank" rel="noreferrer">{currentSong.artistName} · Free music via Jamendo</a>}
                <div className="player-progress"><span>{formatDuration(currentTime)}</span><input aria-label="Seek track" type="range" min="0" max={duration || 1} step="0.1" value={Math.min(currentTime, duration || 1)} onChange={(event) => { const time = Number(event.target.value); setCurrentTime(time); if (audioRef.current) audioRef.current.currentTime = time }} /><span>{formatDuration(duration)}</span></div>
              </div>
              <div className="player-controls">
                <button className="skip-button" aria-label="Previous track" disabled={!currentSong} onClick={previousTrack}>|◀</button>
                <button className="player-play" aria-label={isPlaying ? 'Pause' : 'Play'} disabled={!currentSong} onClick={() => { if (currentSong) setIsPlaying((playing) => !playing) }}>{isPlaying ? 'Ⅱ' : '▶'}</button>
                <button className="skip-button" aria-label="Next track" disabled={!currentSong} onClick={() => nextTrack()}>▶|</button>
              </div>
            </section>
          </aside>
        </section>
        </main>
      </div>

      <footer className="site-footer">
        <div className="footer-columns">
          <section className="footer-column"><h3>Company</h3><a href="#about">About</a><span>Jobs</span><span>For the Record</span></section>
          <section className="footer-column"><h3>Communities</h3><span>For Artists</span><span>For Creators</span><span>For Authors</span><span>Developers</span><span>Advertising</span><span>Investors</span><span>Vendors</span></section>
          <section className="footer-column"><h3>Useful links</h3><span>Support</span><span>Free Mobile App</span><a href="#catalog">Popular in Soundwave</a><a href="#charts-title">Top Songs</a><a href="#account">Import your music</a></section>
          <section className="footer-column"><h3>Soundwave Plans</h3><span>Premium</span><span>Standard</span><span>Premium Family</span><span>Premium Student</span><span>Soundwave Free</span></section>
        </div>
        <section className="footer-about" id="about"><strong>About Soundwave</strong><p>A home for the songs you love. Discover tracks, build playlists, and make every moment sound like yours.</p></section>
        <div className="footer-bottom"><span>© 2026 soundwave</span><div><a href="#about">Privacy</a><a href="#about">Terms</a><a href="#home">Accessibility</a><span>Made for the music in you.</span></div></div>
      </footer>
    </div>
  )
}

function formatDuration(seconds: number) {
  const safeSeconds = Number.isFinite(seconds) ? Math.max(0, Math.floor(seconds)) : 0
  return `${Math.floor(safeSeconds / 60)}:${String(safeSeconds % 60).padStart(2, '0')}`
}

function tokenRole(token: string) {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    return JSON.parse(atob(payload)).role as string
  } catch { return '' }
}
