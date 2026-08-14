package com.w0x7y.justtiers.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.w0x7y.justtiers.JustTiers;
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
            .registerTypeAdapter(DisplayMode.class, new DisplayModeAdapter())
            .create();

    /**
     * Persists {@link DisplayMode} as its lower-case {@link DisplayMode#id()} (the
     * documented on-disk/command-argument format), while still reading back the legacy
     * upper-case {@code name()} form that earlier builds wrote. An absent field keeps
     * whatever default the containing object already had; an explicit {@code null} or an
     * unrecognised string both fall back to {@link DisplayMode#ALL} with a warning naming
     * the offending value, rather than failing silently.
     */
    private static final class DisplayModeAdapter extends TypeAdapter<DisplayMode> {
        @Override
        public void write(JsonWriter out, DisplayMode value) throws IOException {
            out.value((value == null ? DisplayMode.ALL : value).id());
        }

        @Override
        public DisplayMode read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                JustTiers.LOGGER.warn("Config displayMode was null, using default {}", DisplayMode.ALL);
                return DisplayMode.ALL;
            }
            String raw = in.nextString();
            for (DisplayMode mode : DisplayMode.values()) {
                if (mode.id().equalsIgnoreCase(raw)) {
                    return mode;
                }
            }
            JustTiers.LOGGER.warn("Unrecognised config displayMode '{}', using default {}", raw, DisplayMode.ALL);
            return DisplayMode.ALL;
        }
    }

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
    }

    public Map<Source, String> selectedGamemodesBySource() {
        Map<Source, String> result = new EnumMap<>(Source.class);
        for (Source source : Source.values()) {
            result.put(source, selectedGamemode(source));
        }
        return result;
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
