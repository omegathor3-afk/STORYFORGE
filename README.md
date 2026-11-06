# STORYFORGE

> اصنع عوالمك… واترك القصة تتكلم
> Create your worlds… and let the story speak.

## Milestone 1 — Foundation

This repository is the foundation for **STORYFORGE**, an Android app built with Kotlin and Jetpack Compose. Milestone 1 establishes the basic project structure and core components:

- **Build configuration**: Gradle Kotlin DSL (`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`)
- **Android manifest**: Configured with `MainActivity` as the launcher activity, material theme, and RTL support
- **MainActivity.kt**: A minimal Compose UI with story title/body inputs, a "إنشاء مسودة" (Create Draft) button, and a dark material theme using gold accents (`#D6B477`)
- **CI/CD**: GitHub Actions workflow (`.github/workflows/android.yml`) that builds debug APKs on push to `main` or manual dispatch

This is a **foundation release**, not a final product. Future milestones will expand the story-creation features, persistence, and publishing workflows.
