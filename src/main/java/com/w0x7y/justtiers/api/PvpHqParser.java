package com.w0x7y.justtiers.api;

import com.google.gson.JsonParser;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.LinkedHashMap;
import java.util.Map;

/** PvPHQ's ranked array includes current labels and explicitly unranked gametypes. */
public final class PvpHqParser {
    public static Map<String, Tier> parseProfile(String json) {
        try {
            var root = JsonParser.parseString(json).getAsJsonObject();
            var ranked = root.get("ranked");
            if (ranked == null || !ranked.isJsonArray()) {
                throw new TierLookupException("PvPHQ profile must contain a ranked array");
            }
            Map<String, Tier> tiers = new LinkedHashMap<>();
            boolean recognized = ranked.getAsJsonArray().isEmpty();
            for (var value : ranked.getAsJsonArray()) {
                if (!value.isJsonObject()) continue;
                var entry = value.getAsJsonObject();
                try {
                    String slug = entry.get("gametype").getAsString();
                    // Profile responses still use ht_cart while metadata calls it cart.
                    if (slug.equals("ht_cart")) slug = "cart";
                    if (Gamemodes.find(Source.PVPHQ, slug).isEmpty()) continue;
                    if (entry.has("unranked") && entry.get("unranked").getAsBoolean()) {
                        recognized = true;
                        continue;
                    }
                    var tier = Tier.parse(entry.get("tier").getAsString());
                    if (tier.isEmpty()) continue;
                    tiers.put(slug, tier.orElseThrow());
                    recognized = true;
                    // Inactive means not recently played, not a retired placement.
                    // Peak tier/rating fields do not describe the current placement.
                } catch (RuntimeException ignored) {
                    // A malformed entry does not discard the other gametypes.
                }
            }
            if (!recognized) throw new TierLookupException("PvPHQ profile contained no valid ranked entries");
            return Map.copyOf(tiers);
        } catch (RuntimeException error) {
            throw new TierLookupException("Malformed PvPHQ profile", error);
        }
    }

    private PvpHqParser() { }
}
