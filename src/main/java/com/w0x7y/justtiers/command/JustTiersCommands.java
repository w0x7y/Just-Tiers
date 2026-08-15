package com.w0x7y.justtiers.command;

import com.w0x7y.justtiers.JustTiersClient;
import com.w0x7y.justtiers.api.OnlinePlayers;
import com.w0x7y.justtiers.gui.JustTiersKeybinds;
import com.w0x7y.justtiers.gui.PlayerLookupScreen;
import com.w0x7y.justtiers.render.model.BadgePosition;
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
                        .then(literal("icons").executes(JustTiersCommands::toggleIcons))
                        .then(literal("brackets").executes(JustTiersCommands::toggleBrackets))
                        .then(literal("refresh").executes(JustTiersCommands::refresh))
                        .then(literal("gui").executes(JustTiersCommands::openGui))
                        .then(literal("mode")
                                .then(argument("mode", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (DisplayMode mode : DisplayMode.values()) {
                                                builder.suggest(mode.id());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(JustTiersCommands::setMode)))
                        .then(literal("badge")
                                .then(argument("position", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (BadgePosition position : BadgePosition.values()) {
                                                builder.suggest(position.id());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(JustTiersCommands::setBadgePosition)))
                        .then(literal("lookup")
                                .then(argument("player", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            String prefix = builder.getRemaining()
                                                    .toLowerCase(Locale.ROOT);
                                            for (String name : OnlinePlayers.names()) {
                                                if (name.toLowerCase(Locale.ROOT)
                                                        .startsWith(prefix)) {
                                                    builder.suggest(name);
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(JustTiersCommands::lookup)))
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
        reply(context.getSource(), Component.literal(message).withStyle(color));
    }

    private static void reply(FabricClientCommandSource source, Component message) {
        source.sendFeedback(Component.literal("[Just-Tiers] ").withStyle(ChatFormatting.GRAY)
                .append(message));
    }

    private static int status(CommandContext<FabricClientCommandSource> context) {
        var config = JustTiersClient.config();
        reply(context, "Enabled: " + config.isEnabled(), ChatFormatting.WHITE);
        reply(context, "Mode: " + config.getDisplayMode().id(), ChatFormatting.WHITE);
        reply(context, "Retired tiers: " + (config.isShowRetired() ? "shown" : "hidden"),
                ChatFormatting.WHITE);
        reply(context, "Badge: " + config.getBadgePosition().id() + " the name, icons "
                        + (config.isShowIcons() ? "on" : "off") + ", brackets "
                        + (config.isShowBrackets() ? "on" : "off"),
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

    private static int toggleIcons(CommandContext<FabricClientCommandSource> context) {
        var config = JustTiersClient.config();
        config.setShowIcons(!config.isShowIcons());
        JustTiersClient.saveConfig();
        reply(context, config.isShowIcons()
                        ? "Showing gamemode icons"
                        : "Hiding gamemode icons — sites are told apart by colour",
                config.isShowIcons() ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        return 1;
    }

    private static int toggleBrackets(CommandContext<FabricClientCommandSource> context) {
        var config = JustTiersClient.config();
        config.setShowBrackets(!config.isShowBrackets());
        JustTiersClient.saveConfig();
        reply(context, config.isShowBrackets() ? "Showing brackets" : "Hiding brackets",
                config.isShowBrackets() ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        return 1;
    }

    private static int openGui(CommandContext<FabricClientCommandSource> context) {
        // Opened on the next tick: the chat screen is still closing right now, and its
        // setScreen(null) would overwrite anything we opened from here.
        JustTiersKeybinds.requestOpen();
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

    private static int setBadgePosition(CommandContext<FabricClientCommandSource> context) {
        String raw = StringArgumentType.getString(context, "position");
        BadgePosition position;
        try {
            position = BadgePosition.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            reply(context, "Unknown position '" + raw + "'. Valid: before, after",
                    ChatFormatting.RED);
            return 0;
        }
        JustTiersClient.config().setBadgePosition(position);
        JustTiersClient.saveConfig();
        reply(context, "Badge drawn " + position.id() + " the name", ChatFormatting.GREEN);
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

    /**
     * Opens the lookup screen for a name. Everything else — who that name belongs to,
     * what each site says about them, what their skin looks like — happens on the
     * screen, so nothing about a lookup goes to chat any more.
     */
    private static int lookup(CommandContext<FabricClientCommandSource> context) {
        String name = StringArgumentType.getString(context, "player");
        // Opened on the next tick for the same reason /justtiers gui is: the chat screen
        // is still closing right now, and its setScreen(null) would overwrite this.
        JustTiersKeybinds.requestOpen(() -> new PlayerLookupScreen(name));
        return 1;
    }

    private JustTiersCommands() {
    }
}
