package com.w0x7y.justtiers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.Executors;

public final class JustTiers {
    public static final String MOD_ID = "justtiers";
    private static final String VERSION_RESOURCE = "/justtiers-version.properties";
    public static final String VERSION = readVersion();
    public static final String USER_AGENT =
            "Just-Tiers/" + VERSION + " (+https://github.com/w0x7y/Just-Tiers)";

    public static final Logger LOGGER = LoggerFactory.getLogger("Just-Tiers");

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    /**
     * Reads the version Gradle stamped into the jar, so it can never drift from the one
     * in gradle.properties and fabric.mod.json the way a hand-edited constant does.
     */
    private static String readVersion() {
        try (InputStream in = JustTiers.class.getResourceAsStream(VERSION_RESOURCE)) {
            if (in != null) {
                Properties properties = new Properties();
                properties.load(in);
                String version = properties.getProperty("version");
                if (version != null && !version.isBlank()) {
                    return version;
                }
            }
        } catch (IOException ignored) {
            // Falls through to the placeholder: an unreadable stamp is not worth crashing over.
        }
        return "unknown";
    }

    public static HttpClient httpClient() {
        return HTTP_CLIENT;
    }

    private JustTiers() {
    }
}
