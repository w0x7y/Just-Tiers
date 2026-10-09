package com.w0x7y.justtiers.api;

import com.google.gson.JsonParser;
import com.w0x7y.justtiers.tier.Tier;

import java.util.Map;

/** PvPTiers profiles wrap the numeric placements in a rankings object. */
public final class PvpTiersParser {
    public static Map<String, Tier> parseProfile(String json) {
        try {
            var root = JsonParser.parseString(json).getAsJsonObject();
            var rankings = root.get("rankings");
            if (rankings == null || !rankings.isJsonObject()) {
                throw new TierLookupException("PvPTiers profile must contain a rankings object");
            }
            return RankingsParser.parseRankings(rankings.toString());
        } catch (RuntimeException error) {
            throw new TierLookupException("Malformed PvPTiers profile", error);
        }
    }

    private PvpTiersParser() { }
}
