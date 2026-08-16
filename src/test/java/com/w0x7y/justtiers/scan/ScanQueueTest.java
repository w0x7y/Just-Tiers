package com.w0x7y.justtiers.scan;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScanQueueTest {

    @Test
    void runsUpToTheCapImmediatelyAndHoldsTheRest() {
        List<String> started = new ArrayList<>();
        ScanQueue queue = new ScanQueue(2);
        for (int i = 0; i < 5; i++) {
            String name = "task" + i;
            queue.submit(() -> started.add(name));
        }

        assertEquals(List.of("task0", "task1"), started);
        assertEquals(2, queue.inFlight());
        assertEquals(3, queue.remaining());
    }

    @Test
    void aCompletionStartsExactlyOneWaiter() {
        List<String> started = new ArrayList<>();
        ScanQueue queue = new ScanQueue(1);
        queue.submit(() -> started.add("a"));
        queue.submit(() -> started.add("b"));
        queue.submit(() -> started.add("c"));

        queue.completed();
        assertEquals(List.of("a", "b"), started);
        assertEquals(1, queue.inFlight());
    }

    @Test
    void drainsFully() {
        List<String> started = new ArrayList<>();
        ScanQueue queue = new ScanQueue(3);
        for (int i = 0; i < 10; i++) {
            String name = "task" + i;
            queue.submit(() -> started.add(name));
        }
        for (int i = 0; i < 10; i++) {
            queue.completed();
        }

        assertEquals(10, started.size());
        assertEquals(0, queue.inFlight());
        assertEquals(0, queue.remaining());
    }

    @Test
    void aTaskThatThrowsFreesItsSlotSoTheQueueCannotStall() {
        // The session calls completed() from the answer callback, so a task that blows up
        // on submission must not leave the slot occupied forever.
        ScanQueue queue = new ScanQueue(1);
        assertThrows(RuntimeException.class, () -> queue.submit(() -> {
            throw new RuntimeException("boom");
        }));
        assertEquals(0, queue.inFlight());

        List<String> started = new ArrayList<>();
        queue.submit(() -> started.add("next"));
        assertEquals(List.of("next"), started);
    }

    @Test
    void completingMoreThanWasStartedIsHarmless() {
        ScanQueue queue = new ScanQueue(2);
        queue.completed();
        queue.completed();
        assertEquals(0, queue.inFlight());
    }

    @Test
    void theCapMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new ScanQueue(0));
    }
}
