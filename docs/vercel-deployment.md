# Vercel deployment

The React/Vite frontend is deployed to Vercel. The Spring Boot backend must run
on a Java/container host that supports long-lived WebSocket connections and
background jobs.

## Project settings

Import this repository as one Vercel project with these settings:

| Setting | Value |
|---|---|
| Production Branch | `main` |
| Root Directory | `Frontend` |
| Framework Preset | Vite |
| Install Command | `pnpm install --frozen-lockfile` |
| Build Command | `pnpm build` |
| Output Directory | `dist` |

`Frontend/vercel.json` sends browser deep links such as `/admin/staff` back to
`index.html`, where React Router handles them.

## Vercel environment variables

Create separate values for Preview and Production. All `VITE_*` values are
compiled into browser JavaScript and are therefore public, even if the Vercel
dashboard marks them Sensitive.

| Variable | Preview | Production |
|---|---|---|
| `VITE_API_URL` | Staging backend URL ending in `/api/v1` | Production backend URL ending in `/api/v1` |
| `VITE_WS_URL` | Staging backend URL ending in `/ws` | Production backend URL ending in `/ws` |
| `VITE_SHOW_DEMO_ACCOUNT` | `true` only when test accounts are intended | `false` |
| `VITE_GOOGLE_CLIENT_ID` | Optional public OAuth client ID | Optional public OAuth client ID |
| `VITE_GOOGLE_REDIRECT_URI` | Preview login URL, if OAuth is enabled | Production login URL, if OAuth is enabled |

Do not configure demo passwords, JWT signing keys, MongoDB URIs, SMTP
credentials, Cloudinary secrets, Google client secrets, or SMP tokens in the
Vercel frontend project.

Vercel environment-variable changes apply only to new deployments. Redeploy
after adding or changing a value.

## Backend coordination

After Vercel creates a URL for the frontend, configure the backend host with:

```text
CORS_ALLOWED_ORIGINS=https://<preview-or-production-domain>
GOOGLE_REDIRECT_URI=https://<frontend-domain>/login
```

Use an exact comma-separated origin list when both Preview and Production must
work. Do not use `*`; the same allow-list protects the SockJS/WebSocket
handshake.

## Promotion workflow

1. Push `chore/vercel-deployment` and test its Vercel Preview deployment.
2. Check direct route reloads, authentication, REST calls, CORS, and WebSocket
   notifications.
3. Add the Production environment-variable values.
4. Merge the reviewed branch into `main` to create the Production deployment.
5. Smoke-test the production URL and roll back in Vercel if a regression is
   found.
