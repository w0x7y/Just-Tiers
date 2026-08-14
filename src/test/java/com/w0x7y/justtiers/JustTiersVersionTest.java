package com.w0x7y.justtiers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The version Just-Tiers reports must come from the build, not from a constant someone
 * has to remember to bump. Both the log line and the User-Agent sent to the tier sites
 * are built from it, so drift here is silently wrong rather than loudly broken.
 */
class JustTiersVersionTest {

    private static final Pattern MOD_JSON_VERSION =
            Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");

    @Test
    void versionMatchesTheOneDeclaredInFabricModJson() throws IOException {
        assertEquals(fabricModJsonVersion(), JustTiers.VERSION);
    }

    @Test
    void userAgentCarriesTheBuildVersion() {
        assertTrue(JustTiers.USER_AGENT.contains(JustTiers.VERSION),
                "User-Agent should identify the running build, was: " + JustTiers.USER_AGENT);
    }

    private static String fabricModJsonVersion() throws IOException {
        try (InputStream in = JustTiers.class.getResourceAsStream("/fabric.mod.json")) {
            assertNotNull(in, "fabric.mod.json is missing from the test classpath");
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = MOD_JSON_VERSION.matcher(json);
            assertTrue(matcher.find(), "fabric.mod.json declares no version");
            return matcher.group(1);
        }
    }
}
