# Just-Tiers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Fabric 26.2 client mod that displays a player's PvP tier in their nametag, sourced from MCTiers, SubTiers and NovaTiers, with a per-site gamemode selection and an "All" mode showing each site's best tier side by side.

**Architecture:** Three `TierSource` implementations normalise three dissimilar HTTP APIs into one `Map<gamemodeSlug, Tier>` per player. A `TierCache` coalesces in-flight requests and caches negative results. A pure `TierResolver` applies the four display modes and produces a `NametagModel` (a Minecraft-free list of coloured segments). Only the final renderer and the mixin touch Minecraft classes, so the entire domain, HTTP-parsing and resolution layers are unit-testable with plain JUnit.

**Tech Stack:** Java 25, Gradle 9.5.1, Fabric Loom 1.17, Fabric Loader 0.19.3, Fabric API 0.157.0+26.2, Mojang official mappings, MixinExtras, Gson, JUnit 5.

## Global Constraints

- Minecraft version is exactly `26.2`. Mod is **client-side only** (`"environment": "client"`).
- Java toolchain **25**, `options.release = 25`. Gradle wrapper **9.5.1**. Loom **1.17-SNAPSHOT**.
- Mappings are **official Mojang mappings**. Yarn is not published for 26.2 — do not attempt to use it. Class names in this plan are Mojmap (`Player`, `Component`, `ChatFormatting`, `Identifier`).
- Mod id is `justtiers`. Root package is `com.idangilboa.justtiers`. Resource namespace is `justtiers`.
- Packages `tier`, `api`, `cache`, `resolve` and `render.model` **must not import any `net.minecraft.*` class.** This is what keeps them unit-testable. Only `render.NametagRenderer`, `mixin`, `command` and `JustTiersClient` may import Minecraft.
- Tier ordering, lowest to highest: `LT5 < HT5 < LT4 < HT4 < LT3 < HT3 < LT2 < HT2 < LT1 < HT1`.
- Site colours: MCTiers `0xFFFF55` (yellow), SubTiers `0x55FFFF` (cyan), NovaTiers `0xAA55FF` (purple).
- Retired tiers **count** toward "highest tier" but render with an `R` prefix in light red `0xFF5555`, which overrides the site colour.
- **Peak tiers are parsed but never displayed.** Ignore `peak_tier`/`peak_pos`/`peakTiers` in all resolution and rendering.
- Nametag format: `[` + entries joined by a single space + `] ` + original name. Brackets are dark grey `0x555555`. Each entry is the gamemode icon glyph followed immediately by the tier label.
- Never block the render thread on HTTP. A cache miss returns "no tier" and schedules an async fetch.
- All outbound HTTP must send `User-Agent: Just-Tiers/<modversion> (+https://github.com/idangilboa/Just-Tiers)`.

## Verified API Reference

These were confirmed against the live services on 2026-08-11. Do not re-derive them.

**MCTiers** base `https://mctiers.com/api` and **SubTiers** base `https://subtiers.net/api` share an identical v2 schema:

| Endpoint | Returns |
|---|---|
| `GET {base}/v2/mode/list` | `{ "<slug>": { "title": str, ... }, ... }` |
| `GET {base}/v2/profile/{uuid}/rankings` | `{ "<slug>": Ranking, ... }` |
| `GET {base}/v2/profile/by-name/{name}` | full profile incl. `rankings` |

`Ranking` = `{"tier": 1-5, "pos": 0|1, "peak_tier": int?, "peak_pos": int?, "attained": long, "retired": bool}`.
**`pos == 0` means HIGH (HT), `pos == 1` means LOW (LT).** Verified: Marlowww is `{"tier":1,"pos":0}` on `vanilla` and is HT1 there.

**An unranked player returns HTTP 404 with an empty body.** This is normal and MUST be mapped to an empty tier map, not an error. Both dashed and undashed UUIDs are accepted.

MCTiers gamemode slugs (8): `sword pot vanilla nethop axe smp uhc mace`
SubTiers gamemode slugs (12): `bed bow creeper debuff dia_crystal dia_smp elytra manhunt minecart og_vanilla speed trident`

**NovaTiers** is a different backend (Spring Boot) with **no per-player endpoint**. The only data source is:

`GET https://novatiers.com/users` -> a JSON array of every ranked player (6,474 players / ~1.9 MB as of writing).

Each element:
```json
{ "discordId": "...", "minecraftUuid": "4b25be2497f54adf967d8d69ef54d504",
  "minecraftUsername": "X_SUS", "region": "EU", "avatar": "...",
  "tiers": {"Elytra": "HT4", "Modern SMP": "HT3"},
  "peakTiers": {"Elytra": "LT3"},
  "retiredTiers": {"Elytra": false} }
```
`minecraftUuid` is **undashed**. Tier values are literal strings `HT1`..`LT5`, optionally `R`-prefixed (`RHT1`). Retirement is authoritative from the `retiredTiers` boolean map; also accept an `R` prefix defensively. Keys in the three maps are **display names with spaces**, not slugs.

NovaTiers gamemodes (12), slug -> display name:
`spearmace`=Spear Mace, `elytraspear`=Elytra Spear, `modernsmp`=Modern SMP, `smp`=SMP, `diamondop`=Diamond OP, `spleef`=Spleef, `pufferfish`=Pufferfish, `uhc`=UHC, `diamondcart`=Diamond Cart, `vanilla`=Vanilla, `axe`=Axe, `elytra`=Elytra

## File Structure

```
build.gradle.kts                 Loom, deps, Java 25 toolchain
settings.gradle.kts              Fabric maven, foojay toolchain resolver
gradle.properties                versions in one place
gradle/wrapper/…                 Gradle 9.5.1
NOTICE                           MPL-2.0 attribution for reused icon art
tools/gen_nova_icons.py          generates the 12 NovaTiers icon PNGs
src/main/java/com/idangilboa/justtiers/
  JustTiers.java                 mod id, logger, HttpClient, wiring
  JustTiersClient.java           ClientModInitializer entrypoint
  tier/Tier.java                 tier value type + ordering + parsing
  tier/Source.java               MCTIERS / SUBTIERS / NOVATIERS
  tier/Gamemode.java             record(source, slug, displayName, icon)
  tier/Gamemodes.java            static registry of all 32 gamemodes
  api/TierSource.java            interface
  api/MctiersLikeSource.java     MCTiers + SubTiers (shared v2 schema)
  api/NovaTiersSource.java       bulk /users fetch + UUID index
  cache/TierCache.java           per-source async cache, request coalescing
  resolve/DisplayMode.java       MCTIERS_ONLY / SUBTIERS_ONLY / NOVATIERS_ONLY / ALL
  resolve/TierResolver.java      the four display modes
  render/model/Segment.java      (icon, text, colour) — Minecraft-free
  render/model/NametagModel.java list of Segments — Minecraft-free
  render/NametagRenderer.java    NametagModel -> Component
  config/JustTiersConfig.java    settings + Gson persistence
  command/JustTiersCommands.java client commands
  mixin/PlayerMixin.java         Player#getDisplayName
src/main/resources/
  fabric.mod.json
  justtiers.mixins.json
  assets/minecraft/font/default.json    font providers for 32 glyphs
  assets/justtiers/textures/{mctiers,subtiers,novatiers}/*.png
src/test/java/com/idangilboa/justtiers/…   JUnit 5 tests
```

**Icon codepoints.** MCTiers `U+E101`-`U+E108`, SubTiers `U+E201`-`U+E20C`, NovaTiers `U+E301`-`U+E30C`. Assigned per gamemode in alphabetical order of slug within each site (exact assignments in Task 3). Write them in Java as `\uE101` etc.

---

### Task 1: Buildable, launchable Fabric 26.2 skeleton

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `src/main/resources/fabric.mod.json`
- Create: `src/main/java/com/idangilboa/justtiers/JustTiers.java`
- Create: `src/main/java/com/idangilboa/justtiers/JustTiersClient.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `JustTiers.MOD_ID` (`String` = `"justtiers"`), `JustTiers.LOGGER` (`org.slf4j.Logger`), `JustTiers.httpClient()` returning `java.net.http.HttpClient`, `JustTiers.VERSION` (`String`).

- [ ] **Step 1: Create the Gradle wrapper at 9.5.1**

```bash
mkdir -p gradle/wrapper
cat > gradle/wrapper/gradle-wrapper.properties <<'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF
```

Then obtain `gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar`. If a system Gradle 9.x is available run `gradle wrapper --gradle-version 9.5.1`. Otherwise download them:

```bash
curl -sL -o gradle/wrapper/gradle-wrapper.jar \
  https://raw.githubusercontent.com/gradle/gradle/v9.5.1/gradle/wrapper/gradle-wrapper.jar
curl -sL -o gradlew  https://raw.githubusercontent.com/gradle/gradle/v9.5.1/gradlew
curl -sL -o gradlew.bat https://raw.githubusercontent.com/gradle/gradle/v9.5.1/gradlew.bat
chmod +x gradlew
```

- [ ] **Step 2: Write `settings.gradle.kts`**

The foojay resolver is required: the toolchain needs JDK 25 and the dev machine has JDK 26, so Gradle must be allowed to auto-provision.

```kotlin
rootProject.name = "just-tiers"

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
```

- [ ] **Step 3: Write `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true

minecraft_version=26.2
loader_version=0.19.3
fabric_api_version=0.157.0+26.2
mod_version=1.0.0
maven_group=com.idangilboa
archives_base_name=just-tiers
```

- [ ] **Step 4: Write `build.gradle.kts`**

```kotlin
plugins {
    id("fabric-loom") version "1.17-SNAPSHOT"
    id("java")
}

version = "${property("mod_version")}+mc${property("minecraft_version")}"
group = property("maven_group")!!

base { archivesName = property("archives_base_name") as String }

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
```

- [ ] **Step 5: Write `fabric.mod.json`**

```json
{
  "schemaVersion": 1,
  "id": "justtiers",
  "version": "${version}",
  "name": "Just-Tiers",
  "description": "Displays a player's PvP tier in their nametag, from MCTiers, SubTiers and NovaTiers.",
  "authors": ["Idan Gilboa"],
  "contact": { "sources": "https://github.com/idangilboa/Just-Tiers" },
  "license": "MIT",
  "environment": "client",
  "entrypoints": {
    "client": ["com.idangilboa.justtiers.JustTiersClient"]
  },
  "mixins": ["justtiers.mixins.json"],
  "depends": {
    "minecraft": ">=26.2",
    "fabricloader": ">=0.19",
    "fabric-api": "*",
    "java": ">=25"
  }
}
```

- [ ] **Step 6: Write `JustTiers.java`**

```java
package com.idangilboa.justtiers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

public final class JustTiers {
    public static final String MOD_ID = "justtiers";
    public static final String VERSION = "1.0.0";
    public static final String USER_AGENT =
            "Just-Tiers/" + VERSION + " (+https://github.com/idangilboa/Just-Tiers)";

    public static final Logger LOGGER = LoggerFactory.getLogger("Just-Tiers");

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    public static HttpClient httpClient() {
        return HTTP_CLIENT;
    }

    private JustTiers() {
    }
}
```

- [ ] **Step 7: Write `JustTiersClient.java` (stub for now)**

```java
package com.idangilboa.justtiers;

import net.fabricmc.api.ClientModInitializer;

public class JustTiersClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        JustTiers.LOGGER.info("Just-Tiers {} initialising", JustTiers.VERSION);
    }
}
```

- [ ] **Step 8: Write a minimal `justtiers.mixins.json`**

An empty mixin list is valid and keeps `fabric.mod.json` honest until Task 11 adds the mixin.

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.idangilboa.justtiers.mixin",
  "compatibilityLevel": "JAVA_21",
  "mixins": [],
  "client": [],
  "injectors": { "defaultRequire": 1 }
}
```

- [ ] **Step 9: Write `.gitignore`**

```
.gradle/
build/
run/
*.class
.idea/
*.iml
```

- [ ] **Step 10: Verify the build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`, and `build/libs/just-tiers-1.0.0+mc26.2.jar` exists.

The first run downloads Minecraft, remaps it and may take several minutes. If it fails with a toolchain error, confirm the foojay plugin is in `settings.gradle.kts` and that Gradle can reach the network.

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat: Fabric 26.2 project skeleton"
```

---

### Task 2: The `Tier` value type

This is the heart of the ranking system. Everything else orders and formats through it.

