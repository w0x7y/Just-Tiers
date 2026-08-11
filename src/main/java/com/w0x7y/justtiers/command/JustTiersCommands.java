package com.w0x7y.justtiers.command;

import com.w0x7y.justtiers.JustTiersClient;
import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Optional;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class JustTiersCommands {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("justtiers")
                        .executes(JustTiersCommands::status)
                        .then(literal("toggle").executes(JustTiersCommands::toggle))
                        .then(literal("retired").executes(JustTiersCommands::toggleRetired))
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
        reply(context, "Retired tiers: " + (config.isShowRetired() ? "shown" : "hidden"),
                ChatFormatting.WHITE);
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

    private static int toggleRetired(CommandContext<FabricClientCommandSource> context) {
        var config = JustTiersClient.config();
        config.setShowRetired(!config.isShowRetired());
        JustTiersClient.saveConfig();
        reply(context, config.isShowRetired()
                        ? "Showing retired tiers"
                        : "Hiding retired tiers",
                config.isShowRetired() ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
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
