package com.w0x7y.justtiers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

public final class JustTiers {
    public static final String MOD_ID = "justtiers";
    public static final String VERSION = "1.0.0";
    public static final String USER_AGENT =
            "Just-Tiers/" + VERSION + " (+https://github.com/w0x7y/Just-Tiers)";

    public static final Logger LOGGER = LoggerFactory.getLogger("Just-Tiers");

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    public static HttpClient httpClient() {
        return HTTP_CLIENT;
    }

    private JustTiers() {
    }
}
