# Optional verification tools

These tools live in separate Gradle source sets. Normal `build`, `jar`, `remapJar`
and `sourcesJar` do not include them. They add no runtime dependency to the mod.

## Warmed badge benchmark

```bash
./gradlew badgeBenchmark
./gradlew badgeBenchmark -PbenchmarkIterations=1000000
```

The benchmark creates a real `TierCache` with controlled successful sources, warms
all three sites for 128 fixed v4 UUIDs, and calls `Badge.forPlayer` through a
config-backed `TierView`. Each display mode gets a warmup and five measured rounds.
Output includes nanoseconds per operation and per-thread allocated bytes measured
with `com.sun.management.ThreadMXBean`. Unsupported allocation measurement is
reported explicitly. The final assertion proves no additional fetch occurred during
measurement. The result is consumed to keep the computation observable.

Use the same JDK, target and iteration count for comparisons. Early rounds can still
include JIT compilation. Compare steady rounds and repeat in a quiet environment.
The tool has no timing thresholds and is excluded from normal tests. It measures
the cache/model path, not Minecraft component construction, GPU rendering or frame
rate.

## Controlled client smoke

The current harness targets Minecraft 26.2 and requires a working graphical display
and OpenGL. Use JDK 25 and the dependencies pinned in `gradle/targets/26.2.properties`.
On Linux, the command can be wrapped in `xvfb-run -a` when Xvfb and a compatible
software OpenGL driver are installed.

```bash
timeout 360 ./gradlew runSmokeClient -PclientSmoke=true -Pminecraft_version=26.2
```

The optional auxiliary Fabric mod runs in `build/26.2/smoke-instance`, creates a new
integrated world each run and exits after its checks. It replaces the development
client's cache and settings with controlled sources and a separate config file.
The production initializer may still download the NovaTiers index on startup, and
Minecraft may contact its own services. The harness is not an offline-network test.

The checks use an actual `RemotePlayer` in the integrated client world. Its real
`Player.getDisplayName()` executes the production mixin. Checks cover cached badges,
before/after position, team prefix text and styles, icon-font isolation from labels
and the player name, font measurement, custom colors, retired settings, display
disable, icon/bracket settings, hide-own behavior and a v3 offline player. The local
player temporarily receives a controlled v4 UUID during hide-own and badge-restoration
checks, then gets its original development UUID back. This tests the local-player
branch even when the development profile uses an offline v3 UUID.

The config, gamemode grid and invalid-name lookup screens initialize at 320x240,
427x240 and 480x270 GUI pixels. Each remains visible at 480x270 for 40 client ticks
so rendering failures can surface. The lookup's translated error narration is
checked. These checks do not prove full keyboard navigation or the appearance of
every resized screen.

Read `build/26.2/smoke-instance/justtiers-smoke-report.txt` and
`build/26.2/smoke-instance/logs/latest.log`. The Gradle task requires the final
`JUSTTIERS_SMOKE_SUCCESS` marker and fails if it is missing or any assertion failed.
A five-minute in-client deadline catches a stalled setup after initialization; the
shell timeout also catches a client that cannot reach initialization. The task
deletes the previous report before launching, so an earlier pass cannot hide a
failed startup.

This is a controlled in-game simulation. It does not verify authenticated
multiplayer, server-supplied identities, account skins, ModMenu, spoken narration,
live site recovery or visual glyph correctness. Keep the remaining manual checks
in [the runtime checklist](runtime-smoke-test.md) before releasing. The harness
currently rejects other Minecraft targets explicitly; compilation and packaging
checks for those targets remain separate.

## September 30 measurements

On Java 25.0.4.1, Minecraft 26.2, with 200,000 operations per round, the cache changes
and uncached config snapshot used 696 bytes per single-site badge and 1488 bytes
in All mode in steady rounds. Caching the immutable nametag settings reduced these
to about 672 and 1432 bytes. All-mode timings were about 334-352 ns before and
341-345 ns afterward. This supports the allocation reduction; it does not establish
a throughput improvement. Config tests cover setter invalidation, old snapshot
immutability, independent drafts and saved-file round trips.

## Reliability integration verification

After the Nova freshness, cooldown and queue-race review fixes, all six supported
Minecraft targets passed 479 JUnit tests each with no failures, errors or skips.
Each installable JAR passed `tools/verify_artifact.py`; inspection of installable and
sources JARs found no smoke or benchmark classes. The tooling suite passed 11 tests.
The final 26.2 smoke run repeated all 28 assertions and ended with
`JUSTTIERS_SMOKE_SUCCESS`. Independent review found no remaining blocking issues.

The final 200,000-operation benchmark kept All-mode allocation at 1432 bytes per
badge and measured about 333-344 ns per operation, with no additional source
requests. Single-site allocations varied between 568 and 632 bytes in steady
rounds as JIT optimization differed between modes. These measurements cover the
warmed cache/model path; they establish no multiplayer frame-rate improvement.

## Version 1.1.6 architecture verification

After the lookup result, request admission and shared retention refactors, all six
targets passed 497 JUnit tests each with no failures, errors or skips. Each 1.1.6
installable JAR passed artifact verification, and both installable and sources JARs
exclude verification-only classes. The Python suite passed 11 tests. The final 26.2
client smoke check passed all 28 assertions and reported `JUSTTIERS_SMOKE_SUCCESS`.
The independent review found no remaining blocking issues after fixing refresh lock
ordering, completion capacity bookkeeping and captured refresh progress.
