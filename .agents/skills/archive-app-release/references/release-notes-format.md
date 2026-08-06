# Release notes format

The body is **always English**. The release title is the app's display name and is left
as the user writes it (Korean is fine).

## Template

```markdown
## Overview

- <one sentence: what the app does and what platform it targets>

## Tech Stack

- <language / UI framework, with versions>
- <networking or data layer, with versions>
- <remaining notable libraries, with versions>
```

## Rules

- Two sections only: `## Overview`, then `## Tech Stack`. No preamble above the first
  heading, no closing note, no "Assets" section — GitHub already lists attachments.
- Every line is a bullet. No paragraphs.
- Overview is a single bullet. Do not pad it into several.
- Tech Stack is roughly three bullets, grouped by role, each carrying real version
  numbers read from `gradle/libs.versions.toml` — never guessed.
- Mention only libraries the app actually used. Drop anything removed during cleanup.
- No badges, no emoji, no install instructions.

## Worked example

Title: `급식`

```markdown
## Overview

- A Kotlin Multiplatform app that looks up school meals (breakfast/lunch/dinner) through the NEIS open API

## Tech Stack

- Kotlin Multiplatform 2.3.20 / Compose Multiplatform 1.10.3
- Ktor 3.1.3 (NEIS API client), kotlinx-serialization 1.8.1
- kotlinx-datetime 0.7.1, multiplatform-settings 1.3.0
```
