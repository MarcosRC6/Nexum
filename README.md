# NEXUM

**NEXUM** is an Android study application combining Testario (question banks and practice/exam tests) with Planario (study planning, daily objectives and a study timer).

## Current version

- Official source version: **1.2** (`versionCode 7`).
- Android application ID: `com.example.estudario`. Keep this unchanged for in-place upgrades.
- Minimum supported Android API level: 26.
- Main navigation: **Inicio · Biblioteca · Test · Plan**.
- Official APK distribution: [GitHub Releases](https://github.com/MarcosRC6/Nexum/releases).

NEXUM checks the latest public GitHub Release from inside the app. Android always requests user approval before installing an APK. Never uninstall a currently signed version merely to update it; export a full backup first.

## Build and verification

Requirements: Java 17, Android SDK compatible with `compileSdk = 35`, and the included Gradle wrapper.

```shell
./gradlew testDebugUnitTest lintDebug assembleDebug
python3 scripts/check_publication_safety.py
```

The `nexum-audit.yml` workflow also validates debug and production builds. Signed releases are generated only by `.github/workflows/nexum-official-release.yml` on the trusted `main` branch using private GitHub Actions secrets. Publishing an official GitHub Release additionally requires a manual workflow run with `publish_release=true`.

**Never commit private signing stores, passwords, access tokens, personal study databases or backup archives.** For security disclosures, see [SECURITY.md](SECURITY.md).

## Repository layout

- `app/src/main/java/com/example/testario` — Testario features, UI and update manager.
- `app/src/main/java/com/example/planario` — planning, timers and objectives.
- `.github/workflows` — CI, audit and signed release workflows.
- `scripts/check_publication_safety.py` — source exposure preflight.

## Copyright and reuse

Public visibility allows viewing the repository; it **does not grant a software license**. No open-source or redistribution license has been selected. Code and visual assets must not be redistributed or reused beyond what applicable law and GitHub terms permit without the rights holder's permission. Asset provenance should be confirmed before public conversion.

Historical `LEEME_NEXUM_1.3.txt` describes an older development package and is **not** the current official version reference.
