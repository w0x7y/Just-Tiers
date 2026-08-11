package com.w0x7y.justtiers.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w0x7y.justtiers.JustTiers;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Tier;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Parses the NovaTiers bulk {@code /users} array. Keys in the tier maps are spaced
 * display names ("Spear Mace"), which we normalise to slugs. Retirement comes from
 * the sibling {@code retiredTiers} boolean map, with an {@code R} string prefix
 * accepted as a fallback. Peak tiers are deliberately ignored.
 */
public final class NovaParser {

    private static final Gson GSON = new Gson();

    public static Map<UUID, Map<String, Tier>> parseUsers(String json) {
        Map<UUID, Map<String, Tier>> index = new HashMap<>();
        if (json == null || json.isBlank()) {
            return index;
        }

        JsonArray array;
        try {
            JsonElement parsed = GSON.fromJson(json, JsonElement.class);
            if (parsed == null || !parsed.isJsonArray()) {
                return index;
            }
            array = parsed.getAsJsonArray();
        } catch (RuntimeException e) {
            JustTiers.LOGGER.debug("Ignoring malformed NovaTiers payload", e);
            return index;
        }

        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            try {
                parseUser(element.getAsJsonObject(), index);
            } catch (RuntimeException e) {
                JustTiers.LOGGER.debug("Skipping unparseable NovaTiers user", e);
            }
        }
        return index;
    }

    private static void parseUser(JsonObject user, Map<UUID, Map<String, Tier>> index) {
        if (!user.has("minecraftUuid") || user.get("minecraftUuid").isJsonNull()) {
            return;
        }
        Optional<UUID> uuid = parseUuid(user.get("minecraftUuid").getAsString());
        if (uuid.isEmpty() || !user.has("tiers") || !user.get("tiers").isJsonObject()) {
            return;
        }

        JsonObject retiredMap = user.has("retiredTiers") && user.get("retiredTiers").isJsonObject()
                ? user.getAsJsonObject("retiredTiers")
                : new JsonObject();

        Map<String, Tier> tiers = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : user.getAsJsonObject("tiers").entrySet()) {
            Optional<String> slug = Gamemodes.normaliseNovaKey(entry.getKey());
            if (slug.isEmpty() || entry.getValue().isJsonNull()) {
                continue;
            }
            Optional<Tier> parsed = Tier.parse(entry.getValue().getAsString());
            if (parsed.isEmpty()) {
                continue;
            }
            // The retiredTiers map is authoritative when it has an entry for this
            // gamemode: its boolean wins in BOTH directions. The R prefix on the tier
            // string is only a fallback for keys the map does not mention.
            boolean mapHasEntry = retiredMap.has(entry.getKey())
                    && !retiredMap.get(entry.getKey()).isJsonNull();

            Tier tier = parsed.get();
            boolean retired = mapHasEntry
                    ? retiredMap.get(entry.getKey()).getAsBoolean()
                    : tier.retired();
            if (retired != tier.retired()) {
                tier = new Tier(tier.level(), tier.high(), retired);
            }
            tiers.put(slug.get(), tier);
        }

        if (!tiers.isEmpty()) {
            index.put(uuid.get(), tiers);
        }
    }

    /** Accepts both the dashed and the 32-character undashed UUID forms. */
    public static Optional<UUID> parseUuid(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.trim();
        try {
            if (s.length() == 32) {
                return Optional.of(new UUID(
                        Long.parseUnsignedLong(s.substring(0, 16), 16),
                        Long.parseUnsignedLong(s.substring(16), 16)));
            }
            return Optional.of(UUID.fromString(s));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private NovaParser() {
    }
}
