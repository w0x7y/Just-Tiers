package com.w0x7y.justtiers.preview;

import com.w0x7y.justtiers.render.model.NametagModel;
import com.w0x7y.justtiers.render.model.Segment;
import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.resolve.ResolvedTier;
import com.w0x7y.justtiers.resolve.TierResolver;
import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.List;
import java.util.Map;

/**
 * The invented player behind the config screen's nametag preview. The placements are
 * chosen so that every switch in the UI visibly changes the result: two sites' best
 * tiers are retired (so {@code showRetired} does something), each site has three
 * placements (so changing gamemode does something), and no site is ranked in every
 * gamemode (so the fallback rule is reachable).
 *
 * <p>Minecraft-free on purpose — the same resolve-then-model path the real nametag
 * uses, so a preview can never disagree with what is drawn in the world.
 */
public final class PreviewSample {

    public static final Map<Source, Map<String, Tier>> TIERS = Map.of(
            Source.MCTIERS, Map.of(
                    "vanilla", new Tier(2, true, false),
                    "axe", new Tier(3, false, false),
                    "sword", new Tier(4, true, false)),
            Source.SUBTIERS, Map.of(
                    "elytra", new Tier(3, false, false),
                    "bow", new Tier(5, true, false),
                    "minecart", new Tier(2, true, true)),
            Source.NOVATIERS, Map.of(
                    "vanilla", new Tier(4, true, false),
                    "uhc", new Tier(4, false, false),
                    "spearmace", new Tier(1, true, true)));

    public record Caption(Kind kind, String gamemodeName, String sourceName) {
        public enum Kind { SAMPLE, FALLBACK, EMPTY }
    }

    public static List<ResolvedTier> resolve(DisplayMode mode,
                                             Map<Source, String> selectedGamemodes,
                                             boolean showRetired) {
        return TierResolver.resolve(mode, TIERS, selectedGamemodes, showRetired);
    }

    public static List<Segment> segments(DisplayMode mode,
                                         Map<Source, String> selectedGamemodes,
                                         boolean showRetired) {
        return NametagModel.build(resolve(mode, selectedGamemodes, showRetired));
    }

    /**
     * Explains what the preview is showing: the selected gamemode, or — when the sample
     * player has no placement there — which gamemode it fell back from.
     */
    public static Caption caption(DisplayMode mode,
                                  Map<Source, String> selectedGamemodes,
                                  boolean showRetired) {
        List<ResolvedTier> resolved = resolve(mode, selectedGamemodes, showRetired);
        if (resolved.isEmpty()) {
            return new Caption(Caption.Kind.EMPTY, "", "");
        }

        var single = mode.singleSource();
        if (single.isEmpty()) {
            return new Caption(Caption.Kind.SAMPLE, "", "");
        }

        Source source = single.get();
        String slug = selectedGamemodes.get(source);
        String requested = Gamemodes.find(source, slug).map(Gamemode::displayName).orElse(slug);
        String shown = resolved.getFirst().gamemode().displayName();
        Caption.Kind kind = shown.equals(requested)
                ? Caption.Kind.SAMPLE
                : Caption.Kind.FALLBACK;
        return new Caption(kind, requested, source.displayName());
    }

    private PreviewSample() {
    }
}
