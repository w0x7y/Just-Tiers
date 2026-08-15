package com.w0x7y.justtiers.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.w0x7y.justtiers.JustTiers;
import com.w0x7y.justtiers.render.model.BadgePosition;
import com.w0x7y.justtiers.render.model.NametagStyle;
import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class JustTiersConfig {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(DisplayMode.class, new IdEnumAdapter<>(
                    "displayMode", DisplayMode.class, DisplayMode.ALL, DisplayMode::id))
            .registerTypeAdapter(BadgePosition.class, new IdEnumAdapter<>(
                    "badgePosition", BadgePosition.class, BadgePosition.BEFORE,
                    BadgePosition::id))
            .create();

    private static final Map<Source, String> DEFAULT_GAMEMODES = Map.of(
            Source.MCTIERS, "vanilla",
            Source.SUBTIERS, "elytra",
            Source.NOVATIERS, "vanilla");

    private boolean enabled = true;
    private boolean showRetired = true;
    private DisplayMode displayMode = DisplayMode.ALL;
    private Map<String, String> selectedGamemodes = new HashMap<>();
    private int novaRefreshMinutes = 30;
    private boolean showDownloadProgress = true;
    private BadgePosition badgePosition = BadgePosition.BEFORE;
    private boolean showIcons = true;
    private boolean showBrackets = true;

    /**
     * Cache for {@link #selectedGamemodesBySource()}, dropped whenever a selection
     * changes. Transient so it never reaches the config file, and volatile because the
     * render thread reads it.
     */
    private transient volatile Map<Source, String> resolvedSelection;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Applies to every display mode: when false, retired tiers are never rendered. */
    public boolean isShowRetired() {
        return showRetired;
    }

    public void setShowRetired(boolean showRetired) {
        this.showRetired = showRetired;
    }

    public DisplayMode getDisplayMode() {
        return displayMode == null ? DisplayMode.ALL : displayMode;
    }

    public void setDisplayMode(DisplayMode displayMode) {
        this.displayMode = displayMode;
    }

    public int getNovaRefreshMinutes() {
        return novaRefreshMinutes;
    }

    public void setNovaRefreshMinutes(int minutes) {
        this.novaRefreshMinutes = Math.clamp(minutes, 5, 1440);
    }

    /**
     * Whether the download indicator is drawn. It appears for every download the moment one
     * starts, including the timed background refresh, so this is the escape hatch for anyone
     * who finds that intrusive.
     */
    public boolean isShowDownloadProgress() {
        return showDownloadProgress;
    }

    public void setShowDownloadProgress(boolean showDownloadProgress) {
        this.showDownloadProgress = showDownloadProgress;
    }

    /** Which side of the name the badge is drawn on. */
    public BadgePosition getBadgePosition() {
        return badgePosition == null ? BadgePosition.BEFORE : badgePosition;
    }

    public void setBadgePosition(BadgePosition badgePosition) {
        this.badgePosition = badgePosition;
    }

    /**
     * Whether each tier carries its gamemode glyph. With icons off the sites are told
     * apart by tier colour alone, which is what the shortest possible badge costs.
     */
    public boolean isShowIcons() {
        return showIcons;
    }

    public void setShowIcons(boolean showIcons) {
        this.showIcons = showIcons;
    }

    /** Whether the badge is wrapped in {@code [ ]}. */
    public boolean isShowBrackets() {
        return showBrackets;
    }

    public void setShowBrackets(boolean showBrackets) {
        this.showBrackets = showBrackets;
    }

    /** The three cosmetic settings as the one value the nametag layout takes. */
    public NametagStyle nametagStyle() {
        return new NametagStyle(getBadgePosition(), showIcons, showBrackets);
    }

    /** The out-of-the-box gamemode for a site, and the config screen's reset target. */
    public static String defaultGamemode(Source source) {
        return DEFAULT_GAMEMODES.get(source);
    }

    /** Falls back to the site default when the stored slug is absent or no longer valid. */
    public String selectedGamemode(Source source) {
        if (selectedGamemodes == null) {
            selectedGamemodes = new HashMap<>();
        }
        String slug = selectedGamemodes.get(source.name());
        if (slug != null && Gamemodes.find(source, slug).isPresent()) {
            return slug;
        }
        return DEFAULT_GAMEMODES.get(source);
    }

    public void setSelectedGamemode(Source source, String slug) {
        if (selectedGamemodes == null) {
            selectedGamemodes = new HashMap<>();
        }
        selectedGamemodes.put(source.name(), slug);
        resolvedSelection = null;
    }

    /**
     * Every site's selection, resolved once. The nametag asks for this per player per
     * frame, and resolving it means three validity checks and a fresh map each time;
     * only a setter can change the answer, so it is held until one does.
     */
    public Map<Source, String> selectedGamemodesBySource() {
        Map<Source, String> cached = resolvedSelection;
        if (cached != null) {
            return cached;
        }
        Map<Source, String> result = new EnumMap<>(Source.class);
        for (Source source : Source.ALL) {
            result.put(source, selectedGamemode(source));
        }
        cached = Map.copyOf(result);
        resolvedSelection = cached;
        return cached;
    }

    public static JustTiersConfig load(Path path) {
        if (!Files.isRegularFile(path)) {
            return new JustTiersConfig();
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            JustTiersConfig config = GSON.fromJson(reader, JustTiersConfig.class);
            if (config == null) {
                return new JustTiersConfig();
            }
            // clamp bypassed by reflection during deserialization
            config.setNovaRefreshMinutes(config.getNovaRefreshMinutes());
            return config;
        } catch (IOException | RuntimeException e) {
            JustTiers.LOGGER.warn("Could not read config at {}, using defaults", path, e);
            return new JustTiersConfig();
        }
    }

    public void save(Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            JustTiers.LOGGER.warn("Could not save config to {}", path, e);
        }
    }
}
