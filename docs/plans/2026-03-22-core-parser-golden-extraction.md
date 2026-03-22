# Core Parser Golden Extraction Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a minimal `core-parser` module that extracts plain text and chapter markers from DRM-free PDF and EPUB samples.

**Architecture:** Keep the parser self-contained in `core-parser` with one facade (`DocumentParser`) and two format-specific adapters (`PdfParser`, `EpubParser`). Use simple, deterministic parsing for the golden fixtures: PDF text is recovered from uncompressed content streams, and EPUB text is recovered from the spine XHTML files listed in the package manifest. Normalize whitespace before chapter detection so the tests are stable.

**Tech Stack:** Kotlin, Android library unit tests, JUnit4, JDK zip/XML APIs.

---

### Task 1: Add failing golden extraction tests

**Files:**
- Create: `core-parser/src/test/java/com/vodr/parser/DocumentParserTest.kt`
- Create: `core-parser/src/test/resources/samples/sample.pdf`
- Create: `core-parser/src/test/resources/samples/sample.epub`

**Step 1: Write the failing test**

Add one PDF test and one EPUB test that assert extracted text contains the expected chapter headings and body text, and that chapter markers are returned in reading order.

**Step 2: Run test to verify it fails**

Run: `./gradlew :core-parser:testDebugUnitTest --tests com.vodr.parser.DocumentParserTest`
Expected: FAIL because parser classes do not exist yet.

**Step 3: Do not implement production code yet**

**Step 4: Keep tests as the source of truth**

### Task 2: Implement minimal parser module wiring

**Files:**
- Modify: `core-parser/build.gradle.kts`
- Create: `core-parser/src/main/java/com/vodr/parser/DocumentParser.kt`
- Create: `core-parser/src/main/java/com/vodr/parser/PdfParser.kt`
- Create: `core-parser/src/main/java/com/vodr/parser/EpubParser.kt`

**Step 1: Write minimal implementation**

Add the Android/Kotlin test wiring and implement a facade that routes by MIME type, a PDF adapter that extracts text from simple content streams, and an EPUB adapter that reads the spine XHTML files.

**Step 2: Run test to verify it passes**

Run: `./gradlew :core-parser:testDebugUnitTest --tests com.vodr.parser.DocumentParserTest`
Expected: PASS.

### Task 3: Verify the wider gate and commit

**Files:**
- Modify: any files required by the gate

**Step 1: Run the gate**

Run: `./gradlew :core-parser:testDebugUnitTest :feature-library:testDebugUnitTest :core-data:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS.

**Step 2: Commit**

Run:
```bash
git add core-parser docs/plans/2026-03-22-core-parser-golden-extraction.md
git commit -m "feat: implement drm-free pdf and epub parsing"
```
