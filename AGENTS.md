# Project Instructions

## Project Overview

KMPBook is a Kotlin Multiplatform desktop application built with Compose Multiplatform. The current application is **Market Ledger 2040**, a turn-based Korean and U.S. stock-market simulator.

## Repository Layout

- `composeApp/src/commonMain`: shared domain, simulation, presentation, and Compose UI code.
- `composeApp/src/desktopMain`: JVM desktop entry point, persistence, platform implementations, and desktop resources.
- `composeApp/src/commonTest` and `composeApp/src/desktopTest`: existing project tests; use them for verification when relevant, but do not add test code to the final deliverable.
- `composeApp/src/commonMain/composeResources`: shared fonts, images, and data resources.
- `assets`: repository and release artwork.

Keep reusable logic in `commonMain`. Put desktop-only APIs, file-system access, and JVM-specific implementations in `desktopMain`. Preserve the existing package root, `com.amond.kmpbook`.

## Build and Verification

Use the Gradle wrapper and Java 21.

```bash
./gradlew :composeApp:compileKotlinDesktop
./gradlew :composeApp:run
./gradlew :composeApp:allTests
./gradlew :composeApp:packageMsi
./gradlew :composeApp:printAppVersion
```

Run the smallest relevant verification first, then broader compilation or tests when the change warrants it. The application version comes from the `appVersion` Gradle property and must use `MAJOR.MINOR.PATCH` within Windows MSI limits.

## Implementation Guidance

- Follow the existing Kotlin and Compose style in neighboring files.
- Keep domain and simulation rules independent from UI rendering where practical.
- Use the existing market design-system tokens and reusable UI primitives instead of duplicating visual constants or components.
- Keep platform boundaries explicit through the existing `expect`/`actual` pattern.
- Avoid unrelated rewrites and preserve user-authored changes already present in the worktree.
- Do not commit unless the user explicitly asks for a commit.

## Delivery and Test Policy

- Final deliverables must not include test code. Do not add or retain new test source files, test-only production hooks, fixtures, mocks, snapshots, or other test artifacts as part of the final result.
- Temporary test harnesses may be created and used during development to verify functionality. Remove them before delivery, or otherwise ensure they are excluded from the final result.
- Existing repository tests may be run as verification. Do not modify or expand them unless the user explicitly changes the delivery requirements.
- Report the verification commands run and their outcomes. If verification could not be completed, state that clearly.

## Compatibility Policy

This project is under active development and is not in production. Backward compatibility and migration support for older versions are unnecessary unless the user explicitly requests them. Prefer the clean current design over compatibility shims, legacy adapters, old-schema migration paths, or deprecated API preservation.

## Documentation Consistency

`AGENTS.md` and `CLAUDE.md` must remain written in English and contain exactly the same content. Any update to one file must be applied identically to the other.
