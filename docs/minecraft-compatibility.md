# Minecraft compatibility

The project builds one JAR per exact Minecraft version. Select a target with
`-Pminecraft_version=<version>`; the default in `gradle.properties` is 26.2.
`gradle/targets/*.properties` is the target catalog. Adding a valid profile adds that
version to CI, release and dry-run matrices and the release artifact count.

| Target | Java | Packaging |
|---|---|---|
| 1.21.11 | 21 | Mojang mappings, remapped to Fabric intermediary |
| 26.1 | 25 | Unobfuscated |
| 26.1.1 | 25 | Unobfuscated |
| 26.1.2 | 25 | Unobfuscated |
| 26.2 | 25 | Unobfuscated |
| 26.3 | 25 | Unobfuscated |

Use the normal JAR from `build/<version>/libs/`. The 1.21.11 development JAR lives
separately in `devlibs/` and cannot be installed in a normal Fabric instance.
The sources JAR is for developers.

## Target policy

Each profile uses plain UTF-8 `key=value` lines and optional `#` comments. Escapes,
continuation lines and duplicate or unknown keys are rejected. Every profile declares:

| Field | Meaning |
|---|---|
| `java_version` | Compiler release and packaged minimum Java version |
| `loader_version` | Exact Fabric Loader used to build and run development clients |
| `loader_min_version` | Minimum Loader allowed by the packaged mod |
| `mapping_strategy` | `intermediary` remaps the installable JAR; `official` uses unobfuscated names |
| `source_strategy` | `legacy_render` applies the older rendering/helper names; `native` keeps authored names |
| `screen_event` | Fabric screen event for the download overlay |
| `fabric_api_version` | Pinned Fabric API version |
| `yacl_dependency`, `modmenu_dependency` | Pinned Maven coordinates, including version |
| `yacl_min_version` | Minimum YACL allowed by the packaged mod |

The tested Loader must meet the declared minimum. The 26.3 profile preserves its
0.19.5 minimum because older Loader releases bundle an incompatible MixinExtras.
Other targets keep their existing 0.19 minimum and tested Loader 0.19.3.

Gradle's `target-policy.gradle.kts` reads and validates the selected profile before
configuring dependencies. The Python standard-library reader in
`tools/minecraft_targets.py` validates the complete catalog for workflows and artifact
checks. Both consume the declarations directly; neither infers policy from a game
version. Their validation only accepts pinned release dependencies.

Minecraft 26.3 requires Fabric Loader 0.19.5 or newer. Its bundled MixinExtras 0.5.5
is needed by the pinned YACL release. Loader 0.19.3 reproduced a startup
`ClassCastException` in `FactoryRedirectWrapperMixinTransformer`; compilation alone
did not catch it. Other targets retain their existing Loader 0.19 minimum.

## Shared source

Edit `src/main/java`. `prepareMinecraftSources` copies it to the selected target's
generated-source directory, applying the small API rename table in
`gradle/compatibility.gradle.kts` before compilation. The copy task tracks both its
source and the conversion script so changes invalidate Gradle's cached output.

The 1.21.11 build converts GUI extraction names to that version's rendering names,
plus Fabric's older command and key-binding helpers. The title-screen download
overlay uses `afterRender` on 1.21.11, `afterExtract` on 26.1.x and `afterForeground`
on 26.2/26.3. The badge, cache, API, lookup and settings logic remains shared.

`LiveLabelState` keeps configuration labels dynamic on both YACL 3.8 and 3.9.
Keyboard bindings and link confirmation use overloads available on every target.
No runtime reflection or optional mixin injection is used to hide incompatible APIs.

The 26.3 YACL and ModMenu coordinates use pinned Modrinth version IDs because their
releases were available there before Maven Central. The selected YACL release is
3.9.7+26.3-fabric and ModMenu is 21.0.0. Other targets retain ordinary Maven coordinates.

## Verification

Run the full build and inspect the actual installable JAR for each target:

```bash
targets="$(python3 tools/minecraft_targets.py list)" || exit 1
while IFS= read -r version; do
  ./gradlew build -Pminecraft_version="$version" || exit 1
  python3 tools/verify_artifact.py "$version" || exit 1
done <<< "$targets"

python3 -m unittest discover -s tools/tests -v
```

The artifact check verifies the full mod version, packaged dependency constraints, Java class versions and
the nametag mixin's class and method names. This catches an unremapped 1.21.11 JAR even
when the model tests pass. Its bytecode checks inspect the JAR independently of Gradle
and the source conversion. Negative tests feed mismatched metadata, wrong class
versions and incorrect namespaces to the verifier, including under Python's optimized
mode. CI builds every discovered target on Linux and Windows.

For runtime checks, launch `./gradlew runClient -Pminecraft_version=<version>` and
follow [the runtime checklist](runtime-smoke-test.md). Compilation alone cannot prove
screen rendering, mixin application or multiplayer behavior.

The reusable `minecraft-targets.yml` workflow runs policy tests, validates all profiles
and exports the matrix and artifact count. Build, release and dry-run workflows consume
those outputs. The release workflow builds and checks all targets before attaching
one installable JAR per target to GitHub. Separate Modrinth jobs publish each target. After a partial publishing
failure, rerun only the failed jobs so completed versions are not uploaded again.
