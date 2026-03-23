# Generation Worker Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add deterministic generation mode policies plus minimal worker and view-model wiring for the feature-generate module.

**Architecture:** Keep the policy pure and deterministic so it can be covered by unit tests without Android dependencies. Model the worker as a small scheduler that expands a document into ordered chunk jobs, and keep the view model as a thin wrapper over the worker with simple state updates.

**Tech Stack:** Kotlin, JUnit4, AndroidX ViewModel/runtime state, Gradle Kotlin DSL.

---

### Task 1: Add policy tests

**Files:**
- Create: `feature-generate/src/test/java/com/vodr/generate/GenerationPolicyTest.kt`

**Step 1: Write the failing test**

```kotlin
class GenerationPolicyTest {
    @Test
    fun qualityModeSchedulesAllChunksInOrder() { ... }

    @Test
    fun balancedModeSchedulesAnEarlyPrefixBeforeTheRemainingChunks() { ... }

    @Test
    fun fastStartModeSchedulesFirstChunksImmediatelyThenContinuesInOrder() { ... }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :feature-generate:testDebugUnitTest --tests com.vodr.generate.GenerationPolicyTest`
Expected: FAIL because `GenerationPolicy` does not exist yet.

**Step 3: Write minimal implementation**

Implement `GenerationPolicy` with deterministic chunk ordering for each mode.

**Step 4: Run test to verify it passes**

Run: `./gradlew :feature-generate:testDebugUnitTest --tests com.vodr.generate.GenerationPolicyTest`
Expected: PASS

**Step 5: Commit**

```bash
git add feature-generate/src/test/java/com/vodr/generate/GenerationPolicyTest.kt feature-generate/src/main/java/com/vodr/generate/GenerationPolicy.kt
git commit -m "feat: implement generation worker and mode policies"
```

### Task 2: Add worker and view model wiring

**Files:**
- Create: `feature-generate/src/main/java/com/vodr/generate/GenerationWorker.kt`
- Create: `feature-generate/src/main/java/com/vodr/generate/GenerationViewModel.kt`

**Step 1: Write the failing tests**

Add coverage for worker scheduling and simple view-model state transitions.

**Step 2: Run tests to verify they fail**

Run: `./gradlew :feature-generate:testDebugUnitTest`
Expected: FAIL until the worker and view-model exist.

**Step 3: Write minimal implementation**

Keep the worker synchronous and deterministic. Keep the view model as a thin state holder around the worker.

**Step 4: Run tests to verify they pass**

Run: `./gradlew :feature-generate:testDebugUnitTest`
Expected: PASS

**Step 5: Commit**

```bash
git add feature-generate/src/main/java/com/vodr/generate/GenerationWorker.kt feature-generate/src/main/java/com/vodr/generate/GenerationViewModel.kt
git commit -m "feat: implement generation worker and mode policies"
```
