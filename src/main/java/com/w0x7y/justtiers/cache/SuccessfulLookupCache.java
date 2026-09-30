package com.w0x7y.justtiers.cache;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Shares pending lookups and successful answers, but lets a failed lookup retry. */
public final class SuccessfulLookupCache<K, V> {
    private final ConcurrentHashMap<K, CompletableFuture<V>> entries = new ConcurrentHashMap<>();

    /** The key may be normalized while the request keeps the caller's original input. */
    public CompletableFuture<V> get(K key, Supplier<CompletableFuture<V>> fetch) {
        CompletableFuture<V> cached = entries.get(key);
        if (cached != null) {
            if (!cached.isCompletedExceptionally()) return cached;
            // A caller can observe failure before the eviction callback runs.
            entries.remove(key, cached);
        }
        CompletableFuture<V> pending = entries.computeIfAbsent(key, ignored -> fetch.get());
        // Outside computeIfAbsent: even an already-failed future must be removed only
        // after it has been inserted, without recursively updating the same map.
        pending.whenComplete((value, error) -> {
            if (error != null) entries.remove(key, pending);
        });
        return pending;
    }
}
