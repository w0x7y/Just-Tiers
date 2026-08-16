package com.w0x7y.justtiers.scan;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Keeps a scan from arriving at a leaderboard all at once. Work is handed in as fast as
 * the caller likes; at most {@code maxInFlight} of it is running, and each completion
 * releases exactly one waiter.
 *
 * <p>Owns no threads and performs no I/O: the caller runs the tasks and reports back.
 * Everything here is touched from the client thread only, so nothing is synchronised —
 * which is also what makes "never exceeds the cap" a unit test rather than a stress test.
 */
public final class ScanQueue {

    private final int maxInFlight;
    private final Deque<Runnable> waiting = new ArrayDeque<>();
    private int inFlight;

    public ScanQueue(int maxInFlight) {
        if (maxInFlight < 1) {
            throw new IllegalArgumentException("in-flight cap must be positive: " + maxInFlight);
        }
        this.maxInFlight = maxInFlight;
    }

    /** Runs the task now if there is room, and otherwise when room appears. */
    public void submit(Runnable task) {
        waiting.addLast(task);
        pump();
    }

    /** One task has finished — successfully or not — freeing its slot. */
    public void completed() {
        if (inFlight > 0) {
            inFlight--;
        }
        pump();
    }

    public int inFlight() {
        return inFlight;
    }

    public int remaining() {
        return waiting.size();
    }

    private void pump() {
        while (inFlight < maxInFlight && !waiting.isEmpty()) {
            Runnable task = waiting.pollFirst();
            inFlight++;
            try {
                task.run();
            } catch (RuntimeException error) {
                // Nothing is in flight to report back, so the slot was never really
                // occupied. Release it here or the queue stalls one slot per failure.
                inFlight--;
                throw error;
            }
        }
    }
}