**Files:**
- Create: `src/main/java/com/idangilboa/justtiers/tier/Tier.java`
- Test: `src/test/java/com/idangilboa/justtiers/tier/TierTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `record Tier(int level, boolean high, boolean retired) implements Comparable<Tier>` with `int rank()`, `String label()`, and `static Optional<Tier> parse(String raw)`. **Lower `rank()` is better**: HT1 = 0, LT1 = 1, HT2 = 2, … HT5 = 8, LT5 = 9.

- [ ] **Step 1: Write the failing test**

```java
package com.idangilboa.justtiers.tier;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TierTest {

    @Test
    void rankOrdersHt1BestAndLt5Worst() {
        assertEquals(0, new Tier(1, true, false).rank());
        assertEquals(1, new Tier(1, false, false).rank());
        assertEquals(2, new Tier(2, true, false).rank());
        assertEquals(8, new Tier(5, true, false).rank());
        assertEquals(9, new Tier(5, false, false).rank());
    }

    @Test
    void sortingProducesTheDocumentedOrder() {
        List<Tier> tiers = new java.util.ArrayList<>(List.of(
                new Tier(5, false, false), new Tier(1, true, false),
                new Tier(3, false, false), new Tier(2, true, false)));
        java.util.Collections.sort(tiers);
        assertEquals(List.of("HT1", "HT2", "LT3", "LT5"),
                tiers.stream().map(Tier::label).toList());
    }

    @Test
    void activeBeatsRetiredAtEqualRank() {
        Tier active = new Tier(2, true, false);
        Tier retired = new Tier(2, true, true);
        assertTrue(active.compareTo(retired) < 0);
    }

    @Test
    void labelPrefixesRetiredWithR() {
        assertEquals("HT1", new Tier(1, true, false).label());
        assertEquals("LT4", new Tier(4, false, false).label());
        assertEquals("RHT1", new Tier(1, true, true).label());
        assertEquals("RLT5", new Tier(5, false, true).label());
    }

    @Test
    void parseReadsNovaStyleStrings() {
        assertEquals(Optional.of(new Tier(1, true, false)), Tier.parse("HT1"));
        assertEquals(Optional.of(new Tier(5, false, false)), Tier.parse("lt5"));
        assertEquals(Optional.of(new Tier(2, true, true)), Tier.parse("RHT2"));
    }

    @Test
    void parseRejectsUnrankedAndGarbage() {
        assertEquals(Optional.empty(), Tier.parse("-"));
        assertEquals(Optional.empty(), Tier.parse(""));
        assertEquals(Optional.empty(), Tier.parse(null));
        assertEquals(Optional.empty(), Tier.parse("HT6"));
        assertEquals(Optional.empty(), Tier.parse("XT1"));
    }

    @Test
    void constructorRejectsOutOfRangeLevels() {
        assertThrows(IllegalArgumentException.class, () -> new Tier(0, true, false));
        assertThrows(IllegalArgumentException.class, () -> new Tier(6, true, false));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests '*TierTest*'`
Expected: FAIL — compilation error, `Tier` does not exist.

- [ ] **Step 3: Write `Tier.java`**

```java
package com.idangilboa.justtiers.tier;

import java.util.Locale;
import java.util.Optional;

/**
 * A single tier placement. {@code level} is 1-5, {@code high} distinguishes HT from LT.
 * Ordering is by {@link #rank()} ascending, so HT1 sorts first and LT5 last.
 */
public record Tier(int level, boolean high, boolean retired) implements Comparable<Tier> {

    public Tier {
        if (level < 1 || level > 5) {
            throw new IllegalArgumentException("tier level out of range: " + level);
        }
    }

    /** Lower is better. HT1 = 0, LT1 = 1, HT2 = 2, ... HT5 = 8, LT5 = 9. */
    public int rank() {
        return (level - 1) * 2 + (high ? 0 : 1);
    }

    public String label() {
        return (retired ? "R" : "") + (high ? "HT" : "LT") + level;
    }

    /** Parses NovaTiers-style strings: HT1..LT5, optionally R-prefixed. */
    public static Optional<Tier> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        boolean retired = false;
        if (s.startsWith("R")) {
            retired = true;
            s = s.substring(1);
        }
        if (s.length() != 3 || s.charAt(1) != 'T') {
            return Optional.empty();
        }
        char hl = s.charAt(0);
        if (hl != 'H' && hl != 'L') {
            return Optional.empty();
        }
        int level = s.charAt(2) - '0';
        if (level < 1 || level > 5) {
            return Optional.empty();
        }
        return Optional.of(new Tier(level, hl == 'H', retired));
    }

    /** Builds a tier from the MCTiers/SubTiers wire format, where pos 0 means high. */
    public static Tier fromMctiers(int tier, int pos, boolean retired) {
        return new Tier(tier, pos == 0, retired);
    }

    @Override
    public int compareTo(Tier other) {
        int byRank = Integer.compare(rank(), other.rank());
        if (byRank != 0) {
            return byRank;
        }
        return Boolean.compare(retired, other.retired);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests '*TierTest*'`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/idangilboa/justtiers/tier/Tier.java src/test/java/com/idangilboa/justtiers/tier/TierTest.java
git commit -m "feat: add Tier value type with LT5..HT1 ordering"
```

---

### Task 3: Source and Gamemode registry

**Files:**
- Create: `src/main/java/com/idangilboa/justtiers/tier/Source.java`
- Create: `src/main/java/com/idangilboa/justtiers/tier/Gamemode.java`
- Create: `src/main/java/com/idangilboa/justtiers/tier/Gamemodes.java`
- Test: `src/test/java/com/idangilboa/justtiers/tier/GamemodesTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum Source { MCTIERS, SUBTIERS, NOVATIERS }` with `String displayName()`, `String baseUrl()`, `int color()`.
  - `record Gamemode(Source source, String slug, String displayName, char icon)`.
  - `Gamemodes.of(Source)` -> `List<Gamemode>` in display order; `Gamemodes.find(Source, String slug)` -> `Optional<Gamemode>`; `Gamemodes.normaliseNovaKey(String apiKey)` -> `Optional<String>` slug; `Gamemodes.ALL` -> `List<Gamemode>`.

- [ ] **Step 1: Write the failing test**

```java
package com.idangilboa.justtiers.tier;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GamemodesTest {

    @Test
    void eachSiteHasTheExpectedNumberOfGamemodes() {
        assertEquals(8, Gamemodes.of(Source.MCTIERS).size());
        assertEquals(12, Gamemodes.of(Source.SUBTIERS).size());
        assertEquals(12, Gamemodes.of(Source.NOVATIERS).size());
        assertEquals(32, Gamemodes.ALL.size());
    }

    @Test
    void everyIconCodepointIsUnique() {
        Set<Character> icons = new HashSet<>();
        for (Gamemode mode : Gamemodes.ALL) {
            assertTrue(icons.add(mode.icon()),
                    "duplicate icon for " + mode.source() + "/" + mode.slug());
        }
    }

    @Test
    void iconsLiveInThePrivateUseArea() {
        for (Gamemode mode : Gamemodes.ALL) {
            assertTrue(mode.icon() >= '\uE000' && mode.icon() <= '\uF8FF',
                    "icon out of PUA for " + mode.slug());
        }
    }

    @Test
    void slugsAreUniqueWithinASite() {
        for (Source source : Source.values()) {
            Set<String> slugs = new HashSet<>();
            for (Gamemode mode : Gamemodes.of(source)) {
                assertTrue(slugs.add(mode.slug()), "duplicate slug " + mode.slug());
            }
        }
    }

    @Test
    void findLocatesGamemodesBySlug() {
        assertEquals(Optional.of("Vanilla"),
                Gamemodes.find(Source.MCTIERS, "vanilla").map(Gamemode::displayName));
        assertEquals(Optional.of("Netherite OP"),
                Gamemodes.find(Source.MCTIERS, "nethop").map(Gamemode::displayName));
        assertEquals(Optional.of("Diamond SMP"),
                Gamemodes.find(Source.SUBTIERS, "dia_smp").map(Gamemode::displayName));
        assertEquals(Optional.empty(), Gamemodes.find(Source.MCTIERS, "spleef"));
    }

    @Test
    void novaKeysNormaliseToSlugs() {
        assertEquals(Optional.of("spearmace"), Gamemodes.normaliseNovaKey("Spear Mace"));
        assertEquals(Optional.of("elytraspear"), Gamemodes.normaliseNovaKey("Elytra Spear"));
        assertEquals(Optional.of("modernsmp"), Gamemodes.normaliseNovaKey("Modern SMP"));
        assertEquals(Optional.of("diamondop"), Gamemodes.normaliseNovaKey("Diamond OP"));
        assertEquals(Optional.of("diamondcart"), Gamemodes.normaliseNovaKey("Diamond Cart"));
        assertEquals(Optional.of("smp"), Gamemodes.normaliseNovaKey("SMP"));
    }

    @Test
    void novaLegacyAliasesStillResolve() {
        assertEquals(Optional.of("spearmace"), Gamemodes.normaliseNovaKey("mace"));
        assertEquals(Optional.of("elytraspear"), Gamemodes.normaliseNovaKey("spear"));
        assertEquals(Optional.of("elytraspear"), Gamemodes.normaliseNovaKey("elytra_sword"));
        assertEquals(Optional.of("modernsmp"), Gamemodes.normaliseNovaKey("modern"));
        assertEquals(Optional.of("spearmace"), Gamemodes.normaliseNovaKey("spearmacekit"));
    }

    @Test
    void unknownNovaKeysAreRejected() {
        assertEquals(Optional.empty(), Gamemodes.normaliseNovaKey("Quake Pro"));
        assertEquals(Optional.empty(), Gamemodes.normaliseNovaKey(""));
        assertEquals(Optional.empty(), Gamemodes.normaliseNovaKey(null));
    }

    @Test
    void sourcesCarryTheirBrandColours() {
        assertEquals(0xFFFF55, Source.MCTIERS.color());
        assertEquals(0x55FFFF, Source.SUBTIERS.color());
        assertEquals(0xAA55FF, Source.NOVATIERS.color());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests '*GamemodesTest*'`
Expected: FAIL — `Source`, `Gamemode`, `Gamemodes` do not exist.

- [ ] **Step 3: Write `Source.java`**

```java
package com.idangilboa.justtiers.tier;

public enum Source {
    MCTIERS("MCTiers", "https://mctiers.com/api", 0xFFFF55),
    SUBTIERS("SubTiers", "https://subtiers.net/api", 0x55FFFF),
    NOVATIERS("NovaTiers", "https://novatiers.com", 0xAA55FF);

    private final String displayName;
    private final String baseUrl;
    private final int color;

    Source(String displayName, String baseUrl, int color) {
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** Colour applied to tier text originating from this site. */
    public int color() {
        return color;
    }
}
```

- [ ] **Step 4: Write `Gamemode.java`**

```java
package com.idangilboa.justtiers.tier;

/**
 * One gamemode on one site. {@code slug} is the identifier used by that site's API
 * (for NovaTiers, our normalised slug rather than their spaced display name).
 * {@code icon} is a private-use codepoint bound to a bitmap glyph in the font provider.
 */
public record Gamemode(Source source, String slug, String displayName, char icon) {
}
```

- [ ] **Step 5: Write `Gamemodes.java`**

Icon codepoints are assigned in alphabetical slug order within each site. Keep this file and `assets/minecraft/font/default.json` (Task 10) in lockstep.

```java
package com.idangilboa.justtiers.tier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class Gamemodes {

    private static final List<Gamemode> MCTIERS = List.of(
            new Gamemode(Source.MCTIERS, "axe", "Axe", '\uE101'),
            new Gamemode(Source.MCTIERS, "mace", "Mace", '\uE102'),
            new Gamemode(Source.MCTIERS, "nethop", "Netherite OP", '\uE103'),
            new Gamemode(Source.MCTIERS, "pot", "Pot", '\uE104'),
            new Gamemode(Source.MCTIERS, "smp", "SMP", '\uE105'),
            new Gamemode(Source.MCTIERS, "sword", "Sword", '\uE106'),
            new Gamemode(Source.MCTIERS, "uhc", "UHC", '\uE107'),
            new Gamemode(Source.MCTIERS, "vanilla", "Vanilla", '\uE108'));

    private static final List<Gamemode> SUBTIERS = List.of(
            new Gamemode(Source.SUBTIERS, "bed", "Bed", '\uE201'),
            new Gamemode(Source.SUBTIERS, "bow", "Bow", '\uE202'),
            new Gamemode(Source.SUBTIERS, "creeper", "Creeper", '\uE203'),
            new Gamemode(Source.SUBTIERS, "debuff", "DeBuff", '\uE204'),
            new Gamemode(Source.SUBTIERS, "dia_crystal", "Diamond Vanilla", '\uE205'),
            new Gamemode(Source.SUBTIERS, "dia_smp", "Diamond SMP", '\uE206'),
            new Gamemode(Source.SUBTIERS, "elytra", "Elytra", '\uE207'),
            new Gamemode(Source.SUBTIERS, "manhunt", "Manhunt", '\uE208'),
            new Gamemode(Source.SUBTIERS, "minecart", "Minecart", '\uE209'),
            new Gamemode(Source.SUBTIERS, "og_vanilla", "OG Vanilla", '\uE20A'),
            new Gamemode(Source.SUBTIERS, "speed", "Speed", '\uE20B'),
            new Gamemode(Source.SUBTIERS, "trident", "Trident", '\uE20C'));

    private static final List<Gamemode> NOVATIERS = List.of(
            new Gamemode(Source.NOVATIERS, "axe", "Axe", '\uE301'),
            new Gamemode(Source.NOVATIERS, "diamondcart", "Diamond Cart", '\uE302'),
            new Gamemode(Source.NOVATIERS, "diamondop", "Diamond OP", '\uE303'),
            new Gamemode(Source.NOVATIERS, "elytra", "Elytra", '\uE304'),
            new Gamemode(Source.NOVATIERS, "elytraspear", "Elytra Spear", '\uE305'),
            new Gamemode(Source.NOVATIERS, "modernsmp", "Modern SMP", '\uE306'),
            new Gamemode(Source.NOVATIERS, "pufferfish", "Pufferfish", '\uE307'),
            new Gamemode(Source.NOVATIERS, "smp", "SMP", '\uE308'),
            new Gamemode(Source.NOVATIERS, "spearmace", "Spear Mace", '\uE309'),
            new Gamemode(Source.NOVATIERS, "spleef", "Spleef", '\uE30A'),
            new Gamemode(Source.NOVATIERS, "uhc", "UHC", '\uE30B'),
            new Gamemode(Source.NOVATIERS, "vanilla", "Vanilla", '\uE30C'));

    public static final List<Gamemode> ALL =
            java.util.stream.Stream.of(MCTIERS, SUBTIERS, NOVATIERS)
                    .flatMap(List::stream)
                    .toList();

    /**
     * Maps a squashed NovaTiers key to our slug. Mirrors the alias table in
     * novatiers.com/js/script.js so historical key spellings keep resolving.
     */
    private static final Map<String, String> NOVA_ALIASES = buildNovaAliases();

    private static Map<String, String> buildNovaAliases() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("spear", "elytraspear");
        map.put("elytraspear", "elytraspear");
        map.put("elytrasword", "elytraspear");
        map.put("mace", "spearmace");
        map.put("spearmace", "spearmace");
        map.put("spearmacekit", "spearmace");
        map.put("modern", "modernsmp");
        map.put("modernsmp", "modernsmp");
        map.put("smp", "smp");
        map.put("diamondop", "diamondop");
        map.put("diamondcart", "diamondcart");
        map.put("spleef", "spleef");
        map.put("pufferfish", "pufferfish");
        map.put("uhc", "uhc");
        map.put("vanilla", "vanilla");
        map.put("axe", "axe");
        map.put("elytra", "elytra");
        return Map.copyOf(map);
    }

    public static List<Gamemode> of(Source source) {
        return switch (source) {
            case MCTIERS -> MCTIERS;
            case SUBTIERS -> SUBTIERS;
            case NOVATIERS -> NOVATIERS;
        };
    }

    public static Optional<Gamemode> find(Source source, String slug) {
        if (slug == null) {
            return Optional.empty();
        }
        return of(source).stream().filter(m -> m.slug().equals(slug)).findFirst();
    }

    /** "Spear Mace" / "spear_mace" / "SPEARMACE" all collapse to "spearmace". */
    public static Optional<String> normaliseNovaKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        String squashed = apiKey.trim().toLowerCase(Locale.ROOT).replaceAll("[_\\s-]+", "");
        return Optional.ofNullable(NOVA_ALIASES.get(squashed));
    }

    private Gamemodes() {
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew test --tests '*GamemodesTest*'`
Expected: PASS, 9 tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/idangilboa/justtiers/tier/ src/test/java/com/idangilboa/justtiers/tier/GamemodesTest.java
git commit -m "feat: add Source and Gamemode registry for all 32 gamemodes"
```

---

### Task 4: Parse the MCTiers/SubTiers rankings payload

MCTiers and SubTiers serve byte-identical schemas, so one parser covers both.

**Files:**
- Create: `src/main/java/com/idangilboa/justtiers/api/MctiersParser.java`
- Test: `src/test/java/com/idangilboa/justtiers/api/MctiersParserTest.java`

**Interfaces:**
- Consumes: `Tier.fromMctiers(int, int, boolean)` from Task 2.
- Produces: `MctiersParser.parseRankings(String json)` -> `Map<String, Tier>` keyed by the site's gamemode slug. Unparseable entries are skipped, never thrown. A `null`, blank or `"{}"` body yields an empty map.

- [ ] **Step 1: Write the failing test**

The `marlowwwJson` fixture below is a verbatim capture of
`GET https://mctiers.com/api/v2/profile/d219c8ee-d32e-4da2-b22e-0aa69d36c88a/rankings`.

```java
package com.idangilboa.justtiers.api;

import com.idangilboa.justtiers.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MctiersParserTest {

    private static final String MARLOWWW_JSON = """
            {"uhc":{"tier":1,"pos":1,"peak_tier":1,"peak_pos":1,"attained":1784635509,"retired":true},
             "nethop":{"tier":1,"pos":0,"peak_tier":1,"peak_pos":0,"attained":1784635498,"retired":true},
             "vanilla":{"tier":1,"pos":0,"peak_tier":1,"peak_pos":0,"attained":1784635475,"retired":true}}
            """;

    private static final String ACTIVE_JSON = """
            {"sword":{"tier":2,"pos":0,"peak_tier":1,"peak_pos":1,"attained":1784635481,"retired":false},
             "pot":{"tier":5,"pos":1,"peak_tier":null,"peak_pos":null,"attained":1784635494,"retired":false}}
            """;

    @Test
    void posZeroIsHighAndPosOneIsLow() {
        Map<String, Tier> tiers = MctiersParser.parseRankings(MARLOWWW_JSON);
        assertEquals("RHT1", tiers.get("vanilla").label());
        assertEquals("RLT1", tiers.get("uhc").label());
    }

    @Test
    void retiredFlagIsCarriedThrough() {
        Map<String, Tier> tiers = MctiersParser.parseRankings(MARLOWWW_JSON);
        assertTrue(tiers.get("nethop").retired());
        assertFalse(MctiersParser.parseRankings(ACTIVE_JSON).get("sword").retired());
    }

    @Test
    void allGamemodeKeysArePreserved() {
        assertEquals(java.util.Set.of("uhc", "nethop", "vanilla"),
                MctiersParser.parseRankings(MARLOWWW_JSON).keySet());
    }

    @Test
    void peakFieldsAreIgnoredEvenWhenBetterThanCurrent() {
        // sword is currently HT2 with a peak of LT1; the peak must not leak into the result.
        assertEquals("HT2", MctiersParser.parseRankings(ACTIVE_JSON).get("sword").label());
    }

    @Test
    void nullPeaksDoNotBreakParsing() {
        assertEquals("LT5", MctiersParser.parseRankings(ACTIVE_JSON).get("pot").label());
    }

    @Test
    void emptyAndNullBodiesYieldEmptyMaps() {
        assertTrue(MctiersParser.parseRankings(null).isEmpty());
        assertTrue(MctiersParser.parseRankings("").isEmpty());
        assertTrue(MctiersParser.parseRankings("   ").isEmpty());
        assertTrue(MctiersParser.parseRankings("{}").isEmpty());
    }

    @Test
    void malformedBodiesYieldEmptyMapsRatherThanThrowing() {
        assertTrue(MctiersParser.parseRankings("not json").isEmpty());
        assertTrue(MctiersParser.parseRankings("[1,2,3]").isEmpty());
    }

    @Test
    void entriesWithOutOfRangeTiersAreSkippedNotFatal() {
        String json = """
                {"good":{"tier":3,"pos":0,"retired":false},
                 "bad":{"tier":9,"pos":0,"retired":false},
                 "alsoBad":{"tier":"x","pos":0,"retired":false}}
                """;
        Map<String, Tier> tiers = MctiersParser.parseRankings(json);
        assertEquals(java.util.Set.of("good"), tiers.keySet());
        assertEquals("HT3", tiers.get("good").label());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests '*MctiersParserTest*'`
Expected: FAIL — `MctiersParser` does not exist.

- [ ] **Step 3: Write `MctiersParser.java`**

```java
package com.idangilboa.justtiers.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.idangilboa.justtiers.JustTiers;
import com.idangilboa.justtiers.tier.Tier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses the shared MCTiers/SubTiers v2 rankings payload:
 * {@code {"<slug>": {"tier":1-5, "pos":0|1, "retired":bool, ...}}}.
 * {@code pos == 0} means HT. Peak fields are deliberately ignored.
 */
public final class MctiersParser {

    private static final Gson GSON = new Gson();

    public static Map<String, Tier> parseRankings(String json) {
        Map<String, Tier> result = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return result;
        }

        JsonObject root;
        try {
            JsonElement parsed = GSON.fromJson(json, JsonElement.class);
            if (parsed == null || !parsed.isJsonObject()) {
                return result;
            }
            root = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            JustTiers.LOGGER.debug("Ignoring malformed rankings payload", e);
            return result;
        }

        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject ranking = entry.getValue().getAsJsonObject();
            try {
                int tier = ranking.get("tier").getAsInt();
                int pos = ranking.get("pos").getAsInt();
                boolean retired = ranking.has("retired")
                        && !ranking.get("retired").isJsonNull()
                        && ranking.get("retired").getAsBoolean();
                if (tier < 1 || tier > 5 || (pos != 0 && pos != 1)) {
                    continue;
                }
                result.put(entry.getKey(), Tier.fromMctiers(tier, pos, retired));
            } catch (RuntimeException e) {
                // A single bad gamemode entry must never sink the whole profile.
                JustTiers.LOGGER.debug("Skipping unparseable ranking '{}'", entry.getKey(), e);
            }
        }
        return result;
    }

    private MctiersParser() {
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests '*MctiersParserTest*'`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/idangilboa/justtiers/api/MctiersParser.java src/test/java/com/idangilboa/justtiers/api/MctiersParserTest.java
git commit -m "feat: parse MCTiers/SubTiers v2 rankings payload"
```

---

### Task 5: Parse the NovaTiers bulk user list

NovaTiers has no per-player endpoint, so this parses the entire `/users` array into a UUID-keyed index once.

**Files:**
- Create: `src/main/java/com/idangilboa/justtiers/api/NovaParser.java`
- Test: `src/test/java/com/idangilboa/justtiers/api/NovaParserTest.java`

**Interfaces:**
- Consumes: `Tier.parse(String)` (Task 2), `Gamemodes.normaliseNovaKey(String)` (Task 3).
- Produces: `NovaParser.parseUsers(String json)` -> `Map<UUID, Map<String, Tier>>` keyed by player UUID then by our Nova slug; `NovaParser.parseUuid(String raw)` -> `Optional<UUID>` accepting dashed and undashed forms.

- [ ] **Step 1: Write the failing test**

The fixture mirrors the real payload shape, including the spaced display-name keys and the separate `retiredTiers` boolean map.

```java
package com.idangilboa.justtiers.api;

import com.idangilboa.justtiers.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NovaParserTest {

    private static final String USERS_JSON = """
            [
              {"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504","minecraftUsername":"X_SUS",
               "region":"EU",
               "tiers":{"Elytra":"HT4","Modern SMP":"HT3","Spear Mace":"LT3"},
               "peakTiers":{"Elytra":"HT1"},
               "retiredTiers":{"Elytra":false,"Modern SMP":true,"Spear Mace":false}},
              {"minecraftUuid":"dadd05d5-e1a2-41bc-be3e-de5f7d9fffee","minecraftUsername":"sigeonpexign",
               "region":"NA",
               "tiers":{"Vanilla":"HT4","Diamond Cart":"LT5"},
               "peakTiers":{},
               "retiredTiers":{}}
            ]
            """;

    private static final UUID X_SUS = UUID.fromString("4b25be24-97f5-4adf-967d-8d69ef54d504");
    private static final UUID SIGEON = UUID.fromString("dadd05d5-e1a2-41bc-be3e-de5f7d9fffee");

    @Test
    void undashedUuidsAreIndexed() {
        assertTrue(NovaParser.parseUsers(USERS_JSON).containsKey(X_SUS));
    }

    @Test
    void dashedUuidsAreAlsoAccepted() {
        assertTrue(NovaParser.parseUsers(USERS_JSON).containsKey(SIGEON));
    }

    @Test
    void displayNameKeysAreNormalisedToSlugs() {
        Map<String, Tier> tiers = NovaParser.parseUsers(USERS_JSON).get(X_SUS);
        assertEquals(java.util.Set.of("elytra", "modernsmp", "spearmace"), tiers.keySet());
    }

    @Test
    void tierStringsAreParsed() {
        Map<String, Tier> tiers = NovaParser.parseUsers(USERS_JSON).get(X_SUS);
        assertEquals("HT4", tiers.get("elytra").label());
        assertEquals("LT3", tiers.get("spearmace").label());
    }

    @Test
    void retiredMapMarksTiersRetired() {
        Map<String, Tier> tiers = NovaParser.parseUsers(USERS_JSON).get(X_SUS);
        assertTrue(tiers.get("modernsmp").retired());
        assertEquals("RHT3", tiers.get("modernsmp").label());
        assertFalse(tiers.get("elytra").retired());
    }

    @Test
    void peakTiersAreIgnored() {
        // Elytra peaks at HT1 but is currently HT4; the peak must not surface.
        assertEquals("HT4", NovaParser.parseUsers(USERS_JSON).get(X_SUS).get("elytra").label());
    }

    @Test
    void rPrefixedTierStringsAreTreatedAsRetired() {
        String json = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{"Axe":"RHT2"},"retiredTiers":{}}]
                """;
        assertEquals("RHT2", NovaParser.parseUsers(json).get(X_SUS).get("axe").label());
    }

    @Test
    void unknownGamemodeKeysAreDropped() {
        String json = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{"Axe":"HT2","Quake Pro":"HT1"},"retiredTiers":{}}]
                """;
        assertEquals(java.util.Set.of("axe"), NovaParser.parseUsers(json).get(X_SUS).keySet());
    }

    @Test
    void playersWithNoTiersAreOmittedEntirely() {
        String json = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{},"retiredTiers":{}}]
                """;
        assertTrue(NovaParser.parseUsers(json).isEmpty());
    }

    @Test
    void malformedInputYieldsEmptyMap() {
        assertTrue(NovaParser.parseUsers(null).isEmpty());
        assertTrue(NovaParser.parseUsers("").isEmpty());
        assertTrue(NovaParser.parseUsers("not json").isEmpty());
        assertTrue(NovaParser.parseUsers("{\"a\":1}").isEmpty());
    }

    @Test
    void entriesWithBadUuidsAreSkippedNotFatal() {
        String json = """
                [{"minecraftUuid":"zzz","tiers":{"Axe":"HT2"},"retiredTiers":{}},
                 {"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504","tiers":{"Axe":"HT3"},"retiredTiers":{}}]
                """;
        Map<UUID, Map<String, Tier>> users = NovaParser.parseUsers(json);
        assertEquals(1, users.size());
        assertEquals("HT3", users.get(X_SUS).get("axe").label());
    }

    @Test
    void parseUuidAcceptsBothForms() {
        assertEquals(Optional.of(X_SUS), NovaParser.parseUuid("4b25be2497f54adf967d8d69ef54d504"));
        assertEquals(Optional.of(SIGEON), NovaParser.parseUuid("dadd05d5-e1a2-41bc-be3e-de5f7d9fffee"));
        assertEquals(Optional.empty(), NovaParser.parseUuid("zzz"));
        assertEquals(Optional.empty(), NovaParser.parseUuid(null));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests '*NovaParserTest*'`
Expected: FAIL — `NovaParser` does not exist.

- [ ] **Step 3: Write `NovaParser.java`**

```java
package com.idangilboa.justtiers.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.idangilboa.justtiers.JustTiers;
import com.idangilboa.justtiers.tier.Gamemodes;
import com.idangilboa.justtiers.tier.Tier;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Parses the NovaTiers bulk {@code /users} array. Keys in the tier maps are spaced
 * display names ("Spear Mace"), which we normalise to slugs. Retirement comes from
 * the sibling {@code retiredTiers} boolean map, with an {@code R} string prefix
 * accepted as a fallback. Peak tiers are deliberately ignored.
 */
public final class NovaParser {

    private static final Gson GSON = new Gson();

    public static Map<UUID, Map<String, Tier>> parseUsers(String json) {
        Map<UUID, Map<String, Tier>> index = new HashMap<>();
        if (json == null || json.isBlank()) {
            return index;
        }

        JsonArray array;
        try {
            JsonElement parsed = GSON.fromJson(json, JsonElement.class);
            if (parsed == null || !parsed.isJsonArray()) {
                return index;
            }
            array = parsed.getAsJsonArray();
        } catch (RuntimeException e) {
            JustTiers.LOGGER.debug("Ignoring malformed NovaTiers payload", e);
            return index;
        }

        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            try {
                parseUser(element.getAsJsonObject(), index);
            } catch (RuntimeException e) {
                JustTiers.LOGGER.debug("Skipping unparseable NovaTiers user", e);
            }
        }
        return index;
    }

    private static void parseUser(JsonObject user, Map<UUID, Map<String, Tier>> index) {
        if (!user.has("minecraftUuid") || user.get("minecraftUuid").isJsonNull()) {
            return;
        }
        Optional<UUID> uuid = parseUuid(user.get("minecraftUuid").getAsString());
        if (uuid.isEmpty() || !user.has("tiers") || !user.get("tiers").isJsonObject()) {
            return;
        }

        JsonObject retiredMap = user.has("retiredTiers") && user.get("retiredTiers").isJsonObject()
                ? user.getAsJsonObject("retiredTiers")
                : new JsonObject();

        Map<String, Tier> tiers = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : user.getAsJsonObject("tiers").entrySet()) {
            Optional<String> slug = Gamemodes.normaliseNovaKey(entry.getKey());
            if (slug.isEmpty() || entry.getValue().isJsonNull()) {
                continue;
            }
            Optional<Tier> parsed = Tier.parse(entry.getValue().getAsString());
            if (parsed.isEmpty()) {
                continue;
            }
            boolean retiredByMap = retiredMap.has(entry.getKey())
                    && !retiredMap.get(entry.getKey()).isJsonNull()
                    && retiredMap.get(entry.getKey()).getAsBoolean();

            Tier tier = parsed.get();
            if (retiredByMap && !tier.retired()) {
                tier = new Tier(tier.level(), tier.high(), true);
            }
            tiers.put(slug.get(), tier);
        }

        if (!tiers.isEmpty()) {
            index.put(uuid.get(), tiers);
        }
    }

    /** Accepts both the dashed and the 32-character undashed UUID forms. */
    public static Optional<UUID> parseUuid(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.trim();
        try {
            if (s.length() == 32) {
                return Optional.of(new UUID(
                        Long.parseUnsignedLong(s.substring(0, 16), 16),
                        Long.parseUnsignedLong(s.substring(16), 16)));
            }
            return Optional.of(UUID.fromString(s));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private NovaParser() {
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests '*NovaParserTest*'`
Expected: PASS, 12 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/idangilboa/justtiers/api/NovaParser.java src/test/java/com/idangilboa/justtiers/api/NovaParserTest.java
git commit -m "feat: parse NovaTiers bulk user list into a UUID index"
```

---

### Task 6: HTTP tier sources

Three sites, two fetch strategies. The critical behaviour is that **HTTP 404 means "unranked", not "error"** — that is the single most common response you will get.

**Files:**
- Create: `src/main/java/com/idangilboa/justtiers/api/TierSource.java`
- Create: `src/main/java/com/idangilboa/justtiers/api/MctiersLikeSource.java`
- Create: `src/main/java/com/idangilboa/justtiers/api/NovaTiersSource.java`
- Test: `src/test/java/com/idangilboa/justtiers/api/TierSourceTest.java`

**Interfaces:**
- Consumes: `MctiersParser.parseRankings` (Task 4), `NovaParser.parseUsers` (Task 5), `Source` (Task 3).
- Produces:
  - `interface TierSource { Source source(); CompletableFuture<Map<String, Tier>> fetch(UUID uuid); }`
  - `MctiersLikeSource(Source source, HttpClient client, String baseUrl)`.
  - `NovaTiersSource(HttpClient client, String baseUrl)` additionally exposing `CompletableFuture<Void> refresh()` and `int indexedPlayerCount()`.
  - Both constructors take an explicit `baseUrl` so tests can point them at a local stub server.

- [ ] **Step 1: Write the failing test**

`com.sun.net.httpserver.HttpServer` ships with the JDK, so no new dependency is needed.

```java
package com.idangilboa.justtiers.api;

import com.idangilboa.justtiers.tier.Source;
import com.idangilboa.justtiers.tier.Tier;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TierSourceTest {

    private HttpServer server;
    private String baseUrl;
    private final HttpClient client = HttpClient.newHttpClient();
    private final AtomicInteger requestCount = new AtomicInteger();

    private static final UUID PLAYER = UUID.fromString("4b25be24-97f5-4adf-967d-8d69ef54d504");

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void respond(String path, int status, String body) {
        server.createContext(path, exchange -> {
            requestCount.incrementAndGet();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
        });
    }

    // --- MctiersLikeSource ---

    @Test
    void fetchesAndParsesRankings() throws Exception {
        respond("/v2/profile/" + PLAYER + "/rankings", 200,
                "{\"vanilla\":{\"tier\":2,\"pos\":0,\"retired\":false}}");
        Map<String, Tier> tiers =
                new MctiersLikeSource(Source.MCTIERS, client, baseUrl).fetch(PLAYER).get();
        assertEquals("HT2", tiers.get("vanilla").label());
    }

    @Test
    void notFoundMeansUnrankedNotFailure() throws Exception {
        respond("/v2/profile/" + PLAYER + "/rankings", 404, "");
        Map<String, Tier> tiers =
                new MctiersLikeSource(Source.MCTIERS, client, baseUrl).fetch(PLAYER).get();
        assertNotNull(tiers);
        assertTrue(tiers.isEmpty());
    }

    @Test
    void serverErrorsResolveToEmptyRatherThanThrowing() throws Exception {
        respond("/v2/profile/" + PLAYER + "/rankings", 500, "boom");
        assertTrue(new MctiersLikeSource(Source.MCTIERS, client, baseUrl)
                .fetch(PLAYER).get().isEmpty());
    }

    @Test
    void connectionFailuresResolveToEmptyRatherThanThrowing() throws Exception {
        // Nothing is listening on this port path; the future must still complete.
        MctiersLikeSource dead = new MctiersLikeSource(
                Source.MCTIERS, client, "http://127.0.0.1:1");
        assertTrue(dead.fetch(PLAYER).get().isEmpty());
    }

    @Test
    void sourceIdentityIsReported() {
        assertEquals(Source.SUBTIERS,
                new MctiersLikeSource(Source.SUBTIERS, client, baseUrl).source());
    }

    // --- NovaTiersSource ---

    @Test
    void novaIndexesTheBulkListAndServesFromMemory() throws Exception {
        respond("/users", 200, """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{"Axe":"HT3"},"retiredTiers":{}}]
                """);
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl);

        assertEquals("HT3", nova.fetch(PLAYER).get().get("axe").label());
        assertEquals(1, nova.indexedPlayerCount());

        // A second lookup must not hit the network again.
        int before = requestCount.get();
        assertEquals("HT3", nova.fetch(PLAYER).get().get("axe").label());
        assertEquals(before, requestCount.get());
    }

    @Test
    void novaReturnsEmptyForPlayersNotInTheList() throws Exception {
        respond("/users", 200, "[]");
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl);
        assertTrue(nova.fetch(UUID.randomUUID()).get().isEmpty());
    }

    @Test
    void novaRefreshRefetchesTheList() throws Exception {
        respond("/users", 200, """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{"Axe":"HT3"},"retiredTiers":{}}]
                """);
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl);
        nova.fetch(PLAYER).get();
        int before = requestCount.get();

        nova.refresh().get();
        assertTrue(requestCount.get() > before);
    }

    @Test
    void novaSurvivesAFailedFetch() throws Exception {
        respond("/users", 503, "");
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl);
        assertTrue(nova.fetch(PLAYER).get().isEmpty());
        assertEquals(0, nova.indexedPlayerCount());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests '*TierSourceTest*'`
Expected: FAIL — `TierSource`, `MctiersLikeSource`, `NovaTiersSource` do not exist.

- [ ] **Step 3: Write `TierSource.java`**

```java
package com.idangilboa.justtiers.api;

import com.idangilboa.justtiers.tier.Source;
import com.idangilboa.justtiers.tier.Tier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches one player's tiers from one site. Implementations never fail the returned
 * future: unreachable services and unranked players both resolve to an empty map.
 */
public interface TierSource {

    Source source();

    CompletableFuture<Map<String, Tier>> fetch(UUID uuid);
}
```

- [ ] **Step 4: Write `MctiersLikeSource.java`**

```java
package com.idangilboa.justtiers.api;

import com.idangilboa.justtiers.JustTiers;
import com.idangilboa.justtiers.tier.Source;
import com.idangilboa.justtiers.tier.Tier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Serves MCTiers and SubTiers, which expose an identical v2 API. */
public final class MctiersLikeSource implements TierSource {

    private final Source source;
    private final HttpClient client;
    private final String baseUrl;

    public MctiersLikeSource(Source source, HttpClient client, String baseUrl) {
        this.source = source;
        this.client = client;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public Source source() {
        return source;
    }

    @Override
    public CompletableFuture<Map<String, Tier>> fetch(UUID uuid) {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(baseUrl + "/v2/profile/" + uuid + "/rankings"))
                .header("User-Agent", JustTiers.USER_AGENT)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int status = response.statusCode();
                    if (status == 404) {
                        // The site returns 404 for players it has never tested. Not an error.
                        return Map.<String, Tier>of();
                    }
                    if (status != 200) {
                        JustTiers.LOGGER.warn("{} returned HTTP {} for {}", source, status, uuid);
                        return Map.<String, Tier>of();
                    }
                    return MctiersParser.parseRankings(response.body());
                })
                .exceptionally(throwable -> {
                    JustTiers.LOGGER.warn("{} lookup failed for {}: {}",
                            source, uuid, throwable.toString());
                    return Map.of();
                });
    }
}
```

- [ ] **Step 5: Write `NovaTiersSource.java`**

NovaTiers has no per-player route, so the whole list is downloaded once and indexed. Concurrent callers share a single in-flight download.

```java
package com.idangilboa.justtiers.api;

import com.idangilboa.justtiers.JustTiers;
import com.idangilboa.justtiers.tier.Source;
import com.idangilboa.justtiers.tier.Tier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * NovaTiers exposes only a bulk {@code /users} array (~6.5k players, ~1.9 MB), so the
 * entire list is downloaded once and held as a UUID index. Call {@link #refresh()}
 * periodically to pick up new placements.
 */
public final class NovaTiersSource implements TierSource {

    private final HttpClient client;
    private final String baseUrl;

    private volatile CompletableFuture<Map<UUID, Map<String, Tier>>> index;
    private volatile int indexedPlayerCount;

    public NovaTiersSource(HttpClient client, String baseUrl) {
        this.client = client;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public Source source() {
        return Source.NOVATIERS;
    }

    @Override
    public CompletableFuture<Map<String, Tier>> fetch(UUID uuid) {
        return ensureLoaded().thenApply(idx -> idx.getOrDefault(uuid, Map.of()));
    }

    /** Number of players currently indexed. Useful for logging and the refresh command. */
    public int indexedPlayerCount() {
        return indexedPlayerCount;
    }

    private synchronized CompletableFuture<Map<UUID, Map<String, Tier>>> ensureLoaded() {
        if (index == null) {
            index = download();
        }
        return index;
    }

    /** Discards the cached index and downloads it again. */
    public synchronized CompletableFuture<Void> refresh() {
        index = download();
        return index.thenAccept(idx -> { });
    }

    private CompletableFuture<Map<UUID, Map<String, Tier>>> download() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/users"))
                .header("User-Agent", JustTiers.USER_AGENT)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        JustTiers.LOGGER.warn("NovaTiers returned HTTP {}", response.statusCode());
                        return Map.<UUID, Map<String, Tier>>of();
                    }
                    Map<UUID, Map<String, Tier>> parsed = NovaParser.parseUsers(response.body());
                    JustTiers.LOGGER.info("Indexed {} NovaTiers players", parsed.size());
                    return parsed;
                })
                .exceptionally(throwable -> {
                    JustTiers.LOGGER.warn("NovaTiers download failed: {}", throwable.toString());
                    return Map.of();
                })
                .thenApply(idx -> {
                    indexedPlayerCount = idx.size();
                    return idx;
                });
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew test --tests '*TierSourceTest*'`
Expected: PASS, 9 tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/idangilboa/justtiers/api/ src/test/java/com/idangilboa/justtiers/api/TierSourceTest.java
git commit -m "feat: add HTTP tier sources with 404-as-unranked handling"
```

---

### Task 7: Non-blocking tier cache

The nametag renderer runs on the render thread and must never block. This cache answers instantly, and schedules fetches in the background.

**Files:**
- Create: `src/main/java/com/idangilboa/justtiers/cache/TierCache.java`
- Test: `src/test/java/com/idangilboa/justtiers/cache/TierCacheTest.java`

**Interfaces:**
- Consumes: `TierSource` (Task 6).
- Produces:
  - `TierCache(List<TierSource> sources)`.
  - `Optional<Map<String, Tier>> peek(Source source, UUID uuid)` — returns `Optional.empty()` while a fetch is pending (and starts one on first call), or the loaded map (possibly empty, meaning "known unranked").
  - `CompletableFuture<Map<String, Tier>> load(Source source, UUID uuid)` — the awaitable form, used by commands.
  - `void invalidateAll()`.

- [ ] **Step 1: Write the failing test**

```java
package com.idangilboa.justtiers.cache;

import com.idangilboa.justtiers.api.TierSource;
import com.idangilboa.justtiers.tier.Source;
import com.idangilboa.justtiers.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TierCacheTest {

    private static final UUID PLAYER = UUID.randomUUID();

    /** A source we can control precisely, counting calls and completing on demand. */
    private static final class FakeSource implements TierSource {
        private final Source source;
        private final Map<String, Tier> result;
        final AtomicInteger calls = new AtomicInteger();
        CompletableFuture<Map<String, Tier>> pending;

        FakeSource(Source source, Map<String, Tier> result) {
            this.source = source;
            this.result = result;
        }

        @Override public Source source() { return source; }

        @Override public CompletableFuture<Map<String, Tier>> fetch(UUID uuid) {
            calls.incrementAndGet();
            pending = new CompletableFuture<>();
            return pending;
        }

        void complete() { pending.complete(result); }
    }

    @Test
    void peekReturnsEmptyWhilePendingThenTheResult() {
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of("axe", new Tier(2, true, false)));
        TierCache cache = new TierCache(List.of(fake));

        assertEquals(Optional.empty(), cache.peek(Source.MCTIERS, PLAYER));
        assertEquals(1, fake.calls.get());

        fake.complete();
        Optional<Map<String, Tier>> loaded = cache.peek(Source.MCTIERS, PLAYER);
        assertTrue(loaded.isPresent());
        assertEquals("HT2", loaded.get().get("axe").label());
    }

    @Test
    void repeatedPeeksIssueOnlyOneFetch() {
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake));

        cache.peek(Source.MCTIERS, PLAYER);
        cache.peek(Source.MCTIERS, PLAYER);
        cache.peek(Source.MCTIERS, PLAYER);

        assertEquals(1, fake.calls.get(), "in-flight requests must be coalesced");
    }

    @Test
    void unrankedResultsAreCachedAsEmptyNotRefetched() {
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake));

        cache.peek(Source.MCTIERS, PLAYER);
        fake.complete();

        Optional<Map<String, Tier>> loaded = cache.peek(Source.MCTIERS, PLAYER);
        assertTrue(loaded.isPresent(), "a known-unranked player is loaded, not pending");
        assertTrue(loaded.get().isEmpty());

        cache.peek(Source.MCTIERS, PLAYER);
        assertEquals(1, fake.calls.get(), "negative results must not be refetched");
    }

    @Test
    void sourcesAreCachedIndependently() {
        FakeSource mct = new FakeSource(Source.MCTIERS, Map.of("axe", new Tier(1, true, false)));
        FakeSource sub = new FakeSource(Source.SUBTIERS, Map.of("bow", new Tier(3, false, false)));
        TierCache cache = new TierCache(List.of(mct, sub));

        cache.peek(Source.MCTIERS, PLAYER);
        mct.complete();

        assertTrue(cache.peek(Source.MCTIERS, PLAYER).isPresent());
        assertEquals(Optional.empty(), cache.peek(Source.SUBTIERS, PLAYER));
        assertEquals(1, sub.calls.get());
    }

    @Test
    void peekForAnUnconfiguredSourceIsLoadedAndEmpty() {
        TierCache cache = new TierCache(List.of());
        Optional<Map<String, Tier>> result = cache.peek(Source.NOVATIERS, PLAYER);
        assertTrue(result.isPresent());
        assertTrue(result.get().isEmpty());
    }

    @Test
    void invalidateAllForcesARefetch() {
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake));

        cache.peek(Source.MCTIERS, PLAYER);
        fake.complete();
        cache.invalidateAll();
        cache.peek(Source.MCTIERS, PLAYER);

        assertEquals(2, fake.calls.get());
    }

    @Test
    void loadExposesTheAwaitableFuture() throws Exception {
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of("axe", new Tier(4, false, false)));
        TierCache cache = new TierCache(List.of(fake));

        CompletableFuture<Map<String, Tier>> future = cache.load(Source.MCTIERS, PLAYER);
        fake.complete();
        assertEquals("LT4", future.get().get("axe").label());
    }

    @Test
    void aFailedFetchIsNotCachedAndCanBeRetried() {
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake));

        cache.peek(Source.MCTIERS, PLAYER);
        fake.pending.completeExceptionally(new RuntimeException("network down"));

        assertEquals(Optional.empty(), cache.peek(Source.MCTIERS, PLAYER),
                "a failed lookup must not be reported as loaded");
        assertEquals(2, fake.calls.get(), "a failed lookup must be retried");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests '*TierCacheTest*'`
Expected: FAIL — `TierCache` does not exist.

- [ ] **Step 3: Write `TierCache.java`**

```java
package com.idangilboa.justtiers.cache;

import com.idangilboa.justtiers.api.TierSource;
import com.idangilboa.justtiers.tier.Source;
import com.idangilboa.justtiers.tier.Tier;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches per-player tier lookups for every site. {@link #peek} never blocks, so it is
 * safe to call from the render thread; a miss schedules a background fetch and reports
 * "not yet known" until it lands.
 */
public final class TierCache {

    private final Map<Source, TierSource> sources = new EnumMap<>(Source.class);
    private final Map<Source, Map<UUID, CompletableFuture<Map<String, Tier>>>> entries =
            new EnumMap<>(Source.class);

    public TierCache(List<TierSource> sources) {
        for (TierSource source : sources) {
            this.sources.put(source.source(), source);
        }
        for (Source source : Source.values()) {
            this.entries.put(source, new ConcurrentHashMap<>());
        }
    }

    /**
     * @return the player's tiers if already loaded (an empty map means "known unranked"),
     *         or {@link Optional#empty()} if a lookup is still in flight.
     */
    public Optional<Map<String, Tier>> peek(Source source, UUID uuid) {
        if (!sources.containsKey(source)) {
            return Optional.of(Map.of());
        }
        CompletableFuture<Map<String, Tier>> future = load(source, uuid);
        if (!future.isDone()) {
            return Optional.empty();
        }
        if (future.isCompletedExceptionally()) {
            // Drop the failure and start a fresh attempt, rather than caching it forever.
            entries.get(source).remove(uuid, future);
            load(source, uuid);
            return Optional.empty();
        }
        return Optional.ofNullable(future.getNow(null));
    }

    /** Starts (or joins) a lookup and returns its future. */
    public CompletableFuture<Map<String, Tier>> load(Source source, UUID uuid) {
        TierSource tierSource = sources.get(source);
        if (tierSource == null) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return entries.get(source).computeIfAbsent(uuid, tierSource::fetch);
    }

    public void invalidateAll() {
        entries.values().forEach(Map::clear);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests '*TierCacheTest*'`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/idangilboa/justtiers/cache/ src/test/java/com/idangilboa/justtiers/cache/TierCacheTest.java
git commit -m "feat: add non-blocking tier cache with request coalescing"
```

---

### Task 8: Display modes and the tier resolver

This is where your four options become behaviour. Read the rules carefully — they differ per mode.

**Rules:**
- `MCTIERS_ONLY` / `SUBTIERS_ONLY` / `NOVATIERS_ONLY`: look up the selected gamemode on that site. If the player has a tier there, show it. If not, show their **highest tier in any gamemode on that same site**. If they have no tier anywhere on that site, show **nothing**. Never consult the other two sites.
- `ALL`: ignore gamemode selection entirely. For **each** site, take the player's highest tier in any gamemode. Show one entry per site that has a tier, in fixed order MCTiers, SubTiers, NovaTiers. Sites with no tier are omitted.
- "Highest" = lowest `Tier.rank()`. Retired tiers compete normally; on an exact rank tie an active tier wins, then the site's declared gamemode order breaks any remainder.

**Files:**
- Create: `src/main/java/com/idangilboa/justtiers/resolve/DisplayMode.java`
- Create: `src/main/java/com/idangilboa/justtiers/resolve/ResolvedTier.java`
- Create: `src/main/java/com/idangilboa/justtiers/resolve/TierResolver.java`
- Test: `src/test/java/com/idangilboa/justtiers/resolve/TierResolverTest.java`

**Interfaces:**
- Consumes: `Tier`, `Source`, `Gamemode`, `Gamemodes` (Tasks 2-3).
- Produces:
  - `enum DisplayMode { MCTIERS_ONLY, SUBTIERS_ONLY, NOVATIERS_ONLY, ALL }` with `Optional<Source> singleSource()` and `String id()` (the lower-case name used by the config file and command arguments).
  - `record ResolvedTier(Gamemode gamemode, Tier tier)`.
  - `TierResolver.resolve(DisplayMode mode, Map<Source, Map<String, Tier>> tiersBySource, Map<Source, String> selectedGamemodes)` -> `List<ResolvedTier>`, empty when nothing should be shown.
  - `TierResolver.highestOn(Source source, Map<String, Tier> tiers)` -> `Optional<ResolvedTier>`.

- [ ] **Step 1: Write the failing test**

```java
package com.idangilboa.justtiers.resolve;

import com.idangilboa.justtiers.tier.Source;
import com.idangilboa.justtiers.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TierResolverTest {

    private static Tier ht(int level) { return new Tier(level, true, false); }
    private static Tier lt(int level) { return new Tier(level, false, false); }
    private static Tier retiredHt(int level) { return new Tier(level, true, true); }

    private static final Map<Source, String> SELECTED = Map.of(
            Source.MCTIERS, "vanilla",
            Source.SUBTIERS, "bow",
            Source.NOVATIERS, "spleef");

    // --- single-site modes ---

    @Test
    void showsTheSelectedGamemodeWhenThePlayerIsRankedInIt() {
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.MCTIERS_ONLY,
                Map.of(Source.MCTIERS, Map.of("vanilla", ht(2), "axe", ht(1))),
                SELECTED);

        assertEquals(1, result.size());
        assertEquals("vanilla", result.get(0).gamemode().slug());
        assertEquals("HT2", result.get(0).tier().label());
    }

    @Test
    void fallsBackToTheHighestTierOnTheSameSite() {
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.MCTIERS_ONLY,
                Map.of(Source.MCTIERS, Map.of("axe", ht(3), "sword", lt(1), "pot", ht(4))),
                SELECTED);

        assertEquals(1, result.size());
        assertEquals("sword", result.get(0).gamemode().slug());
        assertEquals("LT1", result.get(0).tier().label());
    }

    @Test
    void showsNothingWhenUnrankedOnTheSelectedSite() {
        assertTrue(TierResolver.resolve(
                DisplayMode.MCTIERS_ONLY,
                Map.of(Source.MCTIERS, Map.of()),
                SELECTED).isEmpty());
    }

    @Test
    void singleSiteModeNeverConsultsOtherSites() {
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.MCTIERS_ONLY,
                Map.of(Source.MCTIERS, Map.of(),
                       Source.SUBTIERS, Map.of("bow", ht(1)),
                       Source.NOVATIERS, Map.of("spleef", ht(1))),
                SELECTED);

        assertTrue(result.isEmpty(), "MCTiers-only must not borrow tiers from other sites");
    }

    @Test
    void subtiersAndNovatiersModesBehaveTheSameWay() {
        List<ResolvedTier> sub = TierResolver.resolve(
                DisplayMode.SUBTIERS_ONLY,
                Map.of(Source.SUBTIERS, Map.of("bow", lt(2))),
                SELECTED);
        assertEquals("bow", sub.get(0).gamemode().slug());
        assertEquals(Source.SUBTIERS, sub.get(0).gamemode().source());

        List<ResolvedTier> nova = TierResolver.resolve(
                DisplayMode.NOVATIERS_ONLY,
                Map.of(Source.NOVATIERS, Map.of("axe", ht(5))),
                SELECTED);
        assertEquals("axe", nova.get(0).gamemode().slug(), "falls back to highest on Nova");
        assertEquals(Source.NOVATIERS, nova.get(0).gamemode().source());
    }

    // --- ALL mode ---

    @Test
    void allModeShowsTheBestTierFromEachSiteInFixedOrder() {
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.ALL,
                Map.of(Source.MCTIERS, Map.of("axe", ht(2), "pot", lt(4)),
                       Source.SUBTIERS, Map.of("bow", lt(3)),
                       Source.NOVATIERS, Map.of("spleef", ht(4), "uhc", ht(1))),
                SELECTED);

        assertEquals(3, result.size());
        assertEquals(List.of(Source.MCTIERS, Source.SUBTIERS, Source.NOVATIERS),
                result.stream().map(r -> r.gamemode().source()).toList());
        assertEquals(List.of("HT2", "LT3", "HT1"),
                result.stream().map(r -> r.tier().label()).toList());
        assertEquals(List.of("axe", "bow", "uhc"),
                result.stream().map(r -> r.gamemode().slug()).toList());
    }

    @Test
    void allModeOmitsSitesWithNoTier() {
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.ALL,
                Map.of(Source.MCTIERS, Map.of("axe", ht(2)),
                       Source.SUBTIERS, Map.of(),
                       Source.NOVATIERS, Map.of("uhc", ht(1))),
                SELECTED);

        assertEquals(2, result.size());
        assertEquals(List.of(Source.MCTIERS, Source.NOVATIERS),
                result.stream().map(r -> r.gamemode().source()).toList());
    }

    @Test
    void allModeIgnoresTheSelectedGamemode() {
        // vanilla is selected on MCTiers but axe is higher, so axe must win in ALL mode.
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.ALL,
                Map.of(Source.MCTIERS, Map.of("vanilla", lt(5), "axe", ht(1))),
                SELECTED);

        assertEquals(1, result.size());
        assertEquals("axe", result.get(0).gamemode().slug());
    }

    @Test
    void allModeReturnsEmptyWhenUnrankedEverywhere() {
        assertTrue(TierResolver.resolve(
                DisplayMode.ALL,
                Map.of(Source.MCTIERS, Map.of(), Source.SUBTIERS, Map.of(), Source.NOVATIERS, Map.of()),
                SELECTED).isEmpty());
    }

    // --- highest-tier semantics ---

    @Test
    void retiredTiersCompeteForHighest() {
        // Marlowww's case: every MCTiers mode retired. RHT1 must still beat an active HT3.
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.MCTIERS_ONLY,
                Map.of(Source.MCTIERS, Map.of("vanilla", retiredHt(1), "axe", ht(3))),
                Map.of(Source.MCTIERS, "sword"));

        assertEquals("RHT1", result.get(0).tier().label());
        assertEquals("vanilla", result.get(0).gamemode().slug());
    }

    @Test
    void activeBeatsRetiredAtTheSameRank() {
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.MCTIERS_ONLY,
                Map.of(Source.MCTIERS, Map.of("vanilla", retiredHt(2), "axe", ht(2))),
                Map.of(Source.MCTIERS, "sword"));

        assertEquals("HT2", result.get(0).tier().label());
        assertEquals("axe", result.get(0).gamemode().slug());
    }

    @Test
    void tiesBreakByDeclaredGamemodeOrder() {
        // axe precedes sword in the MCTiers registry, so axe wins an exact tie.
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.MCTIERS_ONLY,
                Map.of(Source.MCTIERS, Map.of("sword", ht(2), "axe", ht(2))),
                Map.of(Source.MCTIERS, "pot"));

        assertEquals("axe", result.get(0).gamemode().slug());
    }

    @Test
    void unknownGamemodeSlugsFromTheApiAreIgnored() {
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.MCTIERS_ONLY,
                Map.of(Source.MCTIERS, Map.of("brand_new_mode", ht(1), "axe", ht(4))),
                Map.of(Source.MCTIERS, "vanilla"));

        assertEquals(1, result.size());
        assertEquals("axe", result.get(0).gamemode().slug());
    }

    @Test
    void missingSourceEntriesAreTreatedAsUnranked() {
        assertTrue(TierResolver.resolve(DisplayMode.ALL, Map.of(), SELECTED).isEmpty());
        assertTrue(TierResolver.resolve(DisplayMode.MCTIERS_ONLY, Map.of(), SELECTED).isEmpty());
    }

    @Test
    void singleSourceReportsTheSiteForSingleSiteModes() {
        assertEquals(java.util.Optional.of(Source.MCTIERS), DisplayMode.MCTIERS_ONLY.singleSource());
        assertEquals(java.util.Optional.of(Source.SUBTIERS), DisplayMode.SUBTIERS_ONLY.singleSource());
        assertEquals(java.util.Optional.of(Source.NOVATIERS), DisplayMode.NOVATIERS_ONLY.singleSource());
        assertEquals(java.util.Optional.empty(), DisplayMode.ALL.singleSource());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests '*TierResolverTest*'`
Expected: FAIL — `DisplayMode`, `ResolvedTier`, `TierResolver` do not exist.

- [ ] **Step 3: Write `DisplayMode.java`**

```java
package com.idangilboa.justtiers.resolve;

import com.idangilboa.justtiers.tier.Source;

import java.util.Locale;
import java.util.Optional;

public enum DisplayMode {
    MCTIERS_ONLY(Source.MCTIERS),
    SUBTIERS_ONLY(Source.SUBTIERS),
    NOVATIERS_ONLY(Source.NOVATIERS),
    /** Show the best tier from every site side by side. */
    ALL(null);

    private final Source source;

    DisplayMode(Source source) {
        this.source = source;
    }

    /** The single site this mode reads, or empty for {@link #ALL}. */
    public Optional<Source> singleSource() {
        return Optional.ofNullable(source);
    }

    /** Lower-case name used by the config file and command arguments. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
```

- [ ] **Step 4: Write `ResolvedTier.java`**

```java
package com.idangilboa.justtiers.resolve;

import com.idangilboa.justtiers.tier.Gamemode;
import com.idangilboa.justtiers.tier.Tier;

/** One tier to display, together with the gamemode that earned it. */
public record ResolvedTier(Gamemode gamemode, Tier tier) {
}
```

- [ ] **Step 5: Write `TierResolver.java`**

```java
package com.idangilboa.justtiers.resolve;

import com.idangilboa.justtiers.tier.Gamemode;
import com.idangilboa.justtiers.tier.Gamemodes;
import com.idangilboa.justtiers.tier.Source;
import com.idangilboa.justtiers.tier.Tier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns raw per-site tier maps into the list of tiers to render.
 * Pure and Minecraft-free so it can be unit-tested directly.
 */
public final class TierResolver {

    public static List<ResolvedTier> resolve(DisplayMode mode,
                                             Map<Source, Map<String, Tier>> tiersBySource,
                                             Map<Source, String> selectedGamemodes) {
        Optional<Source> single = mode.singleSource();
        if (single.isPresent()) {
            return resolveSingleSite(single.get(), tiersBySource, selectedGamemodes);
        }
        return resolveAll(tiersBySource);
    }

    private static List<ResolvedTier> resolveSingleSite(Source source,
                                                        Map<Source, Map<String, Tier>> tiersBySource,
                                                        Map<Source, String> selectedGamemodes) {
        Map<String, Tier> tiers = tiersBySource.getOrDefault(source, Map.of());
        if (tiers.isEmpty()) {
            return List.of();
        }

        String selectedSlug = selectedGamemodes.get(source);
        Tier selected = selectedSlug == null ? null : tiers.get(selectedSlug);
        if (selected != null) {
            Optional<Gamemode> gamemode = Gamemodes.find(source, selectedSlug);
            if (gamemode.isPresent()) {
                return List.of(new ResolvedTier(gamemode.get(), selected));
            }
        }

        // Not ranked in the selected mode: fall back to their best on this same site.
        return highestOn(source, tiers).map(List::of).orElseGet(List::of);
    }

    private static List<ResolvedTier> resolveAll(Map<Source, Map<String, Tier>> tiersBySource) {
        List<ResolvedTier> result = new ArrayList<>(Source.values().length);
        for (Source source : Source.values()) {
            highestOn(source, tiersBySource.getOrDefault(source, Map.of())).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    /**
     * The player's best tier on one site. Retired tiers compete normally; ties break
     * toward the active tier, then toward the site's declared gamemode order.
     */
    public static Optional<ResolvedTier> highestOn(Source source, Map<String, Tier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return Optional.empty();
        }

        List<Gamemode> order = Gamemodes.of(source);
        List<ResolvedTier> candidates = new ArrayList<>(tiers.size());
        for (Gamemode gamemode : order) {
            Tier tier = tiers.get(gamemode.slug());
            if (tier != null) {
                candidates.add(new ResolvedTier(gamemode, tier));
            }
        }
        // Gamemodes the site added after this build are skipped rather than guessed at.

        return candidates.stream().min(
                Comparator.comparingInt((ResolvedTier r) -> r.tier().rank())
                        .thenComparing(r -> r.tier().retired())
                        .thenComparingInt(r -> order.indexOf(r.gamemode())));
    }

    private TierResolver() {
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew test --tests '*TierResolverTest*'`
Expected: PASS, 15 tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/idangilboa/justtiers/resolve/ src/test/java/com/idangilboa/justtiers/resolve/TierResolverTest.java
git commit -m "feat: add tier resolver implementing the four display modes"
```

---

### Task 9: The nametag model

The text layout lives here, deliberately free of Minecraft types so it can be asserted on directly. Task 12 converts it to a `Component` mechanically.

**Important:** Minecraft multiplies bitmap font glyphs by the text colour, so **icon segments must be pure white** (`0xFFFFFF`) or the artwork will be tinted.

**Files:**
- Create: `src/main/java/com/idangilboa/justtiers/render/model/Segment.java`
- Create: `src/main/java/com/idangilboa/justtiers/render/model/NametagModel.java`
- Test: `src/test/java/com/idangilboa/justtiers/render/model/NametagModelTest.java`

**Interfaces:**
- Consumes: `ResolvedTier` (Task 8), `Source` (Task 3).
- Produces:
  - `record Segment(String text, int color)`.
  - `NametagModel.build(List<ResolvedTier>)` -> `List<Segment>`, empty when there is nothing to show.
  - Constants `NametagModel.BRACKET_COLOR = 0x555555`, `NametagModel.RETIRED_COLOR = 0xFF5555`, `NametagModel.ICON_COLOR = 0xFFFFFF`.
  - `NametagModel.plainText(List<Segment>)` -> `String`, for tests and log output.

- [ ] **Step 1: Write the failing test**

```java
package com.idangilboa.justtiers.render.model;

import com.idangilboa.justtiers.resolve.ResolvedTier;
import com.idangilboa.justtiers.tier.Gamemode;
import com.idangilboa.justtiers.tier.Gamemodes;
import com.idangilboa.justtiers.tier.Source;
import com.idangilboa.justtiers.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NametagModelTest {

    private static Gamemode mode(Source source, String slug) {
        return Gamemodes.find(source, slug).orElseThrow();
    }

    private static ResolvedTier resolved(Source source, String slug, Tier tier) {
        return new ResolvedTier(mode(source, slug), tier);
    }

    @Test
    void emptyInputProducesNoSegments() {
        assertTrue(NametagModel.build(List.of()).isEmpty());
    }

    @Test
    void singleTierIsWrappedInBrackets() {
        List<Segment> segments = NametagModel.build(
                List.of(resolved(Source.MCTIERS, "vanilla", new Tier(2, true, false))));

        assertEquals("[\uE108HT2] ", NametagModel.plainText(segments));
    }

    @Test
    void tierTextTakesTheSiteColour() {
        List<Segment> segments = NametagModel.build(
                List.of(resolved(Source.MCTIERS, "vanilla", new Tier(2, true, false))));

        Segment tier = segments.stream().filter(s -> s.text().equals("HT2")).findFirst().orElseThrow();
        assertEquals(0xFFFF55, tier.color());
    }

    @Test
    void eachSiteUsesItsOwnColour() {
        assertEquals(0xFFFF55, colourOf(Source.MCTIERS, "vanilla"));
        assertEquals(0x55FFFF, colourOf(Source.SUBTIERS, "bow"));
        assertEquals(0xAA55FF, colourOf(Source.NOVATIERS, "spleef"));
    }

    private int colourOf(Source source, String slug) {
        List<Segment> segments = NametagModel.build(
                List.of(resolved(source, slug, new Tier(3, true, false))));
        return segments.stream().filter(s -> s.text().equals("HT3")).findFirst().orElseThrow().color();
    }

    @Test
    void retiredTiersOverrideTheSiteColourWithLightRed() {
        List<Segment> segments = NametagModel.build(
                List.of(resolved(Source.MCTIERS, "vanilla", new Tier(1, true, true))));

        Segment tier = segments.stream().filter(s -> s.text().equals("RHT1")).findFirst().orElseThrow();
        assertEquals(NametagModel.RETIRED_COLOR, tier.color());
        assertEquals("[\uE108RHT1] ", NametagModel.plainText(segments));
    }

    @Test
    void iconSegmentsAreWhiteSoTheArtworkIsNotTinted() {
        List<Segment> segments = NametagModel.build(
                List.of(resolved(Source.MCTIERS, "vanilla", new Tier(2, true, false))));

        Segment icon = segments.stream()
                .filter(s -> s.text().equals("\uE108")).findFirst().orElseThrow();
        assertEquals(0xFFFFFF, icon.color());
    }

    @Test
    void bracketsUseTheBracketColour() {
        List<Segment> segments = NametagModel.build(
                List.of(resolved(Source.MCTIERS, "vanilla", new Tier(2, true, false))));

        assertEquals(NametagModel.BRACKET_COLOR, segments.get(0).color());
        assertEquals("[", segments.get(0).text());
        assertEquals(NametagModel.BRACKET_COLOR, segments.get(segments.size() - 1).color());
        assertEquals("] ", segments.get(segments.size() - 1).text());
    }

    @Test
    void multipleEntriesAreSeparatedBySingleSpaces() {
        List<Segment> segments = NametagModel.build(List.of(
                resolved(Source.MCTIERS, "axe", new Tier(2, true, false)),
                resolved(Source.SUBTIERS, "bow", new Tier(3, false, false)),
                resolved(Source.NOVATIERS, "uhc", new Tier(4, true, false))));

        assertEquals("[\uE101HT2 \uE202LT3 \uE30BHT4] ", NametagModel.plainText(segments));
    }

    @Test
    void mixedActiveAndRetiredEntriesKeepIndependentColours() {
        List<Segment> segments = NametagModel.build(List.of(
                resolved(Source.MCTIERS, "axe", new Tier(1, true, true)),
                resolved(Source.NOVATIERS, "uhc", new Tier(4, true, false))));

        Segment retired = segments.stream().filter(s -> s.text().equals("RHT1")).findFirst().orElseThrow();
        Segment active = segments.stream().filter(s -> s.text().equals("HT4")).findFirst().orElseThrow();

        assertEquals(NametagModel.RETIRED_COLOR, retired.color());
        assertEquals(Source.NOVATIERS.color(), active.color());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests '*NametagModelTest*'`
Expected: FAIL — `Segment` and `NametagModel` do not exist.

- [ ] **Step 3: Write `Segment.java`**

```java
package com.idangilboa.justtiers.render.model;

/** A run of nametag text with a single colour. Deliberately Minecraft-free. */
public record Segment(String text, int color) {
}
```

- [ ] **Step 4: Write `NametagModel.java`**

```java
package com.idangilboa.justtiers.render.model;

import com.idangilboa.justtiers.resolve.ResolvedTier;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out the tier prefix that goes in front of a player's name, as
 * {@code [<icon>HT2 <icon>LT3] }. Tier text is coloured by its source site, except
 * retired tiers which are light red and carry an {@code R} prefix.
 */
public final class NametagModel {

    public static final int BRACKET_COLOR = 0x555555;
    public static final int RETIRED_COLOR = 0xFF5555;
    /** Bitmap glyphs are multiplied by the text colour, so icons must be white. */
    public static final int ICON_COLOR = 0xFFFFFF;

    public static List<Segment> build(List<ResolvedTier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return List.of();
        }

        List<Segment> segments = new ArrayList<>(tiers.size() * 3 + 2);
        segments.add(new Segment("[", BRACKET_COLOR));

        for (int i = 0; i < tiers.size(); i++) {
            if (i > 0) {
                segments.add(new Segment(" ", BRACKET_COLOR));
            }
            ResolvedTier resolved = tiers.get(i);
            segments.add(new Segment(String.valueOf(resolved.gamemode().icon()), ICON_COLOR));
            int color = resolved.tier().retired()
                    ? RETIRED_COLOR
                    : resolved.gamemode().source().color();
            segments.add(new Segment(resolved.tier().label(), color));
        }

        segments.add(new Segment("] ", BRACKET_COLOR));
        return List.copyOf(segments);
    }

    /** Concatenated text, ignoring colour. Used by tests and debug logging. */
    public static String plainText(List<Segment> segments) {
        StringBuilder builder = new StringBuilder();
        for (Segment segment : segments) {
            builder.append(segment.text());
        }
        return builder.toString();
    }

    private NametagModel() {
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew test --tests '*NametagModelTest*'`
Expected: PASS, 9 tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/idangilboa/justtiers/render/model/ src/test/java/com/idangilboa/justtiers/render/model/NametagModelTest.java
git commit -m "feat: add Minecraft-free nametag layout model"
```

---

### Task 10: Configuration

**Files:**
- Create: `src/main/java/com/idangilboa/justtiers/config/JustTiersConfig.java`
- Test: `src/test/java/com/idangilboa/justtiers/config/JustTiersConfigTest.java`

**Interfaces:**
- Consumes: `DisplayMode` (Task 8), `Source`, `Gamemodes` (Task 3).
- Produces: `JustTiersConfig` with `boolean enabled`, `DisplayMode displayMode`, `Map<String, String> selectedGamemodes` (keyed by `Source.name()`), `int novaRefreshMinutes`; plus `selectedGamemode(Source)`, `setSelectedGamemode(Source, String)`, `selectedGamemodesBySource()` -> `Map<Source, String>`, `static JustTiersConfig load(Path)`, `void save(Path)`.
- Defaults: enabled `true`, mode `ALL`, MCTiers `vanilla`, SubTiers `elytra`, NovaTiers `vanilla`, `novaRefreshMinutes` 30.

- [ ] **Step 1: Write the failing test**

```java
package com.idangilboa.justtiers.config;

import com.idangilboa.justtiers.resolve.DisplayMode;
import com.idangilboa.justtiers.tier.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JustTiersConfigTest {

    @Test
    void defaultsAreSensible() {
        JustTiersConfig config = new JustTiersConfig();
        assertTrue(config.isEnabled());
        assertEquals(DisplayMode.ALL, config.getDisplayMode());
        assertEquals("vanilla", config.selectedGamemode(Source.MCTIERS));
        assertEquals("elytra", config.selectedGamemode(Source.SUBTIERS));
        assertEquals("vanilla", config.selectedGamemode(Source.NOVATIERS));
        assertEquals(30, config.getNovaRefreshMinutes());
    }

    @Test
    void selectedGamemodesAreExposedBySource() {
        JustTiersConfig config = new JustTiersConfig();
        config.setSelectedGamemode(Source.MCTIERS, "axe");
        assertEquals("axe", config.selectedGamemodesBySource().get(Source.MCTIERS));
    }

    @Test
    void roundTripsThroughDisk(@TempDir Path dir) {
        Path file = dir.resolve("justtiers.json");
        JustTiersConfig config = new JustTiersConfig();
        config.setDisplayMode(DisplayMode.SUBTIERS_ONLY);
        config.setSelectedGamemode(Source.SUBTIERS, "trident");
        config.setEnabled(false);
        config.save(file);

        JustTiersConfig loaded = JustTiersConfig.load(file);
        assertEquals(DisplayMode.SUBTIERS_ONLY, loaded.getDisplayMode());
        assertEquals("trident", loaded.selectedGamemode(Source.SUBTIERS));
        assertFalse(loaded.isEnabled());
    }

    @Test
    void loadingAMissingFileYieldsDefaults(@TempDir Path dir) {
        JustTiersConfig loaded = JustTiersConfig.load(dir.resolve("absent.json"));
        assertEquals(DisplayMode.ALL, loaded.getDisplayMode());
        assertTrue(loaded.isEnabled());
    }

    @Test
    void loadingCorruptJsonYieldsDefaults(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("bad.json");
        Files.writeString(file, "{ this is not json");
        assertEquals(DisplayMode.ALL, JustTiersConfig.load(file).getDisplayMode());
    }

    @Test
    void unknownGamemodeSlugsFallBackToTheSiteDefault(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("stale.json");
        Files.writeString(file, """
                {"enabled":true,"displayMode":"MCTIERS_ONLY",
                 "selectedGamemodes":{"MCTIERS":"mode_that_no_longer_exists"},
                 "novaRefreshMinutes":30}
                """);
        assertEquals("vanilla", JustTiersConfig.load(file).selectedGamemode(Source.MCTIERS));
    }

    @Test
    void refreshIntervalIsClampedToASaneRange() {
        JustTiersConfig config = new JustTiersConfig();
        config.setNovaRefreshMinutes(0);
        assertEquals(5, config.getNovaRefreshMinutes());
        config.setNovaRefreshMinutes(100_000);
        assertEquals(1440, config.getNovaRefreshMinutes());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests '*JustTiersConfigTest*'`
Expected: FAIL — `JustTiersConfig` does not exist.

- [ ] **Step 3: Write `JustTiersConfig.java`**

```java
package com.idangilboa.justtiers.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.idangilboa.justtiers.JustTiers;
import com.idangilboa.justtiers.resolve.DisplayMode;
import com.idangilboa.justtiers.tier.Gamemodes;
import com.idangilboa.justtiers.tier.Source;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class JustTiersConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<Source, String> DEFAULT_GAMEMODES = Map.of(
            Source.MCTIERS, "vanilla",
            Source.SUBTIERS, "elytra",
            Source.NOVATIERS, "vanilla");

    private boolean enabled = true;
    private DisplayMode displayMode = DisplayMode.ALL;
    private Map<String, String> selectedGamemodes = new HashMap<>();
    private int novaRefreshMinutes = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public DisplayMode getDisplayMode() {
        return displayMode == null ? DisplayMode.ALL : displayMode;
    }

    public void setDisplayMode(DisplayMode displayMode) {
        this.displayMode = displayMode;
    }

    public int getNovaRefreshMinutes() {
        return novaRefreshMinutes;
    }

    public void setNovaRefreshMinutes(int minutes) {
        this.novaRefreshMinutes = Math.clamp(minutes, 5, 1440);
    }

    /** Falls back to the site default when the stored slug is absent or no longer valid. */
    public String selectedGamemode(Source source) {
        if (selectedGamemodes == null) {
            selectedGamemodes = new HashMap<>();
        }
        String slug = selectedGamemodes.get(source.name());
        if (slug != null && Gamemodes.find(source, slug).isPresent()) {
            return slug;
        }
        return DEFAULT_GAMEMODES.get(source);
    }

    public void setSelectedGamemode(Source source, String slug) {
        if (selectedGamemodes == null) {
            selectedGamemodes = new HashMap<>();
        }
        selectedGamemodes.put(source.name(), slug);
    }

    public Map<Source, String> selectedGamemodesBySource() {
        Map<Source, String> result = new EnumMap<>(Source.class);
        for (Source source : Source.values()) {
            result.put(source, selectedGamemode(source));
        }
        return result;
    }

    public static JustTiersConfig load(Path path) {
        if (!Files.isRegularFile(path)) {
            return new JustTiersConfig();
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            JustTiersConfig config = GSON.fromJson(reader, JustTiersConfig.class);
            return config == null ? new JustTiersConfig() : config;
        } catch (IOException | RuntimeException e) {
            JustTiers.LOGGER.warn("Could not read config at {}, using defaults", path, e);
            return new JustTiersConfig();
        }
    }

    public void save(Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            JustTiers.LOGGER.warn("Could not save config to {}", path, e);
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests '*JustTiersConfigTest*'`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/idangilboa/justtiers/config/ src/test/java/com/idangilboa/justtiers/config/JustTiersConfigTest.java
git commit -m "feat: add persisted configuration"
```

---

### Task 11: Icon assets and the font provider

32 gamemode glyphs. 20 come from TierTagger (MPL-2.0), 12 are generated originals for NovaTiers.

**Licensing:** TierTagger is MPL-2.0, © MCTiers. MPL-2.0 is file-level copyleft, so those 20 PNGs may ship inside this MIT project provided they keep their licence and are attributed. That is what the `NOTICE` file is for. Do not relicense them, and do not copy any other TierTagger file without the same care.

**Files:**
- Create: `tools/gen_nova_icons.py`
- Create: `tools/gen_font_provider.py`
- Create: `NOTICE`
- Create: `src/main/resources/assets/justtiers/textures/mctiers/*.png` (8, downloaded)
- Create: `src/main/resources/assets/justtiers/textures/subtiers/*.png` (12, downloaded)
- Create: `src/main/resources/assets/justtiers/textures/novatiers/*.png` (12, generated)
- Create: `src/main/resources/assets/minecraft/font/default.json` (generated)

**Interfaces:**
- Consumes: the codepoint assignments from `Gamemodes` (Task 3).
- Produces: bitmap glyphs for `U+E101`-`U+E108`, `U+E201`-`U+E20C`, `U+E301`-`U+E30C` merged into the vanilla `default` font.

- [ ] **Step 1: Download the 20 MCTiers and SubTiers icons**

```bash
BASE=https://raw.githubusercontent.com/mctiers-dev/TierTagger/26.2/common/src/main/resources/assets/tiertagger/textures
mkdir -p src/main/resources/assets/justtiers/textures/{mctiers,subtiers,novatiers}

for n in axe mace nethop pot smp sword uhc vanilla; do
  curl -sfL "$BASE/mctiers/$n.png" -o "src/main/resources/assets/justtiers/textures/mctiers/$n.png"
done

for n in bed bow creeper debuff dia_crystal dia_smp elytra manhunt minecart og_vanilla speed trident; do
  curl -sfL "$BASE/subtiers/$n.png" -o "src/main/resources/assets/justtiers/textures/subtiers/$n.png"
done

ls src/main/resources/assets/justtiers/textures/mctiers | wc -l   # expect 8
ls src/main/resources/assets/justtiers/textures/subtiers | wc -l  # expect 12
```

- [ ] **Step 2: Write `tools/gen_nova_icons.py`**

This script is verified to produce valid 8x8 RGBA PNGs using only the standard library.

```python
#!/usr/bin/env python3
"""Generate the 12 original NovaTiers gamemode icons as 8x8 RGBA PNGs."""
import struct, zlib, os, sys

PALETTE = {
    '.': (0, 0, 0, 0),         'k': (40, 40, 45, 255),    'w': (235, 235, 240, 255),
    'g': (150, 155, 165, 255), 'd': (85, 88, 95, 255),    'r': (205, 55, 60, 255),
    'o': (225, 135, 45, 255),  'y': (240, 205, 80, 255),  'l': (110, 190, 85, 255),
    'c': (110, 215, 225, 255), 'b': (70, 120, 220, 255),  'p': (170, 110, 210, 255),
    'n': (140, 95, 55, 255),
}

ICONS = {
    "axe":         ["...ggg..", "..gwwwg.", ".ggwwwg.", ".nnggg..", "..n.....", ".n......", "n.......", "........"],
    "smp":         [".rr..rr.", "rrrrrrrr", "rrrrrrrr", "rrrrrrrr", ".rrrrrr.", "..rrrr..", "...rr...", "........"],
    "vanilla":     ["......cc", ".....cc.", "....cc..", "...cc...", ".n.c....", "..n.....", ".n......", "........"],
    "uhc":         ["...l....", "..lyy...", ".yyyyyy.", "yyyyyyyy", "yyyyyyyy", "yyyyyyyy", ".yyyyyy.", "..yyyy.."],
    "elytra":      ["gg....gg", "ggg..ggg", "gggggggg", "gg.gg.gg", "g..gg..g", "...gg...", "...gg...", "........"],
    "elytraspear": ["gg....c.", "ggg..c..", "ggggc...", "gg.c....", "g.c.....", "..c.....", ".c......", "........"],
    "spearmace":   ["....kkk.", "...kwwwk", "...kwwwk", "....kkk.", "..n.....", ".n......", "n.......", "........"],
    "modernsmp":   ["kkk..kkk", "kkkkkkkk", "kkkwwkkk", "kkkwwkkk", "kkkkkkkk", ".kkkkkk.", ".kkkkkk.", "........"],
    "diamondop":   ["ccc..ccc", "cccccccc", "cccwwccc", "cccwwccc", "cccccccc", ".cccccc.", ".cccccc.", "........"],
    "diamondcart": ["........", "g......g", "g......g", "gg....gg", "gggggggg", "gggggggg", ".k....k.", ".k....k."],
    "spleef":      [".....ggg", ".....ggg", "....gg..", "...n....", "..n.....", ".n......", "n.......", "........"],
    "pufferfish":  ["..y..y..", ".yyyyyy.", "yyoyyoyy", "yyyyyyyy", "yyyyyyyy", ".yyyyyy.", "..y..y..", "........"],
}


def chunk(tag, data):
    return (struct.pack(">I", len(data)) + tag + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff))


def write_png(path, rows):
    height, width = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + b"".join(bytes(PALETTE[ch]) for ch in row) for row in rows)
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as handle:
        handle.write(png)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/justtiers/textures/novatiers"
    os.makedirs(out, exist_ok=True)
    for name, rows in ICONS.items():
        assert len(rows) == 8, f"{name}: expected 8 rows, got {len(rows)}"
        for i, row in enumerate(rows):
            assert len(row) == 8, f"{name} row {i}: expected 8 columns, got {len(row)}"
        write_png(os.path.join(out, name + ".png"), rows)
    print(f"wrote {len(ICONS)} icons to {out}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 3: Generate the NovaTiers icons**

Run: `python3 tools/gen_nova_icons.py`
Expected: `wrote 12 icons to src/main/resources/assets/justtiers/textures/novatiers`

- [ ] **Step 4: Write `tools/gen_font_provider.py`**

Generating this keeps the codepoints in lockstep with `Gamemodes.java` instead of hand-maintaining 32 near-identical JSON blocks.

```python
#!/usr/bin/env python3
"""Generate assets/minecraft/font/default.json binding gamemode icons to codepoints.

