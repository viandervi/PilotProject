# CreatorRx dashboard

Next.js App Router, React, TypeScript and Tailwind CSS. The UI calls creator-scoped Micronaut APIs; it never stores Google OAuth tokens.

## Development

Use Java backend with `MICRONAUT_ENVIRONMENTS=database`, PostgreSQL, OAuth credentials and encryption key configured. Then:

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:3000. Set `CREATORRX_BACKEND_URL` to override http://localhost:8080 for the Next.js same-origin API/auth proxy.

**Authentication:** Set `GOOGLE_REDIRECT_URI=http://localhost:3000/oauth/callback` in Google OAuth credentials and the backend environment. The Next.js proxy forwards `/oauth/callback` to Micronaut, which redirects back to `CREATORRX_PUBLIC_URL` (default `http://localhost:3000`) and sets an HttpOnly session cookie. Cookies are Secure in HTTPS production; localhost HTTP is supported for development. Use `CREATORRX_PUBLIC_URL=https://your-dashboard-domain` for production. OAuth must be tested with real Google credentials and a connected channel before beta.

Dashboard uses live API responses only; no synthetic channel metrics.
