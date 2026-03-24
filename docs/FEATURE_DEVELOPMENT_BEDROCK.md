# Feature Development Bedrock

Use this as the default contract for all new work.

## Flow Contract

1. Composable renders immutable `UiState`.
2. Composable emits `UiEvent`.
3. ViewModel processes event and updates `StateFlow`.
4. Data work runs in use-cases/repositories, never in navigation or composables.

## Module Placement

- `feature-*`: UI, state, feature-specific use-cases, navigation hooks.
- `core-*`: reusable business logic and data APIs.
- `app`: composition root only (theme, navigation graph, DI wiring).

## Non-Negotiable Rules

- No Android `Context` in ViewModels.
- No synchronous disk/network work in composables.
- Keep one immutable state model per screen.
- Any expensive operation must be cancellable and moved to IO dispatcher.

## Required Checks Before Merge

1. `./gradlew :app:assembleDebug`
2. `./gradlew test`

Both must pass on every commit.
