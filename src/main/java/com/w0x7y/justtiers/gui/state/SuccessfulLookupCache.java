package com.w0x7y.justtiers.gui.state;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Shares pending lookups and successful answers, but lets a failed lookup retry. */
public final class SuccessfulLookupCache<K, V> {
    private final ConcurrentHashMap<K, CompletableFuture<V>> entries = new ConcurrentHashMap<>();
    private final Function<K, CompletableFuture<V>> fetch;

    public SuccessfulLookupCache(Function<K, CompletableFuture<V>> fetch) {
        this.fetch = fetch;
    }

    public CompletableFuture<V> get(K key) {
        CompletableFuture<V> pending = entries.computeIfAbsent(key, fetch);
        // Outside computeIfAbsent: even an already-failed future must be removed only
        // after it has been inserted, without recursively updating the same map.
        pending.whenComplete((value, error) -> {
            if (error != null) entries.remove(key, pending);
        });
        return pending;
    }
}
