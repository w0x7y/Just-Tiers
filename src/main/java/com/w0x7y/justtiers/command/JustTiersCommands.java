package com.w0x7y.justtiers.command;

import com.w0x7y.justtiers.JustTiers;
import com.w0x7y.justtiers.JustTiersClient;
import com.w0x7y.justtiers.api.OnlinePlayers;
import com.w0x7y.justtiers.cache.TierCache;
import com.w0x7y.justtiers.config.JustTiersConfig;
import com.w0x7y.justtiers.config.Palette;
import com.w0x7y.justtiers.debug.DebugReport;
import com.w0x7y.justtiers.debug.DebugSnapshot;
import com.w0x7y.justtiers.debug.SiteDiagnostics;
import com.w0x7y.justtiers.gui.JustTiersKeybinds;
import com.w0x7y.justtiers.gui.PlayerLookupScreen;
import com.w0x7y.justtiers.gui.ScanScreen;
import com.w0x7y.justtiers.render.model.BadgePosition;
import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class JustTiersCommands {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("justtiers")
                        .executes(JustTiersCommands::status)
                        .then(literal("toggle").executes(context -> toggle(context,
                                JustTiersConfig::isEnabled, JustTiersConfig::setEnabled,
                                "justtiers.command.toggle.on", "justtiers.command.toggle.off",
                                ChatFormatting.RED)))
                        .then(literal("retired").executes(context -> toggle(context,
                                JustTiersConfig::isShowRetired, JustTiersConfig::setShowRetired,
                                "justtiers.command.retired.on", "justtiers.command.retired.off",
                                ChatFormatting.YELLOW)))
                        .then(literal("icons").executes(context -> toggle(context,
                                JustTiersConfig::isShowIcons, JustTiersConfig::setShowIcons,
                                "justtiers.command.icons.on", "justtiers.command.icons.off",
                                ChatFormatting.YELLOW)))
                        .then(literal("ownbadge").executes(context -> toggle(context,
                                JustTiersConfig::isHideOwnBadge, JustTiersConfig::setHideOwnBadge,
                                "justtiers.command.ownbadge.on", "justtiers.command.ownbadge.off",
                                ChatFormatting.YELLOW)))
                        .then(literal("brackets").executes(context -> toggle(context,
                                JustTiersConfig::isShowBrackets, JustTiersConfig::setShowBrackets,
                                "justtiers.command.brackets.on", "justtiers.command.brackets.off",
                                ChatFormatting.YELLOW)))
                        .then(literal("refresh").executes(JustTiersCommands::refresh))
                        .then(literal("gui").executes(JustTiersCommands::openGui))
                        .then(literal("scan").executes(JustTiersCommands::scan))
                        .then(literal("debug").executes(JustTiersCommands::debug))
                        .then(literal("mode")
                                .then(argument("mode", StringArgumentType.word())
                                        .suggests(suggestIds(DisplayMode.values(), DisplayMode::id))
                                        .executes(context -> setEnum(context, "mode",
                                                DisplayMode.values(), DisplayMode::id,
                                                JustTiersConfig::setDisplayMode,
                                                mode -> Component.translatable(
                                                        "justtiers.command.modeSet",
                                                        Component.translatable(
                                                                "justtiers.mode." + mode.id()))))))
                        .then(literal("badge")
                                .then(argument("position", StringArgumentType.word())
                                        .suggests(suggestIds(BadgePosition.values(),
                                                BadgePosition::id))
                                        .executes(context -> setEnum(context, "position",
                                                BadgePosition.values(), BadgePosition::id,
                                                JustTiersConfig::setBadgePosition,
                                                position -> Component.translatable(
                                                        "justtiers.command.badgeSet",
                                                        Component.translatable(
                                                                "justtiers.badge."
                                                                        + position.id()))))))
                        .then(literal("palette")
                                .then(argument("palette", StringArgumentType.word())
                                        .suggests(suggestIds(Palette.values(), Palette::id))
                                        .executes(context -> setEnum(context, "palette",
                                                Palette.values(), Palette::id,
                                                JustTiersConfig::setPalette,
                                                palette -> Component.translatable(
                                                        "justtiers.command.paletteSet",
                                                        Component.translatable(
                                                                palette.displayKey()))))))
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
                              ChatFormatting color, Component message) {
        reply(context.getSource(), message.copy().withStyle(color));
    }

    private static void reply(CommandContext<FabricClientCommandSource> context,
                              ChatFormatting color, String key, Object... args) {
        reply(context, color, Component.translatable(key, args));
    }

    /** The on/off wording the status lines share. */
    private static Component onOff(boolean on) {
        return Component.translatable(on ? "justtiers.command.on" : "justtiers.command.off");
    }

    private static void reply(FabricClientCommandSource source, Component message) {
        source.sendFeedback(Component.literal("[Just-Tiers] ").withStyle(ChatFormatting.GRAY)
                .append(message));
    }

    private static int status(CommandContext<FabricClientCommandSource> context) {
        var config = JustTiersClient.config();
        reply(context, ChatFormatting.WHITE, "justtiers.command.status.enabled",
                onOff(config.isEnabled()));
        reply(context, ChatFormatting.WHITE, "justtiers.command.status.mode",
                Component.translatable("justtiers.mode." + config.getDisplayMode().id()));
        reply(context, ChatFormatting.WHITE, "justtiers.command.status.retired",
                Component.translatable(config.isShowRetired()
                        ? "justtiers.command.status.retired.shown"
                        : "justtiers.command.status.retired.hidden"));
        reply(context, ChatFormatting.WHITE, "justtiers.command.status.badge",
                Component.translatable("justtiers.badge." + config.getBadgePosition().id()),
                onOff(config.isShowIcons()), onOff(config.isShowBrackets()));
        reply(context, ChatFormatting.WHITE, "justtiers.command.status.palette",
                Component.translatable(config.getPalette().displayKey()));
        for (Source source : Source.ALL) {
            String slug = config.selectedGamemode(source);
            String title = Gamemodes.find(source, slug).map(Gamemode::displayName).orElse(slug);
            reply(context, ChatFormatting.WHITE, "justtiers.command.status.gamemode",
                    source.displayName(), title);
        }
        reply(context, ChatFormatting.WHITE, "justtiers.data.indexed",
                JustTiersClient.novaSource().indexedPlayerCount());
        return 1;
    }

    /** Flips a boolean setting, saves it and reports the state it landed in. */
    private static int toggle(CommandContext<FabricClientCommandSource> context,
                              Predicate<JustTiersConfig> get,
                              BiConsumer<JustTiersConfig, Boolean> set,
                              String onKey, String offKey, ChatFormatting offColor) {
        JustTiersConfig config = JustTiersClient.config();
        boolean now = !get.test(config);
        set.accept(config, now);
        JustTiersClient.saveConfig();
        reply(context, now ? ChatFormatting.GREEN : offColor, now ? onKey : offKey);
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
        reply(context, ChatFormatting.YELLOW, "justtiers.command.refreshing");
        return 1;
    }

    /**
     * Looks an enum argument up by its {@code id()} — the spelling the tab suggestions
     * offer. The valid values are read off the enum rather than written out here, so
     * adding a constant cannot leave this message stale.
     */
    /** Tab suggestions for an enum argument: exactly the ids {@link #setEnum} accepts. */
    private static <E> SuggestionProvider<FabricClientCommandSource> suggestIds(
            E[] values, Function<E, String> id) {
        return (context, builder) -> {
            for (E value : values) {
                builder.suggest(id.apply(value));
            }
            return builder.buildFuture();
        };
    }

    /**
     * Applies an enum argument matched by its {@code id()} — the spelling the tab
     * suggestions offer — then saves and reports. The valid values in the rejection are
     * read off the enum rather than written out, so adding a constant cannot leave that
     * message stale.
     */
    private static <E> int setEnum(CommandContext<FabricClientCommandSource> context,
                                   String argument, E[] values, Function<E, String> id,
                                   BiConsumer<JustTiersConfig, E> apply,
                                   Function<E, Component> confirmation) {
        String raw = StringArgumentType.getString(context, argument);
        for (E value : values) {
            if (id.apply(value).equalsIgnoreCase(raw)) {
                apply.accept(JustTiersClient.config(), value);
                JustTiersClient.saveConfig();
                reply(context, ChatFormatting.GREEN, confirmation.apply(value));
                return 1;
            }
        }
        reply(context, ChatFormatting.RED, "justtiers.command.unknown", argument, raw,
                Arrays.stream(values).map(id).collect(Collectors.joining(", ")));
        return 0;
    }

    private static int setGamemode(CommandContext<FabricClientCommandSource> context) {
        Optional<Source> source = currentSource();
        if (source.isEmpty()) {
            // The same sentence the config screen shows on a greyed gamemode row.
            reply(context, ChatFormatting.RED, "justtiers.option.gamemode.inactive");
            return 0;
        }

        String slug = StringArgumentType.getString(context, "gamemode");
        Optional<Gamemode> gamemode = Gamemodes.find(source.get(), slug);
        if (gamemode.isEmpty()) {
            reply(context, ChatFormatting.RED, "justtiers.command.gamemode.unknown",
                    slug, source.get().displayName());
            return 0;
        }

        JustTiersClient.config().setSelectedGamemode(source.get(), slug);
        JustTiersClient.saveConfig();
        reply(context, ChatFormatting.GREEN, "justtiers.command.gamemode.set",
                source.get().displayName(), gamemode.get().displayName());
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

    /**
     * Opens the scan screen. Deferred to the next tick for the same reason a lookup is:
     * the chat screen is still closing, and its setScreen(null) would overwrite this.
     */
    private static int scan(CommandContext<FabricClientCommandSource> context) {
        JustTiersKeybinds.requestOpen(ScanScreen::new);
        return 1;
    }

    /**
     * Prints what every site has actually been doing, and puts the same text on the
     * clipboard. Nearly every bug report this mod will get is "it doesn't show tiers for
     * X", which is a question about the cache, the retry delays and the site gates —
     * none of which are visible from inside the game. This makes answering it one paste.
     *
     * <p>The report lines go out unprefixed: the "[Just-Tiers]" the other replies carry
     * would be repeated down the whole dump, and would come back in every pasted issue.
     */
    private static int debug(CommandContext<FabricClientCommandSource> context) {
        DebugSnapshot snapshot = diagnostics();
        for (String line : DebugReport.lines(snapshot)) {
            context.getSource().sendFeedback(
                    Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(DebugReport.asText(snapshot));
        reply(context, ChatFormatting.GREEN, "justtiers.command.debug.copied");
        return 1;
    }

    /** Reads the report's contents off the live cache and config in one pass. */
    private static DebugSnapshot diagnostics() {
        JustTiersConfig config = JustTiersClient.config();
        TierCache cache = JustTiersClient.cache();
        List<SiteDiagnostics> sites = new ArrayList<>(Source.ALL.size());
        for (Source source : Source.ALL) {
            sites.add(new SiteDiagnostics(source,
                    cache.health(source),
                    cache.gateStatus(source),
                    cache.cachedPlayers(source),
                    cache.pendingLookups(source),
                    cache.playersAwaitingRetry(source)));
        }
        return new DebugSnapshot(
                JustTiers.VERSION,
                modVersion("minecraft"),
                modVersion("fabricloader"),
                config.isEnabled(),
                config.getDisplayMode(),
                Duration.ofMinutes(config.getTierCacheMinutes()),
                JustTiersClient.novaSource().indexedPlayerCount(),
                config.getNovaRefreshMinutes(),
                sites);
    }

    /**
     * A loaded mod's own version string. Falls back rather than throwing: the command
     * whose whole job is to work when something else is broken must not be the thing that
     * breaks.
     */
    private static String modVersion(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private JustTiersCommands() {
    }
}
