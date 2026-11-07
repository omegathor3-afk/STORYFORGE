# STORYFORGE Cloud Build Service

Vercel Node.js Functions that securely dispatch standalone Android builds to GitHub Actions.

## Status
Backend foundation only; it is not deployed or live. It requires an OIDC identity provider, PostgreSQL, a GitHub App installation, and Vercel server-side environment variables. Never put a GitHub token or signing key in the Android app.

## Vercel setup
Create a Vercel project linked to this repository with Root Directory set to cloud-build-service and Node.js 20+.

Required server-side environment variables:
- DATABASE_URL: PostgreSQL connection string.
- DATABASE_SSL: optional; defaults to TLS verification.
- OIDC_ISSUER, OIDC_AUDIENCE, OIDC_JWKS_URL: trusted identity provider JWT settings.
- GITHUB_APP_ID, GITHUB_APP_PRIVATE_KEY, GITHUB_APP_INSTALLATION_ID: GitHub App credentials. Grant Actions read/write and Contents read on STORYFORGE only.
- STORYGAME_ALLOWED_HOSTS: comma-separated host allowlist for story and icon downloads. Empty means deny all.
- STORYFORGE_BUILD_REF: branch containing standalone-apk.yml.
- Optional GITHUB_REPOSITORY_OWNER and GITHUB_REPOSITORY_NAME.

Run db/schema.sql once against PostgreSQL. Then install dependencies and run npm run typecheck.

## API
- POST /api/build-jobs: authenticated; requires Idempotency-Key and JSON storygameUrl, gameTitle, plus optional app identity/version/icon/build type.
- GET /api/build-jobs/:id: authenticated job status and artifact readiness.
- GET /api/build-jobs/:id/artifact: authenticated short-lived redirect to the artifact archive.

The API scopes reads to the authenticated subject, allows at most two active jobs per account, restricts download URLs to configured HTTPS hosts, and mints GitHub App installation tokens server-side.

## Not yet integrated
The Android app still needs an OIDC sign-in flow and API client. Configure a real identity provider and database before exposing these routes. No credentials are committed in this directory.
