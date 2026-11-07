# Implemented API Contract

Base path: \`/api\`. All requests except health require an OIDC access token:

\`Authorization: Bearer <OIDC access token>\`

The service verifies JWT signature via JWKS and checks the configured issuer and audience. Each build status and artifact request is scoped to the authenticated OIDC subject.

## `GET /health`

Returns \`200\` when required environment variables are present, otherwise \`503\`. It does not expose secret values.

## `POST /build-jobs`

Creates a standalone APK build request.

Required header:
- \`Idempotency-Key\`: 12–100 characters, letters, digits, underscore, or hyphen.

Request:
\`\`\`json
{
  "storygameUrl": "https://files.example.com/story.storygame",
  "gameTitle": "My Story",
  "applicationId": "com.example.mystory",
  "versionName": "1.0.0",
  "versionCode": 1,
  "buildType": "debug",
  "iconUrl": "https://files.example.com/icon.png"
}
\`\`\`

Rules:
- \`storygameUrl\` is required, HTTPS-only, credential-free, and its host must match \`STORYGAME_ALLOWED_HOSTS\`.
- \`gameTitle\` is required and limited to 40 characters.
- \`applicationId\` is optional but must match Android package ID rules.
- \`versionName\` must use \`MAJOR.MINOR.PATCH\`.
- \`versionCode\` must be an integer from 1 through 2,100,000,000.
- \`buildType\` is \`debug\` or \`release\`; default is \`debug\`.
- \`iconUrl\` is optional and subject to the same HTTPS host allowlist.
- The repository, workflow path, and build ref are server-configured, not client-controlled.
- The service allows at most two active builds per OIDC subject and reuses an existing request for a repeated idempotency key.

Response: \`202\` with \`jobId\`, \`status\`, and \`statusUrl\`; repeated idempotent requests return the existing job.

## `GET /build-jobs/{id}`

Returns status only when the job belongs to the authenticated user. The service polls GitHub Actions and maps run state to \`queued\`, \`in_progress\`, \`completed\`, or \`failed\`. On completion it checks whether the expected artifact exists.

## `GET /build-jobs/{id}/artifact`

Redirects the authenticated owner to the GitHub artifact archive only when the job is completed and the matching artifact has not expired.

## Error behavior

The API returns safe JSON error messages. It does not return private keys, installation tokens, database credentials, or stack traces.

## Not implemented yet

- Direct Android-to-private-Blob uploads.
- In-app OIDC sign-in and secure token storage.
- Webhook/callback-based status updates (status currently polls GitHub Actions).
- Automated artifact retention beyond GitHub Actions artifact expiry.
