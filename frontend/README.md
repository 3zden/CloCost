# CloCost frontend

React 19 + TypeScript + Vite. Talks to the Spring backend at `/api/v1`.

## Develop

```bash
npm install
npm run dev        # http://localhost:5173, proxies /api → http://localhost:8080
```

## Build

```bash
npm run build      # typecheck + production bundle in dist/
```

## Deploy

```bash
docker build -t clocost-frontend .
docker run -p 80:80 -e BACKEND_URL=http://backend:8080 clocost-frontend
```

nginx serves the SPA and proxies `/api/` to `BACKEND_URL`, so the browser stays same-origin (no CORS).
Hashed assets are cached for a year; `index.html` is always revalidated. Health check: `GET /healthz`.

To call an API on another origin instead, build with `VITE_API_URL=https://api.example.com` and enable CORS on the backend.
