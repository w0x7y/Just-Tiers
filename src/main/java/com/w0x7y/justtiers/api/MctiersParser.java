package com.w0x7y.justtiers.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w0x7y.justtiers.JustTiers;
import com.w0x7y.justtiers.tier.Tier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses the shared MCTiers/SubTiers v2 rankings payload:
 * {@code {"<slug>": {"tier":1-5, "pos":0|1, "retired":bool, ...}}}.
 * {@code pos == 0} means HT. Peak fields are deliberately ignored.
 */
public final class MctiersParser {

    private static final Gson GSON = new Gson();

    public static Map<String, Tier> parseRankings(String json) {
        Map<String, Tier> result = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return result;
        }

        JsonObject root;
        try {
            JsonElement parsed = GSON.fromJson(json, JsonElement.class);
            if (parsed == null || !parsed.isJsonObject()) {
                return result;
            }
            root = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            JustTiers.LOGGER.warn("Ignoring malformed rankings payload", e);
            return result;
        }

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
                result.put(entry.getKey(), Tier.fromMctiers(tier, pos, retired));
            } catch (RuntimeException e) {
                // A single bad gamemode entry must never sink the whole profile.
                JustTiers.LOGGER.warn("Skipping unparseable ranking '{}'", entry.getKey(), e);
            }
        }
        return result;
    }

    private MctiersParser() {
    }
}
