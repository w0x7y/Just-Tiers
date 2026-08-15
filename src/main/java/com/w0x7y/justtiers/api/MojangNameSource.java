package com.w0x7y.justtiers.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w0x7y.justtiers.JustTiers;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Resolves a Minecraft name to the account it belongs to, through Mojang's public
 * profile API. This is only the fallback path for {@code /justtiers lookup}: a player
 * who is on the server is resolved straight out of the tab list without touching the
 * network, so the name has to be someone offline before a request is made at all.
 *
 * <p>Answers are remembered for the session — names change rarely, and a lookup run
 * twice before a duel should cost one request, not two. A failure is not remembered, so
 * an unreachable Mojang is retried rather than replayed.
 */
public final class MojangNameSource {

    public static final String DEFAULT_BASE_URL = "https://api.mojang.com";

    /**
     * What can be asked about at all. Deliberately looser than Mojang's current rule:
     * that rule is only enforced on new accounts, and names shorter than three
     * characters were handed out before it existed. Asking about one and being told no
     * costs a request; refusing to ask calls a real account nonexistent. The character
     * class stays, because the name goes into the request path as typed.
     */
    private static final Pattern ASKABLE_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private static final Gson GSON = new Gson();

    private final HttpClient client;
    private final String baseUrl;
    private final Map<String, CompletableFuture<Optional<PlayerRef>>> cache =
            new ConcurrentHashMap<>();

    public MojangNameSource(HttpClient client, String baseUrl) {
        this.client = client;
        this.baseUrl = JustTiers.trimTrailingSlash(baseUrl);
    }

    /**
     * Whether a name is worth a request at all. A caller that gets {@code false} knows
     * the name itself is the problem, which is not the same as Mojang saying nobody owns
     * it — {@link #resolve} cannot tell the two apart in its return value, so the command
     * asks here first and words its message accordingly.
     */
    public static boolean isAskable(String name) {
        return name != null && ASKABLE_NAME.matcher(name).matches();
    }

    /**
     * @return the account, or an empty optional when Mojang says nobody owns that name.
     *         The future fails only when Mojang could not be asked — "no such player"
     *         and "could not tell" are different answers and the command says so.
     */
    public CompletableFuture<Optional<PlayerRef>> resolve(String name) {
        if (!isAskable(name)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String key = name.toLowerCase(Locale.ROOT);
        CompletableFuture<Optional<PlayerRef>> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        // computeIfAbsent, not request-then-putIfAbsent: two lookups of the same name
        // racing here would otherwise both reach Mojang and one answer be thrown away,
        // which is the rate limit paid twice for the deduplication promised above.
        CompletableFuture<Optional<PlayerRef>> pending =
                cache.computeIfAbsent(key, ignored -> request(name));
        // Attached outside computeIfAbsent: the removal touches the same map, and a
        // request that somehow completed inside the mapping function would re-enter it.
        pending.whenComplete((profile, error) -> {
            if (error != null) {
                cache.remove(key, pending);
            }
        });
        return pending;
    }

    private CompletableFuture<Optional<PlayerRef>> request(String name) {
        HttpRequest request = JustTiers.jsonRequest(
                baseUrl + "/users/profiles/minecraft/" + name, Duration.ofSeconds(10));

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int status = response.statusCode();
                    // Mojang answers 404 - historically 204 - for a name nobody owns.
                    if (status == 404 || status == 204) {
                        return Optional.<PlayerRef>empty();
                    }
                    if (status != 200) {
                        // Includes 429: being rate limited is not the same as the name
                        // not existing, so it must not be reported as one.
                        throw new TierLookupException(
                                "Mojang returned HTTP " + status + " for " + name);
                    }
                    return parseProfile(response.body(), name);
                })
                .whenComplete((profile, error) -> {
                    if (error != null) {
                        JustTiers.LOGGER.warn("Mojang name lookup failed for {}: {}",
                                name, error.toString());
                    }
                });
    }

    /** Prefers Mojang's spelling of the name, so {@code notch} prints back as Notch. */
    private static Optional<PlayerRef> parseProfile(String body, String requestedName) {
        try {
            JsonElement parsed = GSON.fromJson(body, JsonElement.class);
            if (parsed == null || !parsed.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject object = parsed.getAsJsonObject();
            if (!object.has("id") || object.get("id").isJsonNull()) {
                return Optional.empty();
            }
            String name = object.has("name") && !object.get("name").isJsonNull()
                    ? object.get("name").getAsString()
                    : requestedName;
            return NovaParser.parseUuid(object.get("id").getAsString())
                    .map(uuid -> new PlayerRef(name, uuid));
        } catch (RuntimeException e) {
            JustTiers.LOGGER.warn("Ignoring malformed Mojang profile response for {}",
                    requestedName, e);
            return Optional.empty();
        }
    }
}
