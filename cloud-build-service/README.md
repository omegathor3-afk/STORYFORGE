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
- R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY, R2_BUCKET_NAME: private Cloudflare R2 bucket used for story uploads. Create an R2 API token scoped to this bucket only.
- STORYFORGE_BUILD_REF: branch containing standalone-apk.yml.
- Optional GITHUB_REPOSITORY_OWNER and GITHUB_REPOSITORY_NAME.

For Cloudflare R2, create a private bucket and an API token scoped to that bucket. Set `STORYGAME_ALLOWED_HOSTS` to `<ACCOUNT_ID>.r2.cloudflarestorage.com` so the worker accepts only the presigned story archive host. Configure an R2 lifecycle rule to delete objects under `storygames/` after a short retention period.

Run db/schema.sql once against PostgreSQL. Then install dependencies and run npm run typecheck.

## API
- POST /api/uploads: authenticated; returns short-lived signed PUT and GET URLs for a `.storygame` file in private R2 storage.
- POST /api/build-jobs: authenticated; requires Idempotency-Key and JSON storygameUrl, gameTitle, plus optional app identity/version/icon/build type.
- GET /api/build-jobs/:id: authenticated job status and artifact readiness.
- GET /api/build-jobs/:id/artifact: authenticated short-lived redirect to the artifact archive.

The API scopes reads to the authenticated subject, allows at most two active jobs per account, restricts download URLs to configured HTTPS hosts, and mints GitHub App installation tokens server-side.

## Android app connection

The Studio Build Center now includes native OIDC Authorization Code + PKCE sign-in and calls the three API routes directly. It keeps the access token in memory only; it is not written to preferences or logs.

In the Android app's **مركز بناء الألعاب** screen, configure:
- Cloud Builder base URL, e.g. `https://your-service.vercel.app`.
- OIDC Issuer: the issuer URL used by your identity provider.
- OIDC Client ID: register a **native/public client** (no client secret).
- API Audience: the audience configured in `OIDC_AUDIENCE`.

Register this exact native redirect URI with the identity provider:
`com.storyforge.app:/oauth2redirect`

Enable Authorization Code + PKCE (S256) and the `openid profile email` scopes. If the provider supports a custom `audience` authorization parameter, the app sends the configured API Audience value.

The app packages the selected story, uploads it directly to a private R2 bucket with a short-lived presigned PUT URL, starts the GitHub build, and refreshes job status. The artifact endpoint returns a short-lived HTTPS download redirect; the app obtains that URL with the bearer token, then hands only the signed URL (never the bearer token) to Android Download Manager.

The Android Studio now exports the `.storygame` archive into its private cache, uploads it directly to the configured private R2 bucket, and starts the build. No public story URL or external file-hosting step is required. The R2 bucket should remain private; the signed download URL expires after one hour and the upload URL after ten minutes. Configure lifecycle deletion for the `storygames/` prefix to remove old uploads.


## Community API foundation

The authenticated community API is implemented as a first backend milestone. It is **not live until this service is deployed and the community SQL schema is applied**.

- `GET /api/community/feed?genre=all&limit=20&cursor=...`: newest-first, cursor-paginated public story feed.
- `POST /api/community/feed`: publish a title, short post body, genre, and optional bounded story JSON.
- `GET|POST /api/community/posts/:id/comments`: read and add comments.
- `POST /api/community/posts/:id/reactions`: toggle like, love, or inspiring reaction.
- `GET|POST /api/community/profile`: read or update the signed-in member profile.
- `POST /api/community/follow`: follow or unfollow a member.
- `POST /api/community/reports`: submit a safety or copyright report.

Apply the expanded `db/schema.sql` before deploying. All community routes require the same verified OIDC bearer-token configuration as the build service. The app must never embed database, GitHub, or storage credentials. Before opening public registration, configure a moderation workflow, privacy policy, abuse-response owner, backups, and retention rules. The current API stores story JSON in PostgreSQL; production deployments should add object storage for larger media and virus/content scanning before accepting uploads.
