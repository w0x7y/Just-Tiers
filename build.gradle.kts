import net.fabricmc.loom.api.LoomGradleExtensionAPI

plugins {
    id("net.fabricmc.fabric-loom") version "1.17.21" apply false
    id("net.fabricmc.fabric-loom-remap") version "1.17.21" apply false
    id("com.modrinth.minotaur") version "2.9.0"
    id("java")
}

val minecraftVersion = property("minecraft_version").toString()
apply(from = "gradle/target-policy.gradle.kts")
@Suppress("UNCHECKED_CAST")
val target = extra["minecraftTarget"] as Map<String, String>
fun targetProperty(name: String) = target.getValue(name)
val remapped = targetProperty("mapping_strategy") == "intermediary"
val javaVersion = targetProperty("java_version").toInt()
val loaderVersion = targetProperty("loader_version")
apply(plugin = if (remapped) "net.fabricmc.fabric-loom-remap" else "net.fabricmc.fabric-loom")
layout.buildDirectory.set(layout.projectDirectory.dir("build/$minecraftVersion"))
version = "${property("mod_version")}+mc$minecraftVersion"
group = property("maven_group")!!

base { archivesName = property("archives_base_name") as String }

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaVersion
}

repositories {
    mavenCentral()
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
    maven("https://maven.fabricmc.net/")
    maven("https://maven.terraformersmc.com/releases/")
    // YACL's org.quiltmc.parsers transitives are not mirrored to Maven Central.
    maven("https://maven.quiltmc.org/repository/release/")
}

dependencies {
    add("minecraft", "com.mojang:minecraft:$minecraftVersion")
    if (remapped) {
        add("mappings", project.extensions.getByType<LoomGradleExtensionAPI>().officialMojangMappings())
    }
    val modImplementation = if (remapped) "modImplementation" else "implementation"
    add(modImplementation, "net.fabricmc:fabric-loader:$loaderVersion")
    add(modImplementation, "net.fabricmc.fabric-api:fabric-api:${targetProperty("fabric_api_version")}")
    add(modImplementation, targetProperty("yacl_dependency"))
    // Only loaded when ModMenu is installed.
    add(if (remapped) "modCompileOnly" else "compileOnly", targetProperty("modmenu_dependency"))
    compileOnly("io.github.llamalad7:mixinextras-common:0.5.4")
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.5.4")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // ResourceContractTest also checks translation keys assembled by the client code.
    inputs.dir("src/main/java")
}

// API renames are applied to a generated copy; the authored source stays on 26.2.
apply(from = "gradle/compatibility.gradle.kts")

// Optional verification tools are separate source sets, never inputs to release JARs.
val benchmark = sourceSets.create("benchmark") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}
val verificationJavaVersion = javaVersion
tasks.register<JavaExec>("badgeBenchmark") {
    group = "verification"
    description = "Measure warmed-cache Badge.forPlayer time and thread allocation"
    classpath = benchmark.runtimeClasspath
    mainClass.set("com.w0x7y.justtiers.benchmark.BadgeBenchmark")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(verificationJavaVersion)) })
    providers.gradleProperty("benchmarkIterations").orNull?.let { args(it) }
}

// Register the auxiliary mod only on request, keeping runClient and packaging ordinary.
if (providers.gradleProperty("clientSmoke").map(String::toBoolean).getOrElse(false)) {
    require(minecraftVersion == "26.2") { "The controlled-world smoke harness currently targets Minecraft 26.2" }
    val smoke = sourceSets.create("smoke") {
        compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
        runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
    }
    val smokeRunDirectory = layout.buildDirectory.dir("smoke-instance").get().asFile
    extensions.getByType<LoomGradleExtensionAPI>().apply {
        mods.create("justtiers-smoke") { sourceSet(smoke) }
        runConfigs.create("smokeClient") {
            client()
            source(smoke)
            runDir(smokeRunDirectory.absolutePath)
            ideConfigGenerated(false)
        }
    }
    tasks.named("runSmokeClient") {
        val report = layout.buildDirectory.file("smoke-instance/justtiers-smoke-report.txt")
        doFirst { report.get().asFile.delete() }
        doLast {
            val evidence = report.get().asFile
            check(evidence.isFile && evidence.readLines().lastOrNull() == "JUSTTIERS_SMOKE_SUCCESS") {
                "Client smoke failed or did not finish; inspect ${evidence.absolutePath} and smoke-instance/logs/latest.log"
            }
        }
    }
}

val resourceProperties = mapOf(
    "version" to project.version.toString(),
    "minecraft" to minecraftVersion,
    "java" to javaVersion.toString(),
    "loader" to targetProperty("loader_min_version"),
    "yacl" to targetProperty("yacl_min_version")
)
tasks.processResources {
    inputs.properties(resourceProperties)
    filesMatching(listOf("fabric.mod.json", "justtiers-version.properties", "justtiers.mixins.json")) {
        expand(resourceProperties)
    }
}

// Publishing. Driven by .github/workflows/release.yml, which runs `./gradlew modrinth`
// with MODRINTH_TOKEN set — Minotaur reads that environment variable itself, so no token
// is ever named here. Configuring this costs an ordinary build nothing: none of it runs
// unless the task is asked for by name, so a contributor without a token is unaffected.
modrinth {
    projectId.set(property("modrinth_id") as String)
    // Include the Minecraft target so each build has a distinct release number.
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

// Loom registers packaging tasks late. The legacy target needs the remapped JAR;
// unobfuscated targets use jar directly. Keep the task provider so publishing builds it.
afterEvaluate {
    val modJar = if (remapped) "remapJar" else "jar"
    modrinth { uploadFile.set(tasks.named(modJar)) }
}
