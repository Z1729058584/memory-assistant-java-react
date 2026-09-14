# Memory Assistant (React + Java)

A memory assistant with a React frontend and a Spring Boot backend powered by the DeepSeek API.

## Stack

- Frontend: React 18 + Vite 5
- Backend: Java 21 + Spring Boot 3.5 + JPA
- AI provider: DeepSeek API
- Local persistence: SQLite

## Local development

Set the DeepSeek key in the backend process environment. Never commit the real key.

```powershell
$env:DEEPSEEK_API_KEY="your-key"
cd backend
mvn spring-boot:run
```

Start the frontend in a second terminal:

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

Open `http://localhost:5173`. During local development, Vite proxies `/api` requests to `http://localhost:8080`.

## Deployment

### Frontend on Vercel

Import this repository and configure:

- Root Directory: `frontend`
- Framework Preset: Vite
- Build Command: `npm run build`
- Output Directory: `dist`
- Environment variable: `VITE_API_BASE_URL=https://your-backend-domain`

### Backend

The backend is a long-running Spring Boot service and should be deployed to a Java-compatible host such as Railway or Render rather than as a Vercel frontend project.

Configure these environment variables on the backend host:

- `DEEPSEEK_API_KEY`: your DeepSeek API key
- `APP_ALLOWED_ORIGINS`: the deployed Vercel origin, for example `https://your-app.vercel.app`

The included SQLite database is intended for local development and is excluded from Git. For production, use persistent storage or migrate the datasource to a managed database before relying on stored memories.

## API

- `GET /api/health`
- `GET /api/app-config`
- `POST /api/chat`
- `GET /api/users/{userId}/memories`
- `POST /api/users/{userId}/memories`
- `PATCH /api/users/{userId}/memories/{memoryId}`
- `DELETE /api/users/{userId}/memories/{memoryId}`
- `GET /api/users/{userId}/memories/review`
- `GET /api/users/{userId}/conversations`
