import org.gradle.api.tasks.SourceSetContainer

// Keep mechanical Minecraft API renames here, not copies of entire screens.
// Every generated variant is compiled and tested in CI. Edit src/main/java only.
@Suppress("UNCHECKED_CAST")
val target = extra["minecraftTarget"] as Map<String, String>
val generatedJava = layout.buildDirectory.dir("generated/sources/minecraft/java")
val prepareMinecraftSources = tasks.register<Sync>("prepareMinecraftSources") {
    inputs.property("sourceStrategy", target.getValue("source_strategy"))
    inputs.property("screenEvent", target.getValue("screen_event"))
    inputs.file("gradle/compatibility.gradle.kts")
    from("src/main/java")
    into(generatedJava)
    filteringCharset = "UTF-8"
    filesMatching("**/DownloadHud.java") {
        val event = target.getValue("screen_event")
        filter { line: String -> line.replace("ScreenEvents.afterForeground(", "ScreenEvents.$event(") }
    }
    if (target.getValue("source_strategy") == "legacy_render") {
        val renames = mapOf(
            "GuiGraphicsExtractor" to "GuiGraphics",
            "extractRenderState" to "render",
            "extractWidgetRenderState" to "renderWidget",
            "extractContents" to "renderContents",
            "graphics.text(" to "graphics.drawString(",
            "graphics.centeredText(" to "graphics.drawCenteredString(",
            "graphics.outline(" to "graphics.renderOutline(",
            "graphics.horizontalLine(" to "graphics.hLine(",
            "client.command.v2.ClientCommands" to "client.command.v2.ClientCommandManager",
            "client.keymapping.v1.KeyMappingHelper" to "client.keybinding.v1.KeyBindingHelper",
            "KeyMappingHelper.registerKeyMapping" to "KeyBindingHelper.registerKeyBinding"
        )
        filter { line: String -> renames.entries.fold(line) { text, (from, to) -> text.replace(from, to) } }
    }
}
extensions.getByType<SourceSetContainer>().named("main") {
    java.setSrcDirs(listOf(generatedJava))
}
tasks.named("compileJava") { dependsOn(prepareMinecraftSources) }
tasks.named("sourcesJar") { dependsOn(prepareMinecraftSources) }
