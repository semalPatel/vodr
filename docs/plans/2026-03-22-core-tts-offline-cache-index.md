# Core TTS Offline Cache Index Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a minimal offline-safe TTS abstraction and a file-backed cache index that persists `chunkKey -> filePath` mappings across process restarts.

**Architecture:** Keep `core-tts` self-contained. `AudioCacheStore` owns a single index file inside a caller-provided directory and persists mappings using a plain text properties format. `TtsEngine` defines the tiny contract needed by callers, and `OfflineTtsEngine` uses the cache store to resolve or record generated audio paths without invoking any online or device TTS APIs.

**Tech Stack:** Kotlin, Android library unit tests, JUnit4, Java IO/util properties.

---

### Task 1: Add the failing cache persistence test

**Files:**
- Create: `core-tts/src/test/java/com/vodr/tts/AudioCacheStoreTest.kt`

**Step 1: Write the failing test**

Add a unit test that writes a `chunkKey -> filePath` mapping, constructs a new store pointed at the same directory, and asserts the mapping is still present.

**Step 2: Run test to verify it fails**

Run: `./gradlew :core-tts:testDebugUnitTest`
Expected: FAIL because `AudioCacheStore` does not exist yet.

### Task 2: Implement the minimal offline TTS module

**Files:**
- Modify: `core-tts/build.gradle.kts`
- Create: `core-tts/src/main/java/com/vodr/tts/TtsEngine.kt`
- Create: `core-tts/src/main/java/com/vodr/tts/OfflineTtsEngine.kt`
- Create: `core-tts/src/main/java/com/vodr/tts/AudioCacheStore.kt`

**Step 1: Write minimal implementation**

Add the module wiring, define a small `TtsEngine` contract, implement an offline-only engine that resolves and records cached file paths, and persist the cache index to disk with a simple file-backed format.

**Step 2: Run test to verify it passes**

Run: `./gradlew :core-tts:testDebugUnitTest`
Expected: PASS.

### Task 3: Verify the wider gate and commit

**Files:**
- Modify: any files required by the gate

**Step 1: Run the gate**

Run:
`./gradlew :core-tts:testDebugUnitTest :core-ai:testDebugUnitTest :core-segmentation:testDebugUnitTest :core-parser:testDebugUnitTest :feature-library:testDebugUnitTest :core-data:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS.

**Step 2: Commit**

Run:
```bash
git add core-tts docs/plans/2026-03-22-core-tts-offline-cache-index.md
git commit -m "feat: add offline tts engine abstraction and cache index"
```
