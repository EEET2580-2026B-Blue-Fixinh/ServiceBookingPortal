# Vercel deployment

The React/Vite frontend is deployed to Vercel. The Spring Boot backend must run
on a Java/container host that supports long-lived WebSocket connections and
background jobs.

## Project settings

The organization repository is private and the project uses Vercel Hobby, so it
is intentionally not connected through Vercel's Git integration. Deploy from
the checked-out local branch with the Vercel CLI instead.

The existing Vercel project uses these settings:

| Setting | Value |
|---|---|
| Project | `fixinh-service-booking-portal` |
| Root Directory | unset (the CLI uploads `Frontend` as the root) |
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

## Manual Preview and Production workflow

Run every Vercel command from `Frontend`; running it from the repository root
would upload the backend and documentation too.

To deploy the currently checked-out feature branch as Preview:

```bash
git status --short
cd Frontend
npx --yes vercel@latest deploy
```

Only deploy Production after the reviewed feature branch has been merged. Start
from an up-to-date, clean `main` worktree:

```bash
git switch main
git pull --ff-only
git status --short
cd Frontend
npx --yes vercel@latest deploy --prod
```

Before using `--prod`, verify that the Production environment variables point
to the production backend and that `VITE_SHOW_DEMO_ACCOUNT=false`. Then test
direct route reloads, authentication, REST calls, CORS, and WebSocket
notifications. Roll back the deployment in Vercel if a regression is found.

The first CLI deployment of a newly created Vercel project is assigned to
Production automatically. Subsequent CLI deployments are Preview unless
`--prod` is supplied.
