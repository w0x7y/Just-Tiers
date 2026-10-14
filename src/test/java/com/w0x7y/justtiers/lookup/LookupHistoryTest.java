package com.w0x7y.justtiers.lookup;

import com.w0x7y.justtiers.api.PlayerRef;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class LookupHistoryTest {

    @Test
    void startsEmpty() {
        assertEquals(List.of(), new LookupHistory().names());
    }

    @Test
    void theMostRecentLookupComesFirst() {
        LookupHistory history = new LookupHistory();
        history.record("Alpha");
        history.record("Bravo");
        history.record("Charlie");

        assertEquals(List.of("Charlie", "Bravo", "Alpha"), history.names());
    }

    @Test
    void aRepeatMovesToTheFrontUnderItsLatestCasing() {
        LookupHistory history = new LookupHistory();
        history.record("Alpha");
        history.record("Bravo");
        history.record("ALPHA");

        assertEquals(List.of("ALPHA", "Bravo"), history.names());
    }

    @Test
    void onlyTheEightMostRecentNamesAreKept() {
        LookupHistory history = new LookupHistory();
        for (int i = 1; i <= 10; i++) {
            history.record("Player" + i);
        }

        assertEquals(LookupHistory.CAPACITY, history.names().size());
        assertEquals("Player10", history.names().getFirst());
        assertEquals("Player3", history.names().getLast());
    }

    @Test
    void aRepeatAtCapacityEvictsNothing() {
        LookupHistory history = new LookupHistory();
        for (int i = 1; i <= LookupHistory.CAPACITY; i++) {
            history.record("Player" + i);
        }
        history.record("player1");

        assertEquals(LookupHistory.CAPACITY, history.names().size());
        assertEquals("player1", history.names().getFirst());
        assertEquals("Player2", history.names().getLast());
    }

    @Test
    void blankNamesAreIgnoredAndOthersAreTrimmed() {
        LookupHistory history = new LookupHistory();
        history.record(null);
        history.record("   ");
        history.record("  Alpha ");

        assertEquals(List.of("Alpha"), history.names());
    }

    @Test
    void theSnapshotDoesNotChangeWithLaterLookups() {
        LookupHistory history = new LookupHistory();
        history.record("Alpha");
        List<String> snapshot = history.names();
        history.record("Bravo");

        assertEquals(List.of("Alpha"), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add("Charlie"));
    }

    // --- only resolved identities are recorded ---

    @Test
    void aResolvedIdentityIsRecordedUnderItsOwnName() {
        LookupHistory history = new LookupHistory();
        CompletableFuture<Optional<PlayerRef>> resolution = new CompletableFuture<>();
        history.recordWhenResolved(resolution);
        assertEquals(List.of(), history.names(), "nothing before the identity is known");

        resolution.complete(Optional.of(new PlayerRef("CanonicalName", UUID.randomUUID())));

        assertEquals(List.of("CanonicalName"), history.names());
    }

    @Test
    void namesThatFailToResolveAreNotRecorded() {
        LookupHistory history = new LookupHistory();
        history.recordWhenResolved(CompletableFuture.completedFuture(Optional.empty()));
        history.recordWhenResolved(CompletableFuture.failedFuture(new IllegalStateException("offline")));

        assertEquals(List.of(), history.names());
    }

    // --- tab completion ---

    @Test
    void suggestionsCombineOnlineAndRecentNamesWithoutDuplicates() {
        LookupHistory history = new LookupHistory();
        history.record("Offline");
        history.record("onlinefriend");

        assertEquals(List.of("OnlineFriend", "Stranger", "Offline"),
                history.suggestions("", List.of("OnlineFriend", "Stranger")));
    }

    @Test
    void suggestionsMatchThePrefixWithoutCase() {
        LookupHistory history = new LookupHistory();
        history.record("Notch");
        history.record("Dinnerbone");

        assertEquals(List.of("NotchFan", "Notch"), history.suggestions("NO", List.of("NotchFan", "jeb_")));
        assertEquals(List.of(), history.suggestions("x", List.of("NotchFan")));
    }

    @Test
    void suggestionsWorkWithNoOnlinePlayers() {
        LookupHistory history = new LookupHistory();
        history.record("Alpha");
        history.record("Bravo");

        assertEquals(List.of("Bravo", "Alpha"), history.suggestions(null, List.of()));
    }
}
