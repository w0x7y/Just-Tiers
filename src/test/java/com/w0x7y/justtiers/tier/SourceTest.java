package com.w0x7y.justtiers.tier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SourceTest {

    @Test
    void everySiteHasAHomePageToLinkTo() {
        for (Source source : Source.values()) {
            assertTrue(source.homeUrl().startsWith("https://"),
                    source + " needs a link a browser can open");
        }
    }

    @Test
    void aHomePageIsTheSiteItselfNotTheApiItIsQueriedThrough() {
        // The lookup screen credits the leaderboards with a link; sending a reader to
        // /api would be sending them to a wall of JSON.
        for (Source source : Source.values()) {
            assertFalse(source.homeUrl().contains("/api"),
                    source + " links at its API rather than its site");
        }
        assertEquals("https://mctiers.com", Source.MCTIERS.homeUrl());
        assertEquals("https://subtiers.net", Source.SUBTIERS.homeUrl());
        assertEquals("https://novatiers.com", Source.NOVATIERS.homeUrl());
    }
}
