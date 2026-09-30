package com.w0x7y.justtiers.cache;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Paces one owner's admitted work. Suppliers and future callbacks run outside the owner's lock. */
final class RequestQueue<K, V> {
    private final Object owner;
    private final int maxActive;
    private final int maxWaiting;
    private final ArrayDeque<Work<K, V>> interactive = new ArrayDeque<>();
    private final ArrayDeque<Work<K, V>> background = new ArrayDeque<>();
    private int active;
    private boolean draining;

    RequestQueue(Object owner, int maxActive, int maxWaiting) {
        this.owner = owner;
        this.maxActive = maxActive;
        this.maxWaiting = maxWaiting;
    }

    /** The owner stages insertion in the same critical section that publishes its claim. */
    Submission<V> enqueue(K key, boolean explicit, Supplier<CompletableFuture<V>> action) {
        synchronized (owner) {
            Work<K, V> work = new Work<>(key, action);
            Work<K, V> displaced = null;
            if (interactive.size() + background.size() >= maxWaiting) {
                if (explicit && !background.isEmpty()) displaced = background.removeLast();
                else return new Submission<>(CompletableFuture.failedFuture(new Deferred("Request queue is full")), null);
            }
            (explicit ? interactive : background).addLast(work);
            return new Submission<>(work.result, displaced == null ? null : displaced.result);
        }
    }

    /** Called after the owner's claim lock has been released. */
    void dispatch(Submission<V> submission) {
        if (submission.displaced != null) {
            submission.displaced.completeExceptionally(new Deferred("Explicit lookup took priority"));
        }
        drain();
    }

    void promote(K key) {
        synchronized (owner) {
            var iterator = background.iterator();
            while (iterator.hasNext()) {
                Work<K, V> work = iterator.next();
                if (work.key.equals(key)) {
                    iterator.remove();
                    interactive.addLast(work);
                    return;
                }
            }
        }
    }

    /** Removal is atomic with generation replacement; the owner publishes cancellation after unlocking. */
    List<CompletableFuture<V>> removeQueued(Predicate<K> obsolete) {
        List<CompletableFuture<V>> removed = new ArrayList<>();
        synchronized (owner) {
            for (var queue : List.of(interactive, background)) {
                var iterator = queue.iterator();
                while (iterator.hasNext()) {
                    Work<K, V> work = iterator.next();
                    if (obsolete.test(work.key)) {
                        iterator.remove();
                        removed.add(work.result);
                    }
                }
            }
        }
        return removed;
    }

    int active() { synchronized (owner) { return active; } }
    int waiting() { synchronized (owner) { return interactive.size() + background.size(); } }

    private void drain() {
        synchronized (owner) {
            if (draining) return;
            draining = true;
        }
        // Synchronous completion releases its slot without recursive queue draining.
        while (true) {
            Work<K, V> work;
            synchronized (owner) {
                if (active >= maxActive || (interactive.isEmpty() && background.isEmpty())) {
                    draining = false;
                    return;
                }
                work = (interactive.isEmpty() ? background : interactive).removeFirst();
                active++;
            }
            CompletableFuture<V> response;
            try {
                response = work.action.get();
            } catch (RuntimeException error) {
                response = CompletableFuture.failedFuture(error);
            }
            response.whenComplete((value, error) -> {
                synchronized (owner) { active--; }
                // Continuations observe settled capacity and can admit their next request.
                if (error == null) work.result.complete(value);
                else work.result.completeExceptionally(error);
                drain();
            });
        }
    }

    static final class Submission<V> {
        final CompletableFuture<V> result;
        private final CompletableFuture<V> displaced;
        Submission(CompletableFuture<V> result, CompletableFuture<V> displaced) {
            this.result = result;
            this.displaced = displaced;
        }
    }

    static final class Deferred extends RuntimeException {
        Deferred(String message) { super(message); }
    }

    private static final class Work<K, V> {
        final K key;
        final Supplier<CompletableFuture<V>> action;
        final CompletableFuture<V> result = new CompletableFuture<>();
        Work(K key, Supplier<CompletableFuture<V>> action) {
            this.key = key;
            this.action = action;
        }
    }
}
