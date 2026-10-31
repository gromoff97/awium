package io.github.gromoff97.awium.result;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.support.ConditionTestRuntime.diagnosticCondition;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AwaitResultTest {

    @Test
    void copiesConditionHistories() {
        var history = new ConditionHistory<String>(diagnosticCondition("ready", null), new TreeMap<>());
        var mutable = new ArrayList<>(List.of(history));

        var result = new AwaitResult.Succeeded<>(mutable, null);
        var failed = new AwaitResult.Failed<>(mutable,
                new AwaitFailure(AwaitFailure.Reason.TIMEOUT, "timeout", null, List.of()));
        mutable.clear();

        for (var outcome : List.of(result, failed)) {
            assertThat(outcome.conditions()).singleElement().isSameAs(history);
            assertThrows(UnsupportedOperationException.class, () -> outcome.conditions().clear());
        }
    }

    @Test
    void successMayReturnNull() {
        var result = new AwaitResult.Succeeded<String, String>(List.of(), null);
        assertNull(result.result());
        assertNull(result.orThrow());
    }

    @Test
    void retainsTheStructuredFailure() {
        var failure = new AwaitFailure(AwaitFailure.Reason.SOURCE_FAILED, "failed",
                new IllegalStateException("failed"), List.of());
        var result = new AwaitResult.Failed<String, String>(List.of(), failure);

        assertSame(failure, result.failure());
    }

    @Test
    void rejectsMissingFailure() {
        assertThrows(NullPointerException.class, () -> new AwaitResult.Failed<String, String>(List.of(), null));
    }

    @Test
    void requiresConditionHistories() {
        var failure = new AwaitFailure(AwaitFailure.Reason.TIMEOUT, "timeout", null, List.of());
        assertThrows(NullPointerException.class, () -> new AwaitResult.Succeeded<>(null, "ready"));
        assertThrows(NullPointerException.class, () -> new AwaitResult.Failed<>(null, failure));
    }

    @Test
    void rejectsNullHistoryEntries() {
        var failure = new AwaitFailure(AwaitFailure.Reason.TIMEOUT, "timeout", null, List.of());
        assertThrows(NullPointerException.class, () -> new AwaitResult.Succeeded<>(singletonList(null), "ready"));
        assertThrows(NullPointerException.class, () -> new AwaitResult.Failed<>(singletonList(null), failure));
    }
}