Codepoints must match Gamemodes.java exactly:
  MCTiers  U+E101..U+E108   SubTiers U+E201..U+E20C   NovaTiers U+E301..U+E30C
each assigned in alphabetical slug order within its site.
"""
import json, os

SITES = {
    "mctiers": (0xE101, ["axe", "mace", "nethop", "pot", "smp", "sword", "uhc", "vanilla"]),
    "subtiers": (0xE201, ["bed", "bow", "creeper", "debuff", "dia_crystal", "dia_smp",
                          "elytra", "manhunt", "minecart", "og_vanilla", "speed", "trident"]),
    "novatiers": (0xE301, ["axe", "diamondcart", "diamondop", "elytra", "elytraspear",
                           "modernsmp", "pufferfish", "smp", "spearmace", "spleef",
                           "uhc", "vanilla"]),
}

OUT = "src/main/resources/assets/minecraft/font/default.json"


def main():
    providers = []
    for site, (start, slugs) in SITES.items():
        for offset, slug in enumerate(slugs):
            providers.append({
                "type": "bitmap",
                "file": f"justtiers:{site}/{slug}.png",
                "ascent": 8,
                "height": 8,
                "chars": [chr(start + offset)],
            })

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as handle:
        json.dump({"providers": providers}, handle, indent=2, ensure_ascii=True)
        handle.write("\n")
    print(f"wrote {len(providers)} providers to {OUT}")


if __name__ == "__main__":
    main()
```

`ensure_ascii=True` matters: it emits `""` escapes rather than raw private-use bytes, which keeps the file safe to open in any editor.

- [ ] **Step 5: Generate the font provider**

Run: `python3 tools/gen_font_provider.py`
Expected: `wrote 32 providers to src/main/resources/assets/minecraft/font/default.json`

Verify the first provider looks like this:

```json
{
  "providers": [
    {
      "type": "bitmap",
      "file": "justtiers:mctiers/axe.png",
      "ascent": 8,
      "height": 8,
      "chars": [""]
    },
```

- [ ] **Step 6: Verify every referenced texture exists**

```bash
python3 - <<'EOF'
import json, os, sys
data = json.load(open("src/main/resources/assets/minecraft/font/default.json"))
missing = []
for provider in data["providers"]:
    ns, path = provider["file"].split(":", 1)
    full = f"src/main/resources/assets/{ns}/textures/{path}"
    if not os.path.isfile(full):
        missing.append(full)
print(f"{len(data['providers'])} providers, {len(missing)} missing")
if missing:
    print("\n".join(missing)); sys.exit(1)
EOF
```

Expected: `32 providers, 0 missing` and exit code 0.

- [ ] **Step 7: Write `NOTICE`**

```
Just-Tiers
Copyright (c) 2026 Idan Gilboa
Licensed under the MIT License (see LICENSE).

------------------------------------------------------------------------
Third-party assets
------------------------------------------------------------------------

The following gamemode icon textures are taken from TierTagger
(https://github.com/mctiers-dev/TierTagger), Copyright (c) 2025 MCTiers,
mctiers.com, and remain licensed under the Mozilla Public License 2.0.
A copy of the MPL 2.0 is available at https://mozilla.org/MPL/2.0/.

  src/main/resources/assets/justtiers/textures/mctiers/*.png
  src/main/resources/assets/justtiers/textures/subtiers/*.png

All other assets, including the NovaTiers gamemode icons under
src/main/resources/assets/justtiers/textures/novatiers/, are original work
licensed under the MIT License.

MCTiers, SubTiers and NovaTiers are the property of their respective owners.
Just-Tiers is an unofficial client and is not affiliated with or endorsed by
any of them.
```

- [ ] **Step 8: Commit**

```bash
git add tools/ NOTICE src/main/resources/assets/
git commit -m "feat: add gamemode icon textures and font provider"
```

---

### Task 12: Render the nametag

The moment the mod does something visible. `JustTiersClient` wires the object graph, `NametagRenderer` converts segments to a `Component`, and `PlayerMixin` prepends it.

**Files:**
- Create: `src/main/java/com/idangilboa/justtiers/render/NametagRenderer.java`
- Create: `src/main/java/com/idangilboa/justtiers/mixin/PlayerMixin.java`
- Modify: `src/main/java/com/idangilboa/justtiers/JustTiersClient.java`
- Modify: `src/main/resources/justtiers.mixins.json`
- Modify: `build.gradle.kts` (add MixinExtras)

**Interfaces:**
- Consumes: `TierCache.peek` (Task 7), `TierResolver.resolve` (Task 8), `NametagModel.build` (Task 9), `JustTiersConfig` (Task 10).
- Produces:
  - `JustTiersClient.cache()`, `JustTiersClient.config()`, `JustTiersClient.novaSource()`, `JustTiersClient.saveConfig()`.
  - `NametagRenderer.decorate(UUID uuid, Component original)` -> `Component`, returning `original` unchanged when there is nothing to show.

- [ ] **Step 1: Add MixinExtras to `build.gradle.kts`**

`@ModifyReturnValue` comes from MixinExtras, which Fabric Loader bundles at runtime but which must be on the compile classpath and run through the annotation processor.

```kotlin
dependencies {
    // ... existing entries ...
    compileOnly("io.github.llamalad7:mixinextras-common:0.5.4")
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.5.4")
}
```

- [ ] **Step 2: Register the mixin in `justtiers.mixins.json`**

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.idangilboa.justtiers.mixin",
  "compatibilityLevel": "JAVA_21",
  "mixins": [],
  "client": ["PlayerMixin"],
  "injectors": { "defaultRequire": 1 }
}
```

- [ ] **Step 3: Write `NametagRenderer.java`**

```java
package com.idangilboa.justtiers.render;

import com.idangilboa.justtiers.JustTiersClient;
import com.idangilboa.justtiers.render.model.NametagModel;
import com.idangilboa.justtiers.render.model.Segment;
import com.idangilboa.justtiers.resolve.DisplayMode;
import com.idangilboa.justtiers.resolve.ResolvedTier;
import com.idangilboa.justtiers.resolve.TierResolver;
import com.idangilboa.justtiers.tier.Source;
import com.idangilboa.justtiers.tier.Tier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Converts resolved tiers into the Component prefix shown in front of a player's name. */
public final class NametagRenderer {

    public static Component decorate(UUID uuid, Component original) {
        var config = JustTiersClient.config();
        if (!config.isEnabled() || uuid == null) {
            return original;
        }
        // Offline-mode and NPC entities use v3 UUIDs and are never in these leaderboards.
        if (uuid.version() != 4) {
            return original;
        }

        DisplayMode mode = config.getDisplayMode();
        Map<Source, Map<String, Tier>> tiersBySource = new EnumMap<>(Source.class);

        for (Source source : sourcesFor(mode)) {
            Optional<Map<String, Tier>> tiers = JustTiersClient.cache().peek(source, uuid);
            if (tiers.isEmpty()) {
                // Still loading. Render the plain name now; the nametag refreshes next frame.
                return original;
            }
            tiersBySource.put(source, tiers.get());
        }

        List<ResolvedTier> resolved =
                TierResolver.resolve(mode, tiersBySource, config.selectedGamemodesBySource());
        List<Segment> segments = NametagModel.build(resolved);
        if (segments.isEmpty()) {
            return original;
        }

        MutableComponent prefix = Component.empty();
        for (Segment segment : segments) {
            prefix.append(Component.literal(segment.text())
                    .withStyle(style -> style.withColor(segment.color())));
        }
        return prefix.append(original);
    }

    private static List<Source> sourcesFor(DisplayMode mode) {
        return mode.singleSource().map(List::of).orElseGet(() -> List.of(Source.values()));
    }

    private NametagRenderer() {
    }
}
```

- [ ] **Step 4: Write `PlayerMixin.java`**

`Player#getDisplayName` is the same hook TierTagger uses on 26.2, so it is known to work on this version. Class names are Mojmap.

```java
package com.idangilboa.justtiers.mixin;

import com.idangilboa.justtiers.render.NametagRenderer;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public class PlayerMixin {

    @ModifyReturnValue(method = "getDisplayName", at = @At("RETURN"))
    private Component justtiers$prependTier(Component original) {
        Player self = (Player) (Object) this;
        return NametagRenderer.decorate(self.getUUID(), original);
    }
}
```

- [ ] **Step 5: Rewrite `JustTiersClient.java` to wire everything together**

```java
package com.idangilboa.justtiers;

import com.idangilboa.justtiers.api.MctiersLikeSource;
import com.idangilboa.justtiers.api.NovaTiersSource;
import com.idangilboa.justtiers.cache.TierCache;
import com.idangilboa.justtiers.config.JustTiersConfig;
import com.idangilboa.justtiers.tier.Source;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class JustTiersClient implements ClientModInitializer {

    private static JustTiersConfig config;
    private static TierCache cache;
    private static NovaTiersSource novaSource;
    private static Path configPath;

    @Override
    public void onInitializeClient() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("justtiers.json");
        config = JustTiersConfig.load(configPath);

        novaSource = new NovaTiersSource(JustTiers.httpClient(), Source.NOVATIERS.baseUrl());
        cache = new TierCache(List.of(
                new MctiersLikeSource(Source.MCTIERS, JustTiers.httpClient(), Source.MCTIERS.baseUrl()),
                new MctiersLikeSource(Source.SUBTIERS, JustTiers.httpClient(), Source.SUBTIERS.baseUrl()),
                novaSource));

        // NovaTiers only offers a bulk list, so warm it once up front and refresh on a timer.
        novaSource.refresh();
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "just-tiers-refresh");
                    thread.setDaemon(true);
                    return thread;
                });
        scheduler.scheduleWithFixedDelay(
                () -> {
                    novaSource.refresh();
                    cache.invalidateAll();
                },
                config.getNovaRefreshMinutes(), config.getNovaRefreshMinutes(), TimeUnit.MINUTES);

        JustTiers.LOGGER.info("Just-Tiers {} ready (mode {})",
                JustTiers.VERSION, config.getDisplayMode());
    }

    public static JustTiersConfig config() {
        return config;
    }

    public static TierCache cache() {
        return cache;
    }

    public static NovaTiersSource novaSource() {
        return novaSource;
    }

    public static void saveConfig() {
        config.save(configPath);
    }
}
```

- [ ] **Step 6: Verify the build and the full test suite**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`, all tests from Tasks 2-10 passing.

- [ ] **Step 7: Verify in game**

Run: `./gradlew runClient`

Then, in the client:
1. Join any server with other players, or a LAN world with a second account.
2. Look at a player known to be ranked. `Marlowww` is a reliable check: he is retired HT1 on every MCTiers mode, so with the default `ALL` mode his nametag should read `[<icon>RHT1 ...]` with `RHT1` in light red.
3. Confirm the gamemode icon renders as artwork rather than a `?` box. If you see a box, the font provider and texture paths disagree — re-run Step 6 of Task 11.
4. Confirm an unranked player's nametag is completely unchanged, with no stray brackets.
5. Check the log for `Indexed <n> NovaTiers players` with n in the low thousands.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: render tiers in player nametags"
```

---

### Task 13: Client commands

Commands rather than a settings GUI: 26.2 moved screen management off `Minecraft` and onto `Gui`, so a custom `Screen` is the most churn-prone surface in this whole mod. Commands are stable, scriptable and need no extra dependency.

**Files:**
- Create: `src/main/java/com/idangilboa/justtiers/command/JustTiersCommands.java`
- Modify: `src/main/java/com/idangilboa/justtiers/JustTiersClient.java` (register the commands)

**Interfaces:**
- Consumes: `JustTiersClient.config()`, `JustTiersClient.cache()`, `JustTiersClient.novaSource()`, `JustTiersClient.saveConfig()` (Task 12); `Gamemodes`, `Source` (Task 3); `DisplayMode` (Task 8).
- Produces: `JustTiersCommands.register()`, called from `onInitializeClient`.

Command surface:

| Command | Effect |
|---|---|
| `/justtiers` | Print current mode, per-site gamemode, and cache state |
| `/justtiers toggle` | Enable or disable the nametag prefix |
| `/justtiers mode <mctiers_only\|subtiers_only\|novatiers_only\|all>` | Set the display mode |
| `/justtiers gamemode <slug>` | Set the selected gamemode for the current single-site mode |
| `/justtiers refresh` | Re-download the NovaTiers list and clear all caches |

- [ ] **Step 1: Write `JustTiersCommands.java`**

```java
package com.idangilboa.justtiers.command;

import com.idangilboa.justtiers.JustTiersClient;
import com.idangilboa.justtiers.resolve.DisplayMode;
import com.idangilboa.justtiers.tier.Gamemode;
import com.idangilboa.justtiers.tier.Gamemodes;
import com.idangilboa.justtiers.tier.Source;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Optional;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class JustTiersCommands {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("justtiers")
                        .executes(JustTiersCommands::status)
                        .then(literal("toggle").executes(JustTiersCommands::toggle))
                        .then(literal("refresh").executes(JustTiersCommands::refresh))
                        .then(literal("mode")
                                .then(argument("mode", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (DisplayMode mode : DisplayMode.values()) {
                                                builder.suggest(mode.id());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(JustTiersCommands::setMode)))
                        .then(literal("gamemode")
                                .then(argument("gamemode", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            currentSource().ifPresent(source -> {
                                                for (Gamemode mode : Gamemodes.of(source)) {
                                                    builder.suggest(mode.slug());
                                                }
                                            });
                                            return builder.buildFuture();
                                        })
                                        .executes(JustTiersCommands::setGamemode)))));
    }

    private static Optional<Source> currentSource() {
        return JustTiersClient.config().getDisplayMode().singleSource();
    }

    private static void reply(CommandContext<FabricClientCommandSource> context,
                              String message, ChatFormatting color) {
        context.getSource().sendFeedback(
                Component.literal("[Just-Tiers] ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(message).withStyle(color)));
    }

    private static int status(CommandContext<FabricClientCommandSource> context) {
        var config = JustTiersClient.config();
        reply(context, "Enabled: " + config.isEnabled(), ChatFormatting.WHITE);
        reply(context, "Mode: " + config.getDisplayMode().id(), ChatFormatting.WHITE);
        for (Source source : Source.values()) {
            String slug = config.selectedGamemode(source);
            String title = Gamemodes.find(source, slug).map(Gamemode::displayName).orElse(slug);
            reply(context, "  " + source.displayName() + " gamemode: " + title,
                    ChatFormatting.WHITE);
        }
        reply(context, "NovaTiers players indexed: "
                + JustTiersClient.novaSource().indexedPlayerCount(), ChatFormatting.WHITE);
        return 1;
    }

    private static int toggle(CommandContext<FabricClientCommandSource> context) {
        var config = JustTiersClient.config();
        config.setEnabled(!config.isEnabled());
        JustTiersClient.saveConfig();
        reply(context, config.isEnabled() ? "Enabled" : "Disabled",
                config.isEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED);
        return 1;
    }

    private static int refresh(CommandContext<FabricClientCommandSource> context) {
        JustTiersClient.cache().invalidateAll();
        JustTiersClient.novaSource().refresh();
        reply(context, "Refreshing tier data...", ChatFormatting.YELLOW);
        return 1;
    }

    private static int setMode(CommandContext<FabricClientCommandSource> context) {
        String raw = StringArgumentType.getString(context, "mode");
        DisplayMode mode;
        try {
            mode = DisplayMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            reply(context, "Unknown mode '" + raw + "'. Valid: mctiers_only, subtiers_only, "
                    + "novatiers_only, all", ChatFormatting.RED);
            return 0;
        }
        JustTiersClient.config().setDisplayMode(mode);
        JustTiersClient.saveConfig();
        reply(context, "Mode set to " + mode.id(), ChatFormatting.GREEN);
        return 1;
    }

    private static int setGamemode(CommandContext<FabricClientCommandSource> context) {
        Optional<Source> source = currentSource();
        if (source.isEmpty()) {
            reply(context, "'all' mode always shows each site's highest tier, so there is no "
                    + "gamemode to pick. Switch mode first.", ChatFormatting.RED);
            return 0;
        }

        String slug = StringArgumentType.getString(context, "gamemode");
        Optional<Gamemode> gamemode = Gamemodes.find(source.get(), slug);
        if (gamemode.isEmpty()) {
            reply(context, "'" + slug + "' is not a " + source.get().displayName()
                    + " gamemode.", ChatFormatting.RED);
            return 0;
        }

        JustTiersClient.config().setSelectedGamemode(source.get(), slug);
        JustTiersClient.saveConfig();
        reply(context, source.get().displayName() + " gamemode set to "
                + gamemode.get().displayName(), ChatFormatting.GREEN);
        return 1;
    }

    private JustTiersCommands() {
    }
}
```

- [ ] **Step 2: Register the commands in `JustTiersClient.onInitializeClient`**

Add the import and one call at the end of the method, immediately before the closing log line:

```java
import com.idangilboa.justtiers.command.JustTiersCommands;
```

```java
        JustTiersCommands.register();

        JustTiers.LOGGER.info("Just-Tiers {} ready (mode {})",
                JustTiers.VERSION, config.getDisplayMode());
```

- [ ] **Step 3: Verify the build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Verify in game**

Run: `./gradlew runClient`, join a world, then:

1. `/justtiers` — prints enabled state, mode, three per-site gamemodes, and a non-zero NovaTiers index count.
2. `/justtiers mode mctiers_only` — confirm nametags switch to a single MCTiers tier.
3. `/justtiers gamemode axe` — tab-completion offers exactly the 8 MCTiers slugs; a ranked player's nametag switches to their Axe tier, and someone unranked in Axe falls back to their highest MCTiers tier.
4. `/justtiers gamemode bed` — rejected, because `bed` is a SubTiers mode.
5. `/justtiers mode all` then `/justtiers gamemode axe` — rejected with the "no gamemode to pick" message.
6. `/justtiers toggle` — nametags return to normal; toggle again to restore.
7. Quit and relaunch — settings persist via `config/justtiers.json`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add /justtiers client commands"
```

---

## Verification Checklist

Run before calling the mod done:

- [ ] `./gradlew build` succeeds with all tests green: 9 test classes, 84 tests (Tier 7, Gamemodes 9, MctiersParser 8, NovaParser 12, TierSource 9, TierCache 8, TierResolver 15, NametagModel 9, JustTiersConfig 7).
- [ ] `python3 tools/gen_font_provider.py` reports 32 providers and the texture-existence check in Task 11 Step 6 reports 0 missing.
- [ ] Every codepoint in `Gamemodes.java` has a matching provider in `assets/minecraft/font/default.json`. These files are generated from the same ordering but are not mechanically linked, so eyeball them together after any gamemode change.
- [ ] In game with `mode=all`, a player ranked on two sites shows exactly two entries, coloured yellow/cyan/purple by site.
- [ ] In game with a single-site mode, an unranked-in-that-gamemode player falls back to their best tier **on that site only**.
- [ ] A player unranked on the selected site shows a completely unmodified nametag.
- [ ] `Marlowww` renders `RHT1` in light red, confirming retired handling.
- [ ] No peak tier ever appears anywhere in the UI.

## Known Constraints and Follow-ups

Deliberately out of scope, recorded so they are not mistaken for oversights:

- **NovaTiers bulk download.** ~1.9 MB per refresh, because the site offers no per-player route. Default refresh is 30 minutes and configurable via `novaRefreshMinutes` in the config file. If NovaTiers ever adds a per-player endpoint, `NovaTiersSource` is the only class that changes.
- **Gamemode lists are compiled in.** MCTiers and SubTiers publish `/v2/mode/list`, so new gamemodes could be discovered at runtime, but icons and codepoints still have to ship with the mod. Unknown slugs returned by the API are ignored rather than rendered without an icon. Adding a gamemode means editing `Gamemodes.java`, `tools/gen_font_provider.py` and adding a texture.
- **No cross-site gamemode merging.** `Vanilla` on MCTiers and `Vanilla` on NovaTiers stay separate entries, per the agreed design. Only five gamemode names overlap between sites at all.
- **Tab list is untouched.** Tiers appear in nametags only, as specified.
- **Rate limiting is unmeasured.** MCTiers/SubTiers publish no documented limit. The cache issues at most one request per player per site per session, which should be modest, but if 429s appear, add backoff in `MctiersLikeSource`.
