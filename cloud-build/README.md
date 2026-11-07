# STORYFORGE Cloud Build Backend

This is the architecture and security documentation for the Vercel Functions service in \`cloud-build-service/\`.

## Current implementation status

The API foundation exists in \`cloud-build-service/api/\` and its TypeScript typecheck has passed in GitHub Actions. It is **not deployed or connected to the Android app yet**. It requires an OIDC provider, PostgreSQL, a GitHub App installation, and Vercel environment variables.

The current API accepts an HTTPS \`.storygame\` download URL on an explicit host allowlist. Direct upload from the Android app to private object storage is a later improvement; it is not implemented yet.

## Intended flow

1. The user signs in through an OIDC provider configured for the service.
2. The Android client sends a short-lived OIDC access token to the API over HTTPS.
3. The API validates the JWT against the configured issuer, audience, and JWKS.
4. The user submits a direct HTTPS \`.storygame\` URL hosted on an allowlisted domain.
5. The API creates a build record in PostgreSQL and dispatches the fixed standalone APK workflow using a short-lived GitHub App installation token.
6. The app polls the build status endpoint. The API correlates the generated request ID with the GitHub Actions run name.
7. After a successful build, the API redirects the authenticated owner to the GitHub artifact download.

## Deployment shape

- Vercel Node.js Functions in \`cloud-build-service/\`.
- PostgreSQL for build records and user ownership.
- GitHub App installation token for workflow dispatch and artifact access.
- GitHub Actions for Android Gradle builds.

Vercel orchestrates builds; it does not compile Android itself.

## Required Vercel setup

Create a Vercel project linked to this repository with Root Directory set to \`cloud-build-service\`. Configure the server-side variables described in \`cloud-build-service/README.md\`, run \`cloud-build-service/db/schema.sql\` against PostgreSQL, and deploy only after the security checklist passes.

## Next implementation gates

- Configure the OIDC identity provider and Android sign-in/token flow.
- Provision PostgreSQL and apply the schema.
- Create and install a least-privilege GitHub App on \`omegathor3-afk/STORYFORGE\`.
- Set \`STORYFORGE_BUILD_REF=feature/cloud-build-center\` so the dispatched workflow includes request correlation.
- Set the HTTPS host allowlist for story archives and test redirect handling.
- Add the API client and build progress UI to Android.
- Run an end-to-end build and artifact download before calling the service live.

See [API_CONTRACT.md](./API_CONTRACT.md) and [SECURITY.md](./SECURITY.md).
