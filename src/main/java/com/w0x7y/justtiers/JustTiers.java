package com.w0x7y.justtiers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
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

    /** A JSON GET carrying the mod's User-Agent. Every request this mod makes is one. */
    public static HttpRequest jsonRequest(String url, Duration timeout) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .timeout(timeout)
                .GET()
                .build();
    }

    /** Trims one trailing slash, so callers can append "/path" without doubling it. */
    public static String trimTrailingSlash(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private JustTiers() {
    }
}
