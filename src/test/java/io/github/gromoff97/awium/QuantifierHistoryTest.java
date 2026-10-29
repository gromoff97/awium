package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AttemptStatus;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.CollectionConditions.all;
import static io.github.gromoff97.awium.CollectionConditions.any;
import static io.github.gromoff97.awium.MapConditions.noValue;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class QuantifierHistoryTest {

    @Test
    void allRetainsProgressThroughAMutableCollectionWithoutRecheckingItsElements() {
        var time = new FakeTime(0);
        var values = new ArrayList<>(List.of(false, false, false));
        var check = new CountedCheck();
        int[] calls = {0};
        var result = tryAwait(() -> {
            if (calls[0] > 0) values.set(calls[0] - 1, true);
            calls[0]++;
            return values;
        }).usingTime(time, time).every(ofNanos(1)).until(all(check::test));

        assertSame(values, result.orThrow());
        assertEquals(4, calls[0]);
        assertEquals(9, check.calls);
        assertEquals(List.of(
                "not all collection elements matched (checked 1): value did not match",
                "not all collection elements matched (checked 2): value did not match",
                "not all collection elements matched (checked 3): value did not match"), mismatches(result));
    }

    @Test
    void anyRecordsHowManyElementsWereAvailableIncludingAnEmptyObservation() {
        var time = new FakeTime(0);
        var values = new ArrayList<Boolean>();
        var check = new CountedCheck();
        int[] calls = {0};
        var result = tryAwait(() -> {
            if (calls[0] > 0) values.add(calls[0] == 3);
            calls[0]++;
            return values;
        }).usingTime(time, time).every(ofNanos(1)).until(any(check::test));

        assertSame(values, result.orThrow());
        assertEquals(4, calls[0]);
        assertEquals(6, check.calls);
        assertEquals(List.of("no collection elements matched (checked 0)",
                "no collection elements matched (checked 1): value did not match",
                "no collection elements matched (checked 2): value did not match"), mismatches(result));
    }

    @Test
    void mapAbsenceChecksKeepTheirFirstMatchPositionInIterationOrder() {
        var time = new FakeTime(0);
        var keys = List.of("first", "second", "third");
        var values = new LinkedHashMap<String, Boolean>();
        keys.forEach(key -> values.put(key, true));
        var check = new CountedCheck();
        int[] calls = {0};
        var result = tryAwait(() -> {
            if (calls[0] > 0) values.put(keys.get(calls[0] - 1), false);
            calls[0]++;
            return values;
        }).usingTime(time, time).every(ofNanos(1)).until(noValue(check::test));

        assertSame(values, result.orThrow());
        assertEquals(4, calls[0]);
        assertEquals(9, check.calls);
        assertEquals(List.of("some map values matched (checked 1)",
                "some map values matched (checked 2)", "some map values matched (checked 3)"), mismatches(result));
    }

    private static List<String> mismatches(AwaitResult<?, ?> result) {
        var mismatches = new ArrayList<String>();
        for (var group : result.attempts()) {
            if (group.first().result() instanceof AttemptStatus.Unsatisfied status) {
                mismatches.add(status.mismatch());
            }
        }
        return mismatches;
    }

    private static final class CountedCheck {
        int calls;

        boolean test(Boolean value) {
            calls++;
            return value;
        }
    }
}
