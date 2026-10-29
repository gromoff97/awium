package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.HistoryEntry;
import java.util.ArrayDeque;
import java.util.List;

/** Counts attempts and retains the latest ones; zero counts only, and -1 retains all. */
final class AttemptHistory<OBSERVED> {

    private final int limit;
    private final ArrayDeque<HistoryEntry<OBSERVED>> attempts = new ArrayDeque<>();
    private long count;

    AttemptHistory(int limit) {
        if (limit < -1) {
            throw new IllegalArgumentException("history limit must be non-negative or -1");
        }
        this.limit = limit;
    }

    void add(HistoryEntry<OBSERVED> attempt) {
        attempts.addLast(attempt);
        count++;
        if (limit != -1 && attempts.size() > limit) {
            attempts.removeFirst();
        }
    }

    long count() {
        return count;
    }

    List<HistoryEntry<OBSERVED>> snapshot() {
        return List.copyOf(attempts);
    }
}
