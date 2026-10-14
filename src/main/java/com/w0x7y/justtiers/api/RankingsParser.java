package com.w0x7y.justtiers.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w0x7y.justtiers.JustTiers;
import com.w0x7y.justtiers.tier.DiscoveredGamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.Map;
import java.util.Optional;

/**
 * Parses the shared PvPTiers/SubTiers rankings map:
 * {@code {"<slug>": {"tier":1-5, "pos":0|1, "retired":bool, ...}}}.
 * {@code pos == 0} means HT. Peak fields are deliberately ignored.
 *
 * <p>Keys are lowercased slugs. A key this build does not register becomes a discovered
 * gamemode when it passes {@link DiscoveredGamemodes}' sanitizing and cap; otherwise its
 * placement is dropped. A non-empty payload with no admitted placement fails the lookup:
 * placements under keys we refused are no evidence that the player is unranked. A payload
 * whose only placements are in valid discovered gamemodes is therefore a ranked answer,
 * not a failure.
 */
public final class RankingsParser {

    private static final Gson GSON = new Gson();

    public static Map<String, Tier> parseRankings(Source source, String json) {
        DiscoveredGamemodes.Collector result = new DiscoveredGamemodes.Collector(source);
        JsonObject root;
        try {
            JsonElement parsed = GSON.fromJson(json, JsonElement.class);
            if (parsed == null || !parsed.isJsonObject()) {
                throw new TierLookupException("Rankings response must be a JSON object");
            }
            root = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new TierLookupException("Malformed rankings response", e);
        }

        int refusedKeys = 0;
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject ranking = entry.getValue().getAsJsonObject();
            try {
                int tier = ranking.get("tier").getAsInt();
                int pos = ranking.get("pos").getAsInt();
                boolean retired = ranking.has("retired")
                        && !ranking.get("retired").isJsonNull()
                        && ranking.get("retired").getAsBoolean();
                if (tier < 1 || tier > 5 || (pos != 0 && pos != 1)) {
                    continue;
                }
                Optional<String> slug = DiscoveredGamemodes.profileSlug(entry.getKey());
                if (slug.isEmpty()) {
                    refusedKeys++;
                    continue;
                }
                result.offer(slug.get(), Tier.fromRanking(tier, pos, retired));
            } catch (RuntimeException e) {
                // A single bad gamemode entry must never sink the whole profile.
                JustTiers.LOGGER.warn("Skipping unparseable ranking '{}'", entry.getKey(), e);
            }
        }
        Map<String, Tier> tiers = result.tiers();
        int refused = refusedKeys + result.refused();
        if (refused > 0) {
            JustTiers.LOGGER.warn("{} rankings refused {} placements with unacceptable or excess gamemode keys",
                    source, refused);
        }
        if (!root.isEmpty() && tiers.isEmpty()) {
            throw new TierLookupException("Rankings response contained no valid rankings");
        }
        return tiers;
    }

    private RankingsParser() {
    }
}
