package com.w0x7y.justtiers.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w0x7y.justtiers.JustTiers;
import com.w0x7y.justtiers.tier.DiscoveredGamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Parses the NovaTiers bulk {@code /users} array. Keys in the tier maps are spaced
 * display names ("Spear Mace"), which we normalise to slugs. Retirement comes from
 * the sibling {@code retiredTiers} boolean map, with an {@code R} string prefix
 * accepted as a fallback. Peak tiers are deliberately ignored.
 *
 * <p>A key this build does not register becomes a discovered gamemode when its slug
 * passes {@link DiscoveredGamemodes}' sanitizing, up to its per-player cap. Placements in
 * discovered gamemodes are understood data, so a player listed only in them is ranked.
 * A placement whose key is refused is dropped and counted in {@code unknownGamemodes}; a
 * player whose every placement was refused is rejected rather than shown as unranked,
 * because the record says nothing this build can trust about their standing.
 */
public final class NovaParser {

    private static final Gson GSON = new Gson();

    /**
     * Immutable placements and diagnostics from one bulk response. The compatibility
     * map preserves understood placements, but callers must check rejectedPlayers
     * before treating an identifiable player's answer as complete.
     * {@code unknownGamemodes} counts placements dropped for an unacceptable gamemode key
     * or over the discovered-gamemode cap.
     */
    public record ParsedIndex(Map<UUID, Map<String, Tier>> users, Set<UUID> rejectedPlayers,
                              int malformedUsers, int malformedPlacements, int unknownGamemodes) {
        public ParsedIndex {
            Map<UUID, Map<String, Tier>> copy = new HashMap<>();
            users.forEach((uuid, tiers) -> copy.put(uuid, Map.copyOf(tiers)));
            users = Map.copyOf(copy);
            rejectedPlayers = Set.copyOf(rejectedPlayers);
        }
    }

    /** Compatibility view; sources should use {@link #parseUsersDetailed(String)}. */
    public static Map<UUID, Map<String, Tier>> parseUsers(String json) {
        return parseUsersDetailed(json).users();
    }

    public static ParsedIndex parseUsersDetailed(String json) {
        JsonArray array;
        try {
            JsonElement parsed = GSON.fromJson(json, JsonElement.class);
            if (parsed == null || !parsed.isJsonArray()) {
                throw new TierLookupException("NovaTiers response must be a JSON array");
            }
            array = parsed.getAsJsonArray();
        } catch (RuntimeException e) {
            throw new TierLookupException("Malformed NovaTiers response", e);
        }

        Map<UUID, Map<String, Tier>> index = new HashMap<>();
        Map<UUID, Map<String, Tier>> seenPlacements = new HashMap<>();
        Set<UUID> rejected = new HashSet<>();
        int understoodUsers = 0;
        int malformedUsers = 0;
        int malformedPlacements = 0;
        int unknownGamemodes = 0;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                malformedUsers++;
                continue;
            }
            JsonObject user = element.getAsJsonObject();
            JsonElement identity = user.get("minecraftUuid");
            Optional<UUID> uuid = isString(identity)
                    ? parseUuid(identity.getAsString()) : Optional.empty();
            if (uuid.isEmpty()) {
                malformedUsers++;
                continue;
            }
            JsonElement rawPlacements = user.get("tiers");
            if (rawPlacements == null || !rawPlacements.isJsonObject()) {
                malformedUsers++;
                rejected.add(uuid.get());
                continue;
            }
            JsonElement rawRetired = user.get("retiredTiers");
            boolean malformed = rawRetired != null && !rawRetired.isJsonNull()
                    && !rawRetired.isJsonObject();
            JsonObject retiredMap = rawRetired != null && rawRetired.isJsonObject()
                    ? rawRetired.getAsJsonObject() : new JsonObject();
            JsonObject placements = rawPlacements.getAsJsonObject();
            DiscoveredGamemodes.Collector tiers = new DiscoveredGamemodes.Collector(Source.NOVATIERS);
            Map<String, Tier> allPlacements = new LinkedHashMap<>();
            boolean understood = placements.isEmpty();
            boolean anyRefused = false;
            for (Map.Entry<String, JsonElement> entry : placements.entrySet()) {
                Optional<Tier> parsed = isString(entry.getValue())
                        ? Tier.parse(entry.getValue().getAsString()) : Optional.empty();
                JsonElement retirement = retiredMap.get(entry.getKey());
                if (parsed.isEmpty() || entry.getKey().isBlank()
                        || (retirement != null && !retirement.isJsonNull()
                        && (!retirement.isJsonPrimitive() || !retirement.getAsJsonPrimitive().isBoolean()))) {
                    malformedPlacements++;
                    malformed = true;
                    continue;
                }
                Tier tier = parsed.get();
                if (retirement != null && !retirement.isJsonNull()) {
                    tier = new Tier(tier.level(), tier.division(), retirement.getAsBoolean());
                }
                Optional<String> slug = DiscoveredGamemodes.novaSlug(entry.getKey());
                if (slug.isPresent() && tiers.offer(slug.get(), tier)) {
                    understood = true;
                } else {
                    // Refused keys and placements over the cap are dropped, not malformed.
                    unknownGamemodes++;
                    anyRefused = true;
                }
                String key = slug.orElse(entry.getKey());
                Tier previous = allPlacements.putIfAbsent(key, tier);
                if (previous != null && !previous.equals(tier)) {
                    malformedPlacements++;
                    malformed = true;
                }
            }
            if (understood) {
                understoodUsers++;
            } else if (anyRefused) {
                // Only refused placements: neither ranked nor known to be unranked.
                rejected.add(uuid.get());
            }
            Map<String, Tier> previous = seenPlacements.putIfAbsent(uuid.get(), allPlacements);
            if (previous != null && !previous.equals(allPlacements)) {
                malformed = true;
            }
            if (malformed) {
                malformedUsers++;
                rejected.add(uuid.get());
            }
            Map<String, Tier> admitted = tiers.tiers();
            if (!admitted.isEmpty()) {
                index.putIfAbsent(uuid.get(), admitted);
            }
        }
        if (!array.isEmpty() && understoodUsers == 0) {
            throw new TierLookupException("NovaTiers response contained no valid player records");
        }
        if (malformedUsers > 0 || malformedPlacements > 0) {
            JustTiers.LOGGER.warn("NovaTiers parsing found {} malformed user records, {} malformed placements and {} rejected players",
                    malformedUsers, malformedPlacements, rejected.size());
        }
        return new ParsedIndex(index, rejected, malformedUsers, malformedPlacements, unknownGamemodes);
    }

    private static boolean isString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
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
