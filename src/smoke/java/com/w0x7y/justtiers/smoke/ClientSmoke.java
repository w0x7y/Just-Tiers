package com.w0x7y.justtiers.smoke;

import com.mojang.authlib.GameProfile;
import com.w0x7y.justtiers.JustTiersClient;
import com.w0x7y.justtiers.api.TierSource;
import com.w0x7y.justtiers.cache.TierCache;
import com.w0x7y.justtiers.config.JustTiersConfig;
import com.w0x7y.justtiers.config.Palette;
import com.w0x7y.justtiers.gui.GamemodeGridScreen;
import com.w0x7y.justtiers.gui.JustTiersScreens;
import com.w0x7y.justtiers.gui.PlayerLookupScreen;
import com.w0x7y.justtiers.render.Icons;
import com.w0x7y.justtiers.render.model.BadgePosition;
import com.w0x7y.justtiers.settings.RefreshLifecycle;
import com.w0x7y.justtiers.settings.SettingsApplication;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.scores.PlayerTeam;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Optional auxiliary client mod. All fake state stays in the disposable game directory. */
public final class ClientSmoke implements ClientModInitializer {
    private final List<String> checks = new ArrayList<>();
    private final long deadline = System.nanoTime() + java.time.Duration.ofMinutes(5).toNanos();
    private int stage;
    private int ticks;
    private Path report;

    public void onInitializeClient() {
        report = FabricLoader.getInstance().getGameDir().resolve("justtiers-smoke-report.txt");
        try { Files.deleteIfExists(report); } catch (Exception error) { throw new RuntimeException(error); }
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (stage == 99) return;
        try {
            if (System.nanoTime() > deadline) throw new AssertionError("Client smoke timed out at stage " + stage);
            ticks++;
            if (stage == 0 && client.isGameLoadFinished() && client.gui.overlay() == null && ticks > 40) {
                installControlledState();
                LevelSettings settings = new LevelSettings("Just-Tiers smoke", GameType.CREATIVE,
                        new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true,
                        WorldDataConfiguration.DEFAULT);
                stage = 1;
                client.createWorldOpenFlows().createFreshLevel("justtiers-smoke-" + System.currentTimeMillis(),
                        settings, new WorldOptions(42L, false, false),
                        WorldPresets::createTestWorldDimensions, null);
            } else if (stage == 1 && client.level != null && client.player != null && client.gui.overlay() == null) {
                checkNames(client);
                open(client, JustTiersScreens.create(null), "config");
                stage = 2;
                ticks = 0;
            } else if (stage == 2 && ticks >= 40) {
                open(client, new GamemodeGridScreen(null, Source.PVPTIERS, "crystal",
                        () -> JustTiersClient.config().nametagSettings(), ignored -> {}), "gamemode grid");
                stage = 3;
                ticks = 0;
            } else if (stage == 3 && ticks >= 40) {
                open(client, new PlayerLookupScreen("!"), "invalid-name lookup");
                stage = 4;
                ticks = 0;
            } else if (stage == 4 && ticks >= 40) {
                check(client.gui.screen().getNarrationMessage().getString().contains("not a name"), "invalid-name narration");
                finish(client, null);
            }
        } catch (Throwable failure) {
            finish(client, failure);
        }
    }

    private void installControlledState() throws Exception {
        List<TierSource> sources = Source.ALL.stream().<TierSource>map(source -> new TierSource() {
            public Source source() { return source; }
            public CompletableFuture<Map<String, Tier>> fetch(UUID uuid) {
                Tier tier = switch (source) {
                    case PVPTIERS -> new Tier(2, true, false);
                    case PVPHQ -> Tier.parse("MT3").orElseThrow();
                    case SUBTIERS -> new Tier(3, false, false);
                    case NOVATIERS -> new Tier(1, true, true);
                };
                return CompletableFuture.completedFuture(Map.of(JustTiersConfig.defaultGamemode(source), tier));
            }
        }).toList();
        TierCache cache = new TierCache(sources);
        RefreshLifecycle refresh = new RefreshLifecycle(cache, () -> CompletableFuture.completedFuture(null));
        SettingsApplication settings = new SettingsApplication(new JustTiersConfig(),
                FabricLoader.getInstance().getConfigDir().resolve("smoke-settings.json"), cache, refresh);
        // Injection belongs only to this test mod; production APIs remain unchanged.
        replace("cache", cache);
        replace("settings", settings);
        replace("refresh", refresh);
    }

