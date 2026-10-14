# Soundwave

A full-stack music-player learning project using React + TypeScript, Spring Boot, Spring Security, PostgreSQL, and Flyway.

## Run locally

Requirements: Java 21, Maven, Node.js, npm, and Docker Desktop.

From the project root, open three PowerShell terminals:

```powershell
docker compose up -d postgres
```

```powershell
cd backend
mvn spring-boot:run
```

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The health endpoint is `http://localhost:8181/api/health`.

### Run the whole app in Docker

Use this option instead of the three local terminal commands above. Stop the locally running Spring Boot and Vite processes first, then run from the project root:

```powershell
docker compose up -d --build
```

Open the containerized frontend at `http://localhost:4173`; the API remains at `http://localhost:8181`. PostgreSQL data and audio uploads use named Docker volumes. To stop the containers without deleting their data, run `docker compose down`.

## What is implemented

- Flyway creates `users`, `artists`, `albums`, `songs`, `playlists`, `playlist_songs`, and `likes`.
- Signup and login issue short-lived JWT bearer tokens. Passwords are BCrypt-hashed.
- Public song catalog and title search.
- User-owned playlists, playlist tracks, and liked tracks.
- Admin-only endpoints for creating artists, albums, and song metadata.
- Admin UI to add artists and albums and link them to uploaded tracks.
- Admin-only audio uploads (MP3, WAV, OGG, M4A, AAC; max 25 MB) and a song streaming endpoint.
- React page for browsing/searching tracks, registering/logging in, uploading tracks as admin, browsing liked tracks, managing playlists, playing audio, and advancing through playlist queues.

## Make your account an admin (local development)

1. Register from the Soundwave page.
2. In PowerShell, replace the email below with your registered email:

```powershell
docker exec -it music-player-postgres psql -U music_user -d music_player -c "UPDATE users SET role = 'ADMIN' WHERE email = 'you@example.com';"
```

3. Log out and log in again so the new token carries the admin role.
4. Use the admin API to create artists/albums/song metadata. Admin routes require `Authorization: Bearer <accessToken>`.

Example PowerShell request to add song metadata:

```powershell
$headers = @{ Authorization = "Bearer YOUR_ACCESS_TOKEN" }
$body = @{ title = "My Demo Track"; audioKey = "tracks/demo.mp3"; durationSeconds = 180 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8181/api/admin/songs -Headers $headers -ContentType "application/json" -Body $body
```

## API routes

| Method | Route | Access |
| --- | --- | --- |
| POST | `/api/auth/register` | Public |
| POST | `/api/auth/login` | Public |
| GET | `/api/songs` | Public |
| GET | `/api/songs/{id}` | Public |
| GET | `/api/songs/{id}/stream` | Public |
| GET | `/api/search?q=...` | Public |
| GET / POST | `/api/playlists` | Login required |
| POST / DELETE | `/api/playlists/{id}/songs` | Login required |
| GET | `/api/likes` | Login required |
| PUT / DELETE | `/api/likes/{songId}` | Login required |
| POST | `/api/admin/artists`, `/api/admin/albums`, `/api/admin/songs`, `/api/admin/songs/upload` | Admin |

## Notes

- Compose database credentials and the JWT fallback secret are for local development only. Set private environment variables before deployment.
- Local PostgreSQL is exposed on host port `5433` to avoid colliding with an existing PostgreSQL installation on `5432`.
- The Compose volume is named `soundwave_postgres_data`. If a prior volume existed, it is left untouched.
- Local audio uploads are stored in `backend/uploads/`; this folder is ignored by Git. Use persistent or object storage before production deployment because hosted app filesystems may be temporary.
- The Docker Compose audio volume is convenient for local use. For hosted production, configure persistent or object storage before deploying uploads.
- A Render Blueprint is included in `render.yaml` for the API, PostgreSQL, and persistent audio disk. It uses a paid API compute plan because Render persistent disks require a paid web service; review the resource plan and current pricing before creating the Blueprint.
- Deploy the frontend as a separate Render Static Site with root directory `frontend`, build command `npm ci && npm run build`, publish directory `dist`, and build-time variable `VITE_API_BASE_URL` set to the deployed API URL (include `https://`). Then set the API service's `FRONTEND_ORIGIN` to the deployed frontend URL and redeploy the API.
- `audioKey` is metadata only at this stage. The next implementation step is private object storage, upload, and short-lived playback URLs; the UI does not stream audio yet.
- Hibernate schema changes are validated against Flyway migrations (`ddl-auto: validate`). Add future schema changes as new numbered migration files; do not edit a migration already applied to a shared database.
