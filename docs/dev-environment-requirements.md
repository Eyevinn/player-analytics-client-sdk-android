# Development Environment Requirements

Issue: #12 — "specify requirements for the development environment setup"
(split from #6 during backlog triage; #6 had an empty body).

This document is a requirements-clarification deliverable. It records the **actual** state of
this repository's development-environment setup as of the referenced commit, answers the open
questions raised on the issue, and proposes concrete, verifiable follow-up work. Every claim
below cites a real file in this repo.

## 1. Scope clarification: what the issue is (and is not) about

### 1.1 "Ad Normalizer" — not a concept in this repository

The issue body asks what the "Ad Normalizer" is and how it relates to this SDK and the EPAS
spec. After reading the full source tree, **there is no "ad normalizer" component anywhere in
this repository** — no type, file, symbol, comment, or doc mentions one. A repo-wide search for
`ad[-]?normaliz*` returns nothing.

The only "normalizer" that exists is `DurationNormalizer`
(`app/src/main/java/com/analytics/sdk/DurationNormalizer.kt`), which is unrelated to ads: it
maps an ExoPlayer `duration` value onto the EPAS wire contract's `duration` field (mapping the
unknown-duration sentinel and any non-positive value to the spec's `-1`). It is covered by
`app/src/test/java/com/analytics/sdk/DurationNormalizerTest.kt`.

Conclusion: the "Ad Normalizer" phrasing does not correspond to anything in
`player-analytics-client-sdk-android`. It appears to have been carried over generically when #12
was split from the empty-bodied #6 and **points outside this repo**. It should not drive any
work here. If a genuine ad-value-normalization requirement exists, it belongs to a separate,
explicitly-scoped issue (and, if it concerns event schema, to `player-analytics-specification`
first — see Section 5). This document therefore treats #12 purely as: *specify and document the
development-environment setup for this Android SDK.*

### 1.2 What "development environment" means here

Grounded in what the repo already contains, "development environment" for this repo means the
tooling and steps a contributor needs to **build, test, and run the SDK plus its bundled sample
app locally, and to have those same steps enforced in CI**. Concretely that breaks down into:

- Build tooling: the Gradle/AGP/Kotlin/JDK/SDK versions the project pins.
- Dependencies: how they are declared and resolved.
- Local run: the sample app used to exercise the SDK by hand.
- Test: how to run the existing unit tests.
- CI: automated build/test on push/PR (currently absent — see Section 3).

It does **not** mean adding a new build system, changing the module layout, or implementing new
SDK features. This chore is about documenting and closing gaps in the existing setup, not
re-architecting it.

## 2. Current development-environment setup (as-is, from real files)

### 2.1 Build tooling and versions

| Aspect | Value | Source file |
|--------|-------|-------------|
| Gradle wrapper | 8.4 | `gradle/wrapper/gradle-wrapper.properties` (`distributionUrl=...gradle-8.4-bin.zip`) |
| Android Gradle Plugin (AGP) | 8.3.2 | `gradle/libs.versions.toml` (`agp = "8.3.2"`) |
| Kotlin | 1.9.22 | `gradle/libs.versions.toml` (`kotlin = "1.9.22"`) |
| compileSdk | 35 | `app/build.gradle` |
| targetSdk | 35 | `app/build.gradle` |
| minSdk | 34 | `app/build.gradle` |
| Java source/target compatibility | 1.8 (`jvmTarget = '1.8'`) | `app/build.gradle` (`compileOptions`, `kotlinOptions`) |
| Jetpack Compose | enabled; compiler ext 1.5.10 | `app/build.gradle` (`buildFeatures { compose true }`, `composeOptions`) |
| Kotlin code style | `official` | `gradle.properties` (`kotlin.code.style=official`) |
| AndroidX | enabled | `gradle.properties` (`android.useAndroidX=true`) |

Implied JDK: AGP 8.3.x and Gradle 8.4 require **JDK 17** to *run* the Gradle build, even though
the project *compiles* bytecode at Java 8 level (`sourceCompatibility`/`targetCompatibility` /
`jvmTarget = '1.8'`). This distinction (build-JDK 17 vs. target bytecode 8) is currently
undocumented and is a common contributor pitfall — see Section 3.

### 2.2 Module layout

- Single Gradle module `:app`, declared in `settings.gradle` (`include ':app'`).
- Despite the name, `:app` is built as an **Android library**, not an application:
  `app/build.gradle` applies `alias(libs.plugins.androidLibrary)` (`com.android.library`) with
  `namespace 'eyevinn.com.client.sdk.android'` and publishes via `maven-publish` to JitPack
  (`groupId = 'com.github.Eyevinn'`, `artifactId = 'player-analytics-client-sdk-android'`,
  `version = '1.0.0'`).
- The same module also carries a **runnable sample app**: a launcher activity and player
  screens live under `app/src/main/java/com/analytics/sampleplayer/`
  (`SGAIPlayerActivity.kt`, `SimplePlayerActivity.kt`,
  `sgai/ui/SGAIVideoPlayerScreen.kt`), with a launcher `<activity>` and an ad-SDK
  `<meta-data>` application id declared in `app/src/main/AndroidManifest.xml`.
- The SDK sources proper live under `app/src/main/java/com/analytics/sdk/`
  (e.g. `VideoAnalyticsTracker.kt`, `AnalyticsEventSender.kt`, the SGAI tracking classes, and
  `DurationNormalizer.kt`).

Note (documentation gap, not a code change for this issue): a library module that both publishes
as a library **and** ships launcher-activity + sample-player code and an ad-SDK application-id in
its manifest is an unusual mix. Whether the sample app should be split into its own
`com.android.application` module is a design question worth a separate issue; it is out of scope
here and only flagged so the current shape is understood.

### 2.3 Dependencies

- Declared through a Gradle **version catalog**: `gradle/libs.versions.toml`
  (`[versions]`, `[libraries]`, `[plugins]`).
- Repositories are configured in `settings.gradle` under `dependencyResolutionManagement`
  (`google()`, `mavenCentral()`) with `RepositoriesMode.FAIL_ON_PROJECT_REPOS`; consumers of
  the published artifact additionally need JitPack, per `README.md` "Installation".
- Runtime stack (from `app/build.gradle` + catalog): AndroidX Media3/ExoPlayer (HLS/DASH),
  Jetpack Compose (via BOM) + Material3, Retrofit + Gson converter, and a third-party
  client-side ad-insertion SDK. Some Media3 coordinates are pinned inline as string literals
  in `app/build.gradle` (e.g. `"androidx.media3:media3-exoplayer:1.4.1"`) **in addition** to
  catalog-referenced Media3 entries pinned at `1.8.0-alpha01` in `libs.versions.toml`. That is a
  real version inconsistency (see Section 3).

### 2.4 Building and running locally (current, undocumented reality)

The `README.md` documents how to *consume* the published SDK, but there are **no
contributor-facing build/run/test instructions** in the repo. Based on the actual Gradle setup,
the working commands are:

- Assemble the library:   `./gradlew :app:assembleDebug`
- Run unit tests:         `./gradlew :app:testDebugUnitTest`  (or `./gradlew test`)
- Lint:                   `./gradlew :app:lint`
- Install/run sample app: via Android Studio Run configuration, or
  `./gradlew :app:installDebug` on a connected device/emulator, launching the
  `SGAIPlayerActivity` launcher activity.

These should be captured in a contributor-facing doc (see acceptance criteria, Section 4).

### 2.5 Tests present

- Unit tests: `app/src/test/java/com/analytics/sdk/DurationNormalizerTest.kt` — plain JVM JUnit,
  no Android/ExoPlayer runtime required. This is currently the **only** automated test.
- Instrumented-test wiring exists (`testInstrumentationRunner "androidx.test.runner..."` in
  `app/build.gradle`, plus `androidTest*` dependencies) but there are **no** instrumented test
  sources under `app/src/androidTest/`.

## 3. Gaps in the current setup

These are the concrete gaps that "specify requirements for the development environment" should
close. Each is grounded in a real file above.

1. **No CI.** There is no `.github/` directory and no workflow of any kind. Nothing enforces
   that the build compiles or that `DurationNormalizerTest` passes on push/PR.
2. **No contributor build/test/run documentation.** `README.md` and `sdk-usage-guide.md` cover
   *using* the SDK, not *developing* it. The build-JDK-17-vs-target-JDK-8 requirement, the
   Gradle wrapper usage, and the sample-app run steps are all undocumented.
3. **Placeholder project name.** `settings.gradle` still sets
   `rootProject.name = "YourProjectName"` — an IDE-template leftover that should be the real
   project name.
4. **Dependency-version inconsistency.** Media3 is pinned to `1.8.0-alpha01` in
   `gradle/libs.versions.toml` but several Media3 artifacts are hard-coded to `1.4.1` as inline
   string dependencies in `app/build.gradle`. Contributors get a mixed, ambiguous set.
5. **Documentation/config mismatch on minSdk.** `README.md` "Requirements" states "Android API
   21+", but `app/build.gradle` sets `minSdk 34`. One of the two is wrong; the environment doc
   must state the authoritative value.
6. **Missing LICENSE file.** `README.md` states the project is MIT-licensed and links to a
   `LICENSE` file, but no `LICENSE` file exists at the repo root. (Flagged for a separate
   issue; not fixed here.)
7. **No `local.properties`/SDK-location guidance.** `local.properties` is correctly gitignored
   (`.gitignore`), but nothing documents that a contributor must point `sdk.dir` /
   `ANDROID_HOME` at an installed Android SDK before the build resolves.

## 4. Acceptance criteria for "development environment is set up"

The dev-environment work is "done" when all of the following are true and independently
verifiable:

1. A contributor-facing "Development / Contributing" section (in `README.md` or a
   `docs/`/`CONTRIBUTING.md` doc) documents, at minimum:
   - Required build JDK (17) vs. the project's target bytecode level (8), and the pinned
     Gradle (8.4) / AGP (8.3.2) / Kotlin (1.9.22) versions, each matching the source files in
     Section 2.1.
   - Android SDK prerequisites (compileSdk 35; the authoritative minSdk once gap #5 is
     resolved) and `local.properties` / `ANDROID_HOME` setup.
   - Copy-pasteable commands to **build**, **unit-test**, **lint**, and **run the sample app**,
     matching Section 2.4.
2. A fresh checkout can be built and unit-tested from those documented commands alone, with no
   undocumented manual steps (`./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest`
   both succeed on a machine with JDK 17 + Android SDK).
3. CI runs those same build + unit-test commands on every PR to `main`, and is green on a PR
   that changes nothing but docs (gap #1 closed).
4. The IDE-template leftovers that block a clean, unambiguous setup are resolved or explicitly
   tracked: `rootProject.name` (gap #3) and the Media3 version inconsistency (gap #4).
5. This document's version/setup claims stay consistent with the build files; if a version is
   bumped, this doc and the "Development" section are updated in the same change.

## 5. Upstream / cross-repo dependencies

- **No hard upstream blocker exists for the dev-environment work itself.** Documenting and
  CI-enforcing the existing build/test is entirely local to this repo.
- The one genuine cross-repo coupling is the **EPAS event/schema contract**: `DurationNormalizer`
  is written against `player-analytics-specification`'s `duration` field definition (cited in its
  own KDoc). Any change to how event *values* are normalized is a specification-first concern and
  must be settled in `player-analytics-specification` (and coordinated with `eventsink`) before it
  lands here — but that is orthogonal to #12 and only relevant if the misfiled "ad normalizer"
  idea (Section 1.1) is ever pursued as its own scoped issue.

## 6. Proposed follow-up sub-issues

Each is small, independently verifiable, and maps to a gap above:

1. **Add CI** (gap #1): a GitHub Actions workflow under `.github/workflows/` on JDK 17 that runs
   `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` on PRs to `main`.
2. **Add contributor build/test/run docs** (gaps #2, #7): a "Development" section /
   `CONTRIBUTING.md` capturing Section 2.1/2.4 and `local.properties` setup.
3. **Fix `rootProject.name`** (gap #3): set the real project name in `settings.gradle`.
4. **Reconcile Media3 versions** (gap #4): converge the inline `app/build.gradle` Media3
   coordinates and the catalog `media3*` versions onto a single pinned value.
5. **Reconcile the documented minSdk** (gap #5): make `README.md` "Requirements" agree with
   `app/build.gradle` `minSdk`.
6. **Add the missing LICENSE file** (gap #6): add the MIT `LICENSE` the README already links to.

## 7. Verification note

This is a documentation-only change. No Gradle build, unit test, or lint run was performed
because the automation host has no JDK/Gradle toolchain installed; verification for this change
is a diff read. The build/test/lint commands prescribed above are derived from the repo's own
pinned tooling (Section 2.1) and its single existing test
(`app/src/test/java/com/analytics/sdk/DurationNormalizerTest.kt`), not from a local run.
