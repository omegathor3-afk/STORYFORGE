# STORYFORGE

> اصنع عوالمك… واترك القصة تتكلم  
> Create your worlds… and let the story speak.

STORYFORGE is an offline-first Android story authoring studio built with Kotlin and Jetpack Compose.

## Implemented features

- Arabic-first dark fantasy interface with RTL support.
- Multiple story projects with local autosave.
- Scene editor for narrative text, dialogue, characters, and branching choices.
- Story variables, conditional choice visibility, and choice effects.
- Undo / redo history, interactive preview, and project backup tools.
- Immersive Player with scene artwork, dialogue, typewriter narration, audio, and choice feedback.
- Separate Player and Academy Android application modules.
- Portable `.storygame` export containing a versioned manifest, project data, and referenced media.

## Standalone Android games

A GitHub Actions workflow can package one exported `.storygame` into its own installable Android APK:

- The story bundle is embedded and loaded automatically at launch.
- Each generated game can have its own Android application ID, launcher name, version, and optional square PNG icon.
- The workflow verifies the embedded story archive and APK identity, and publishes a SHA-256 checksum plus build metadata.
- Debug builds are available without a production signing key. Release builds require the owner-provided GitHub Actions signing secrets.

### Cloud Builder status

The Android Build Center client is implemented. It includes native OIDC Authorization Code + PKCE sign-in, local creation of the selected story package, upload to private object storage through a short-lived signed URL, build-job dispatch, status refresh, and secure artifact download handoff.

The matching backend foundation is in `cloud-build-service/` (Vercel Functions, PostgreSQL, OIDC/JWKS validation, GitHub App dispatch, and private Cloudflare R2 uploads). **It is not deployed or live.** The cloud flow therefore requires the owner to provision and configure that backend before it can be used. No GitHub token, GitHub App private key, R2 secret, or APK signing key belongs in the Android app.

Until the backend is deployed, use the manual GitHub Actions workflow described in [docs/STANDALONE_APK.md](docs/STANDALONE_APK.md). The temporary signing key used by CI is only a disposable verification fixture and must never be reused for a real game or production distribution.

See the Arabic setup and status guide: [docs/STANDALONE_APK.md](docs/STANDALONE_APK.md).

## Build and CI

The project uses Kotlin, Jetpack Compose, Android SDK 35, Java 17, and Gradle. GitHub Actions runs automated story-engine and Android build checks and publishes APK artifacts.

## Development status

STORYFORGE is actively developed. Some advanced authoring workflows, expanded localization, device-level automated tests, and secure Release signing remain future work.
