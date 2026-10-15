export type Song = {
  id: string
  title: string
  artistId: string | null
  albumId: string | null
  audioKey: string
  durationSeconds: number
}

export type Artist = { id: string; name: string; imageUrl: string | null }
export type Album = { id: string; title: string; artistId: string | null; coverKey: string | null; releaseDate: string | null }

export type Playlist = {
  id: string
  name: string
  createdAt: string
  songs: Song[]
}

export type AuthResponse = {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
}

const apiBase = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8181'

async function request<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
  const headers = new Headers(options.headers)
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(`${apiBase}${path}`, { ...options, headers })
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.detail ?? body?.message ?? `Request failed (${response.status})`)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const api = {
  songs: (query = '') => request<Song[]>(query ? `/api/search?q=${encodeURIComponent(query)}` : '/api/songs'),
  topSongs: () => request<Song[]>('/api/charts/top?limit=10'),
  recordPlay: (songId: string) => request<void>(`/api/songs/${songId}/play`, { method: 'POST' }),
  artists: () => request<Artist[]>('/api/artists'),
  albums: () => request<Album[]>('/api/albums'),
  createArtist: (name: string, token: string) => request<Artist>('/api/admin/artists', {
    method: 'POST', body: JSON.stringify({ name }),
  }, token),
  createAlbum: (form: FormData, token: string) => request<Album>('/api/admin/albums/with-cover', {
    method: 'POST', body: form,
  }, token),
  register: (email: string, password: string) => request<AuthResponse>('/api/auth/register', {
    method: 'POST', body: JSON.stringify({ email, password }),
  }),
  login: (email: string, password: string) => request<AuthResponse>('/api/auth/login', {
    method: 'POST', body: JSON.stringify({ email, password }),
  }),
  playlists: (token: string) => request<Playlist[]>('/api/playlists', {}, token),
  createPlaylist: (name: string, token: string) => request<Playlist>('/api/playlists', {
    method: 'POST', body: JSON.stringify({ name }),
  }, token),
  addSong: (playlistId: string, songId: string, token: string) => request<Playlist>(`/api/playlists/${playlistId}/songs`, {
    method: 'POST', body: JSON.stringify({ songId }),
  }, token),
  removeSong: (playlistId: string, songId: string, token: string) => request<void>(`/api/playlists/${playlistId}/songs/${songId}`, {
    method: 'DELETE',
  }, token),
  likedSongs: (token: string) => request<Song[]>('/api/likes', {}, token),
  like: (songId: string, token: string) => request<void>(`/api/likes/${songId}`, { method: 'PUT' }, token),
  unlike: (songId: string, token: string) => request<void>(`/api/likes/${songId}`, { method: 'DELETE' }, token),
  uploadSong: (form: FormData, token: string) => request<Song>('/api/admin/songs/upload', {
    method: 'POST', body: form,
  }, token),
  streamUrl: (songId: string) => `${apiBase}/api/songs/${songId}/stream`,
  coverUrl: (key: string) => `${apiBase}/api/media/covers/${encodeURIComponent(key.split('/').pop() ?? '')}`,
}
