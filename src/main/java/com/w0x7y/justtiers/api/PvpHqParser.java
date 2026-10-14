package com.w0x7y.justtiers.api;

import com.google.gson.JsonParser;
import com.w0x7y.justtiers.JustTiers;
import com.w0x7y.justtiers.tier.DiscoveredGamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.Map;
import java.util.Optional;

/**
 * PvPHQ's ranked array includes current labels and explicitly unranked gametypes.
 *
 * <p>A gametype this build does not register becomes a discovered gamemode when its
 * lowercased slug passes {@link DiscoveredGamemodes}' sanitizing and cap. An entry,
 * placed or explicitly unranked, under an acceptable gametype is recognized content, so
 * a profile listing only gametypes this build has never seen is a valid answer. A
 * non-empty array with no recognized entry still fails: entries under refused gametype
 * keys say nothing about whether the player is ranked.
 */
public final class PvpHqParser {
    public static Map<String, Tier> parseProfile(String json) {
        try {
            var root = JsonParser.parseString(json).getAsJsonObject();
            var ranked = root.get("ranked");
            if (ranked == null || !ranked.isJsonArray()) {
                throw new TierLookupException("PvPHQ profile must contain a ranked array");
            }
            DiscoveredGamemodes.Collector tiers = new DiscoveredGamemodes.Collector(Source.PVPHQ);
            boolean recognized = ranked.getAsJsonArray().isEmpty();
            int refusedKeys = 0;
            for (var value : ranked.getAsJsonArray()) {
                if (!value.isJsonObject()) continue;
                var entry = value.getAsJsonObject();
                try {
                    Optional<String> gametype = DiscoveredGamemodes.profileSlug(entry.get("gametype").getAsString());
                    if (gametype.isEmpty()) {
                        refusedKeys++;
                        continue;
                    }
                    String slug = gametype.get();
                    // Profile responses still use ht_cart while metadata calls it cart.
                    if (slug.equals("ht_cart")) slug = "cart";
                    if (entry.has("unranked") && entry.get("unranked").getAsBoolean()) {
                        recognized = true;
                        continue;
                    }
                    var tier = Tier.parse(entry.get("tier").getAsString());
                    if (tier.isEmpty()) continue;
                    if (tiers.offer(slug, tier.orElseThrow())) {
                        recognized = true;
                    }
                    // Inactive means not recently played, not a retired placement.
                    // Peak tier/rating fields do not describe the current placement.
                } catch (RuntimeException ignored) {
                    // A malformed entry does not discard the other gametypes.
                }
            }
            int refused = refusedKeys + tiers.refused();
            if (refused > 0) {
                JustTiers.LOGGER.warn("PvPHQ profile refused {} entries with unacceptable or excess gametype keys", refused);
            }
            if (!recognized) throw new TierLookupException("PvPHQ profile contained no valid ranked entries");
            return Map.copyOf(tiers.tiers());
        } catch (RuntimeException error) {
            throw new TierLookupException("Malformed PvPHQ profile", error);
        }
    }

    private PvpHqParser() { }
}
