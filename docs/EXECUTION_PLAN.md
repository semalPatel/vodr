# Vodr Modernization Execution Plan

## Non-Negotiable Quality Gate

Every commit in this program must pass:

1. `./gradlew :app:assembleDebug`
2. `./gradlew test`

No commit is merged without passing both checks.

## Sprint 1: Build and Architecture Bedrock

- Centralize versions with `libs.versions.toml`.
- Normalize plugin application with root `apply false` plugin declarations.
- Move generation orchestration out of `NavHost` and into dedicated stateful workflow.
- Keep UI modules focused on rendering and user events.

## Sprint 2: Async and Stability Hardening

- Run expensive parsing/segmentation work on `Dispatchers.IO`.
- Convert settings persistence to asynchronous APIs.
- Introduce typed generation status states (`idle/loading/success/error`).

## Sprint 3: Sleek and Minimal UI Foundation

- Introduce app theme package and standard screen shell.
- Use `Scaffold` for visual consistency and better app-level actions.
- Add explicit empty/loading/error rendering patterns.

## Sprint 4: Functional Resilience

- Persist critical user flows and avoid in-memory-only cross-screen state.
- Improve error boundaries and retry behavior for generation and playback.
- Reduce accidental UI jank from synchronous writes.

## Sprint 5: Velocity and Developer Experience

- Expand focused tests around generation and state transitions.
- Document architectural rules for future feature work.
- Keep module boundaries clean so teams can ship features faster.