    private static void replace(String name, Object value) throws Exception {
        Field field = JustTiersClient.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private void checkNames(Minecraft client) {
        UUID uuid = UUID.fromString("12345678-1234-4234-8234-123456789abc");
        RemotePlayer remote = new RemotePlayer(client.level, new GameProfile(uuid, "SmokePlayer"));
        remote.setId(234567);
        remote.setPos(client.player.getX() + 2, client.player.getY(), client.player.getZ());
        client.level.addEntity(remote);
        for (Source source : Source.ALL) JustTiersClient.cache().load(source, uuid).join();
        PlayerTeam team = client.level.getScoreboard().addPlayerTeam("smoke-formatting");
        team.setPlayerPrefix(Component.literal("[Team] ").withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD));
        client.level.getScoreboard().addPlayerToTeam("SmokePlayer", team);

        edit(config -> config.setEnabled(false));
        Component original = remote.getDisplayName();
        check(original.getString().equals("[Team] SmokePlayer"), "actual original team display name");
        edit(config -> config.setEnabled(true));
        Component decorated = remote.getDisplayName();
        check(decorated.getString().endsWith(original.getString()) && decorated.getString().contains("HT2"),
                "Player.getDisplayName mixin prepends cached badge on RemotePlayer");
        check(decorated.getString().contains("RHT1"), "retired placement visible");
        check(decorated.getString().contains("MT3"), "PvPHQ middle tier visible");
        List<Run> originalRuns = runs(original);
        List<Run> decoratedRuns = runs(decorated);
        check(decoratedRuns.stream().anyMatch(run -> run.text().equals("MT3")
                && run.style().getColor() != null && run.style().getColor().getValue() == 0xD2D2D2),
                "PvPHQ uses the default gray color");
        check(decoratedRuns.subList(decoratedRuns.size() - originalRuns.size(), decoratedRuns.size())
                .equals(originalRuns), "original text styles preserved");
        check(decoratedRuns.stream().anyMatch(run -> Icons.FONT.equals(run.style().getFont())), "icon font applied");
        check(decoratedRuns.stream().filter(run -> run.text().contains("HT") || run.text().contains("SmokePlayer"))
                .noneMatch(run -> Icons.FONT.equals(run.style().getFont())), "tier labels and player name use text font");
        check(client.font.width(decorated) > client.font.width(original), "font measures transformed badge");

        edit(config -> config.setBadgePosition(BadgePosition.AFTER));
        check(remote.getDisplayName().getString().startsWith(original.getString()), "after-name badge position");
        edit(config -> { config.setShowIcons(false); config.setShowBrackets(false); });
        Component plain = remote.getDisplayName();
        check(runs(plain).stream().noneMatch(run -> Icons.FONT.equals(run.style().getFont())), "icons off removes icon font");
        check(!plain.getString().substring(original.getString().length()).contains("["), "brackets off removes badge brackets");
        edit(config -> config.setShowRetired(false));
        check(!remote.getDisplayName().getString().contains("RHT1"), "retired placement hidden");
        edit(config -> { Source.ALL.forEach(source -> config.setSiteEnabled(source, source == Source.PVPTIERS)); config.setPalette(Palette.CUSTOM);
            config.setCustomColor(Source.PVPTIERS, 0x123456); });
        check(!remote.getDisplayName().getString().contains("LT3")
                && !remote.getDisplayName().getString().contains("MT3"), "disabled sites excluded from cached badge");
        edit(config -> config.setSiteEnabled(Source.PVPHQ, true));
        check(remote.getDisplayName().getString().contains("HT2")
                && remote.getDisplayName().getString().contains("MT3"), "two independently enabled sites visible");
        edit(config -> Source.ALL.forEach(source -> config.setSiteEnabled(source, false)));
        check(remote.getDisplayName().equals(original), "all sites disabled restores the original name");
        edit(config -> config.setSiteEnabled(Source.PVPTIERS, true));
        check(runs(remote.getDisplayName()).stream().anyMatch(run -> run.text().equals("HT2")
                && run.style().getColor() != null && run.style().getColor().getValue() == 0x123456), "custom tier color");
        edit(config -> config.setHideOwnBadge(true));
        check(remote.getDisplayName().getString().contains("HT2"), "hide-own preserves remote badge");
        // Developer profiles may be offline v3 UUIDs. Use a temporary controlled v4
        // identity so this assertion tests hide-own rather than the offline filter.
        UUID localUuid = client.player.getUUID();
        try {
            client.player.setUUID(UUID.fromString("12345678-1234-4234-8234-123456789abd"));
            JustTiersClient.cache().load(Source.PVPTIERS, client.player.getUUID()).join();
            check(!client.player.getDisplayName().getString().contains("HT2"), "hide-own excludes ranked local player");
            edit(config -> config.setHideOwnBadge(false));
            check(client.player.getDisplayName().getString().contains("HT2"), "local badge restored");
        } finally {
            client.player.setUUID(localUuid);
        }
        RemotePlayer offline = new RemotePlayer(client.level, new GameProfile(
                UUID.fromString("12345678-1234-3234-8234-123456789abc"), "OfflineSmoke"));
        check(offline.getDisplayName().getString().equals("OfflineSmoke"), "v3 offline player undecorated");
        edit(config -> config.setEnabled(false));
        check(remote.getDisplayName().equals(original), "disable restores original component");
        edit(config -> config.setEnabled(true));
    }

    private static List<Run> runs(Component component) {
        List<Run> result = new ArrayList<>();
        component.visit((style, text) -> { if (!text.isEmpty()) result.add(new Run(text, style));
            return java.util.Optional.empty(); }, Style.EMPTY);
        return result;
    }

    private record Run(String text, Style style) {}

    private static void edit(Consumer<JustTiersConfig> action) {
        JustTiersClient.settings().editSession(action);
    }

    private void open(Minecraft client, Screen screen, String name) {
        client.setScreenAndShow(screen);
        for (int[] size : new int[][] {{320, 240}, {427, 240}, {480, 270}}) {
            screen.resize(size[0], size[1]);
            check(!screen.children().isEmpty(), name + " widgets initialize at " + size[0] + "x" + size[1]);
        }
        checks.add("OPEN: " + name + " rendered for 40 client ticks at 480x270");
    }

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add("PASS: " + description);
        System.out.println("JUSTTIERS_SMOKE_PASS " + description);
    }

    private void finish(Minecraft client, Throwable failure) {
        stage = 99;
        checks.add("LIMIT: controlled integrated world and fake tier sources; no authenticated multiplayer");
        checks.add("LIMIT: no ModMenu, live recovery, spoken narration, authenticated skins, or visual font inspection");
        checks.add(failure == null ? "JUSTTIERS_SMOKE_SUCCESS" : "JUSTTIERS_SMOKE_FAILURE " + failure);
        try { Files.write(report, checks); } catch (Exception error) { error.printStackTrace(); }
        if (failure != null) failure.printStackTrace();
        System.out.println(checks.getLast());
        client.stop();
    }
}
