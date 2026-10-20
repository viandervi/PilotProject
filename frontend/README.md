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

**Authentication note:** Backend sets a Secure cookie. For local HTTP development, use HTTPS or configure a development-only cookie policy; do not disable Secure for production. Existing backend OAuth callback redirects to the backend POC path, so the end-to-end frontend return URL must be aligned before beta.

Dashboard uses live API responses only; no synthetic channel metrics.
