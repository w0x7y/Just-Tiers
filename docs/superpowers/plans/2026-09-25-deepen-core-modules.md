# Deepen core modules implementation plan

**Goal:** Implement all three opportunities accepted from the architecture report: settings application, lookup orchestration and Minecraft target support.
**Spec:** User instruction "Implement and fix all of them" and `/tmp/architecture-review-20260925-145813.html`.
**Architecture:** Each module owns the policy its callers currently coordinate. Minecraft, storage, scheduler and build tools adapt that policy; tests exercise observable behavior through the same seam.
**Stack:** Java 21/25, JUnit 5, Fabric/YACL, Gradle Kotlin DSL, Python standard library, GitHub Actions.

## Constraints

- Preserve the six current Minecraft targets and their dependencies, especially 26.3's Loader 0.19.5 floor.
- Preserve all existing compatibility changes in this working tree; do not commit, publish or discard them.
- Keep accepted ADR 0001 and existing badge, report, cache and layout logic.
- Assign distinct files to each implementation stream. Serialize Gradle with `flock /tmp/justtiers-gradle.lock`; build configuration changes must settle before full integration checks.
- Add behavior tests for application policy and asynchronous orchestration. Keep independent inspection of actual JAR contents.

## Task 1: Settings application

Files: `JustTiersClient.java`, `command/JustTiersCommands.java`, `gui/JustTiersScreens.java`, `gui/ConfigScreen.java`, new settings/runtime modules and their tests.

- [x] Add failing tests for draft save failure, command save failure, successful publication, unchanged refresh intervals and cache TTL effects.
- [x] Move active-setting publication and persistence policy behind a settings application module. Screen drafts persist before publication; command edits remain live if persistence fails. Migrate both callers and delete replaced save entrypoints.
- [x] Move actual refresh scheduling policy into a testable implementation so unchanged intervals do not reset countdowns. Keep manual and scheduled refresh behavior unchanged.
- [x] Use real temporary files and the real TierCache when practical; inject deterministic scheduling only where time varies. Keep YACL rendering/retry controls in its adapter.
- [x] Run focused settings/runtime tests and report affected files and observable guarantees.

## Task 2: Minecraft target support

Files: `build.gradle.kts`, `gradle/compatibility.gradle.kts`, `gradle/targets/`, `tools/verify_artifact.py`, new target policy tooling/tests, build/release/dry-run workflows and `docs/minecraft-compatibility.md`.

- [x] Add failing tests for target discovery, malformed profiles and incompatible packaged artifacts.
- [x] Make target declarations the single source for supported versions, Java and Loader requirements, source conversion and mapping strategies. Distinguish tested Loader from minimum Loader.
- [x] Generate workflow matrices and release artifact expectations from target discovery. Remove hardcoded version lists/counts and verifier version inference.
- [x] Keep bytecode and mixin namespace checks against actual JAR bytes. Invalid profiles fail before publishing. Preserve independent per-target publishing jobs.
- [x] Run tooling tests and actionlint. Stabilize build scripts before asking for full builds.

## Task 3: Lookup orchestration

Files: replace `gui/LookupSession.java` with a Minecraft-free `lookup/LookupSession.java`, add a game-facing lookup adapter, migrate `gui/PlayerLookupScreen.java`, add lookup session tests.

- [x] Write failing tests for online preference, invalid/unknown/unavailable names, concurrent site requests, partial results, all-unranked completion and manual failure retry.
- [x] Put name resolution, site fan-out, failure eviction and partial-result state into the session implementation. Inject production/test dependencies at an internal seam, without exposing collaborators to screen callers.
- [x] Keep translation, skin loading and client scheduling in the Minecraft adapter. Use controllable futures and a queued executor to verify publication timing.
- [x] Exercise retry against the real TierCache with controlled TierSource futures. Remove the old game-bound coordinator after migrating its only caller.
- [x] Run focused session tests.

## Integration

- [x] Record the agreed domain terms in CONTEXT.md and update contributor guidance.
- [x] Review the three changes for specification and code quality; fix actionable findings.
- [x] Run all tests and artifact validation for every discovered target, workflow lint and diff checks.
- [x] Run disposable client smoke checks on all targets. Record results and limits in the runtime checklist.

## Verification result

All six discovered targets built successfully with 444 JUnit tests each and no
failures or skipped tests. Every installable JAR passed metadata, Java bytecode and
mixin namespace validation. The 11 Python tooling tests, actionlint and whitespace
checks passed. Fresh client runs on all six targets exercised mixin transformation,
config/grid/lookup rendering, failed draft save and successful retry, plus translated
lookup errors. See `docs/runtime-smoke-test.md` for evidence and untested release checks.

Independent review found a verifier issue with spaces in unrelated Gradle properties;
the final reader inspects only the plain mod_version declaration, and its regression
test passes. Settings and lookup review found no actionable defects.
