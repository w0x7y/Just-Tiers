import java.util.Properties

// Same flat declarations as tools/minecraft_targets.py. Do not infer policy from versions.
val minecraftVersion = property("minecraft_version").toString()
require(Regex("[0-9]+(?:\\.[0-9]+){1,2}").matches(minecraftVersion)) {
    "Invalid Minecraft target: $minecraftVersion"
}
val targetFile = file("gradle/targets/$minecraftVersion.properties")
require(targetFile.isFile) { "Unsupported Minecraft target: $minecraftVersion. See gradle/targets." }
val keys = mutableSetOf<String>()
targetFile.readLines().forEachIndexed { index, raw ->
    val line = raw.trim()
    if (line.isNotEmpty() && !line.startsWith("#")) {
        require(Regex("[a-z_]+=[^\\s\\\\]+").matches(line)) {
            "$targetFile:${index + 1}: expected a plain key=value declaration"
        }
        require(keys.add(line.substringBefore('='))) { "$targetFile: duplicate key on line ${index + 1}" }
    }
}
val target = Properties().apply { targetFile.reader(Charsets.UTF_8).use { load(it) } }
    .entries.associate { it.key.toString() to it.value.toString() }
val required = setOf("java_version", "loader_version", "loader_min_version", "mapping_strategy",
    "source_strategy", "screen_event", "fabric_api_version", "yacl_dependency", "yacl_min_version", "modmenu_dependency")
require(target.keys == required) {
    "$targetFile: missing keys ${required - target.keys}; unknown keys ${target.keys - required}"
}
require((target.getValue("java_version").toIntOrNull() ?: 0) > 0) { "$targetFile: invalid java_version" }
val versions = listOf("loader_version", "loader_min_version", "yacl_min_version").associateWith { key ->
    val value = target.getValue(key)
    require(Regex("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?").matches(value)) { "$targetFile: invalid $key" }
    (value + ".0").split('.').take(3).map(String::toInt)
}
val loaderComparison = versions.getValue("loader_version").zip(versions.getValue("loader_min_version"))
    .map { (tested, minimum) -> tested.compareTo(minimum) }.firstOrNull { it != 0 } ?: 0
require(loaderComparison >= 0) { "$targetFile: tested loader_version is below loader_min_version" }
mapOf(
    "mapping_strategy" to setOf("official", "intermediary"),
    "source_strategy" to setOf("native", "legacy_render"),
    "screen_event" to setOf("afterRender", "afterExtract", "afterForeground")
).forEach { (key, choices) ->
    require(target.getValue(key) in choices) { "$targetFile: unknown $key: ${target.getValue(key)}" }
}
listOf("fabric_api_version", "yacl_dependency", "modmenu_dependency").forEach { key ->
    val value = target.getValue(key)
    val pattern = if (key == "fabric_api_version") "[A-Za-z0-9_.+-]+"
        else "[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.+-]+"
    val version = value.substringAfterLast(':')
    require(Regex(pattern).matches(value) && !version.startsWith("latest.")
            && !version.endsWith('+') && !version.endsWith("-SNAPSHOT")) { "$targetFile: $key must pin a release dependency" }
}
extra["minecraftTarget"] = target
