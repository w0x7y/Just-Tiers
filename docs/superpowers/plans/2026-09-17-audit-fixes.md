# Audit fixes implementation plan

The September 17 code, test and user-experience audit is the accepted specification.
Implement on `fix/audit-findings` in the existing checkout. The initial audit implementation was local-only. The follow-up user request authorizes
committing, pushing, publishing version 1.1.2 and syncing the Modrinth description.

## Constraints

- Keep Java 25, Minecraft 26.2 and the Minecraft-free model/thin client boundary.
- Reserve empty API results for valid empty responses or documented not-found statuses.
- Keep diagnostics history while ignoring obsolete requests for current retry state.
- Settings take effect on Save; failures must remain visible and preserve the old file.
- Keep all primary controls accessible with the keyboard and at 320 by 240 GUI pixels.
- Localized player-facing copy; no em dashes in README or Modrinth description.

## Implementation and verification

- [x] API/cache: first reproduce exceptional completion during peek, obsolete callbacks
  after invalidation, malformed successful responses, and overlapping Nova refreshes.
  Fix the boundaries and run API/cache tests. Consolidate resolver and report APIs and
  migrate callers/tests before deleting obsolete overloads.
- [x] Client/UI: add persistence and small-layout regression tests before fixing them.
  Use real focusable controls for grids, lookup links and cells; reserve fixed search
  and exit rows around a scrollable lookup viewport. Add keyboard picker activation,
  retry/name editing, accurate disabled reasons, palette-aware progress, refresh feedback,
  and retryable skin fallbacks. Verify compilation and model tests, then runtime smoke checks.
- [x] Tests/resources/CI: remove only proven duplicate or vacuous assertions; check custom
  icon tint; add registry/font/texture and localization contract checks to Gradle check.
  Pin the tested Loom version, lint workflows in CI, and derive GitHub prerelease status
  from release_type. Remove the obsolete icon generator.
- [x] Documentation: revise README, Modrinth description, CLAUDE, all existing docs Markdown,
  and the backlog after implementation. Clearly mark old plans/specs as historical and
  explain superseding behavior. Document network exceptions, smoke tests and failures.
- [x] Review the combined diff, run the full build, workflow lint and resource validation;
  confirm regression tests and record any runtime-verification limits honestly.

## Ownership

API/cache and their tests belong to the production reviewer. Documentation, workflows,
resource validation and unrelated test cleanup belong to the test reviewer. The primary
agent owns config, GUI, lookup geometry and their tests. Gradle runs are coordinated to
avoid shared build-output races. No agent may revert another agent's changes.

## Verification recorded so far

- Loom 1.17.21 resolves and compiles the project. The five deprecated YACL formatter
  calls were migrated to formatValue; a fresh compile with -Xlint:deprecation emits no
  Java deprecation warnings. A Gradle plugin compatibility warning remains.
- actionlint passes for all three workflows. The release shell was exercised with local
  fixtures and a recording gh stub: alpha/beta/release, invalid type/tag, new releases,
  existing releases and sources-JAR exclusion all behaved as expected. No release was published.
- Resource checks verify packaged font providers against the Java registry, decode referenced
  PNGs and check English translation keys. Their first run correctly caught a new UI key
  before its translation was added; the completed UI/resource targeted run passed.
- Every Markdown document was reviewed; local links resolve, README/Modrinth have no em
  dashes or placeholder screenshot comments, and old implementation recipes are archived.
- A temporary real-client harness passed 26 assertions for keyboard controls, save failure/
  retry/Undo/Cancel, lookup editing, real-font small layouts, focused scrolling and link
  confirmation. Screenshots were inspected; the harness was removed afterward. See the
  [runtime checklist](../../runtime-smoke-test.md) for exact scope and remaining release checks.
- Preview overflow seen in the running YACL screen was corrected by fitting the full nametag
  to its column and wrapping its caption. Failed-save Undo now rebuilds from committed values;
  the retry message remains visible until the failure is resolved or the draft is discarded.
- After the final formatter and availability API cleanup, 31 targeted tests passed:
  20 lookup-layout tests, nine availability tests and two resource-contract tests.

## Final verification

- `./gradlew build --warning-mode all`: passed, 428 tests across 34 suites, no failures,
  errors or skipped tests. The final build emitted no deprecation warning. A separate fresh
  Java compilation with `-Xlint:deprecation` also passed after migrating YACL formatters.
- All 12 pre-existing tracked Markdown files were updated, two current verification documents
  were added, and all local Markdown links resolve. The text backlog was reconciled as well.
- `git diff --check` passed. The temporary runtime test mod was removed; the audit phase created no release, remote
  listing, tag or commit. The subsequent release request is tracked in the version release notes. Remaining environment-dependent checks are recorded in
  the runtime checklist rather than counted as passes.
