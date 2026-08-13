plugins {
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
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
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
