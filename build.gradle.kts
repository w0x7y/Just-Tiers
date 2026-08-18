plugins {
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
    id("com.modrinth.minotaur") version "2.9.0"
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
    maven("https://maven.terraformersmc.com/releases/")
    // YACL's org.quiltmc.parsers transitives are not mirrored to Maven Central.
    maven("https://maven.quiltmc.org/repository/release/")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    implementation("dev.isxander:yet-another-config-lib:${property("yacl_version")}")
    // Only loaded when ModMenu is installed, so it never becomes a runtime dependency.
    compileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")
    compileOnly("io.github.llamalad7:mixinextras-common:0.5.4")
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.5.4")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching(listOf("fabric.mod.json", "justtiers-version.properties")) {
        expand("version" to project.version)
    }
}

// Publishing. Driven by .github/workflows/release.yml, which runs `./gradlew modrinth`
// with MODRINTH_TOKEN set — Minotaur reads that environment variable itself, so no token
// is ever named here. Configuring this costs an ordinary build nothing: none of it runs
// unless the task is asked for by name, so a contributor without a token is unaffected.
modrinth {
    projectId.set(property("modrinth_id") as String)
    // The full "1.0.2+mc26.2", so two builds of one mod version for different Minecraft
    // versions do not collide — Modrinth rejects a version number it already has.
    versionNumber.set(project.version as String)
    versionName.set("Just-Tiers ${property("mod_version")} for Minecraft "
            + "${property("minecraft_version")}")
    versionType.set(property("release_type") as String)
    gameVersions.add(property("minecraft_version") as String)
    loaders.add("fabric")
    // Written by the release workflow from the commits since the previous tag. The
    // fallback is for a hand-run publish, where a wrong changelog would be worse than a
    // pointer to the one on GitHub.
    changelog.set(providers.environmentVariable("CHANGELOG")
            .orElse("https://github.com/w0x7y/Just-Tiers/releases"))
    // As declared in fabric.mod.json: the mod does not load without the first two.
    // Called straight on the extension rather than inside the `dependencies { }` block
    // the Groovy examples use — ModrinthExtension extends DependencyDSL, which has no
    // such method, so in the Kotlin DSL that block would resolve to Gradle's own
    // `dependencies { }` and configure the wrong thing.
    required.project("fabric-api")
    required.project("yacl")
    optional.project("modmenu")
    // `./gradlew modrinth -Pmodrinth_dry_run=true` still authenticates, still resolves
    // the project, and still assembles the whole payload — then prints it and stops
    // without creating a version. That is the only way to find out whether publishing is
    // wired up correctly without publishing something, which matters most before the
    // first real release.
    debugMode.set(providers.gradleProperty("modrinth_dry_run")
            .map(String::toBoolean).orElse(false))
    // The Modrinth listing is written for Modrinth rather than for GitHub, so it lives in
    // its own file. Deliberately not wired into the release job: `modrinthSyncBody`
    // overwrites the project body, and that cannot be undone.
    syncBodyFrom.set(rootProject.file("Modrinth/description.md").readText())
}

// The jar to upload. Minecraft 26.2 ships unobfuscated, so Loom has nothing to remap and
// registers no `remapJar` task — `jar` is the distributable mod jar, not a dev-only one.
// An obfuscated target would bring `remapJar` back, and uploading `jar` there would ship
// something that only runs in a development workspace, so pick whichever exists rather
// than hardcoding today's answer. This sits in afterEvaluate because Loom registers its
// tasks late; keeping it a TaskProvider is what makes `modrinth` build the jar it uploads.
afterEvaluate {
    val modJar = if (tasks.findByName("remapJar") != null) "remapJar" else "jar"
    modrinth { uploadFile.set(tasks.named(modJar)) }
}
