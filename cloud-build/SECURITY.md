# Cloud Build Security Checklist

## Identity and authorization
- [ ] Configure an OIDC provider with exact issuer and audience values.
- [ ] Validate access-token signature and expiry using the configured JWKS.
- [ ] Use stable OIDC subject IDs as ownership keys; never trust a client-supplied user ID.
- [ ] Enforce ownership on status and artifact endpoints.
- [ ] Add revocation/session expiry handling and per-user quotas.

## GitHub App
- [ ] Install the App only on \`omegathor3-afk/STORYFORGE\`.
- [ ] Grant Actions read/write and Contents read only.
- [ ] Keep App private key and installation ID in Vercel server-side environment variables.
- [ ] Mint short-lived installation tokens per API operation.
- [ ] Do not put GitHub tokens or App credentials in the Android APK.

## Story URL and archive safety
- [ ] Set \`STORYGAME_ALLOWED_HOSTS\` to explicit trusted domains; empty must deny all.
- [ ] Accept HTTPS only and reject URL credentials and unexpected ports.
- [ ] Verify every redirect remains HTTPS and within the allowlist (required hardening before production).
- [ ] Keep the existing archive validator limits for compressed size, entry count, expanded size, encryption, symlinks, and path traversal.
- [ ] Test DNS/IP edge cases and redirects to private or loopback addresses.
- [ ] Do not log full signed URLs.

## Abuse prevention and privacy
- [ ] Keep the two-active-jobs limit and add daily quotas and per-IP rate limits.
- [ ] Preserve idempotency and test concurrent duplicate submissions.
- [ ] Define retention for job metadata and story source files.
- [ ] Avoid logging OIDC bearer tokens, GitHub installation tokens, or database credentials.
- [ ] Return safe errors without stack traces or provider secrets.

## Release gates
- [ ] Vercel project deployed with server-only secrets.
- [ ] PostgreSQL schema applied and backups configured.
- [ ] OIDC login and token expiry tested from Android.
- [ ] GitHub App installation and workflow dispatch tested.
- [ ] Status polling correlated to the correct run for concurrent requests.
- [ ] Artifact download authorization and expiry tested.
- [ ] Full debug build and download completed end-to-end.
- [ ] Release build verified with the protected signing secrets.
