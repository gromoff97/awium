package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.CollectionCondition;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitTimeoutException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitConditionEvaluationException;
import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.sources.Source.CollectionSource;

import java.util.Collection;
import java.util.List;

import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.await.AwaitTestAccess.timedCollectionAwait;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.SATISFIED;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.UNSATISFIED;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.empty;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.nonEmpty;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.sizeAtLeast;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.sizeAtMost;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.sizeExactly;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.sizeGreaterThan;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.sizeLessThan;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.sizeNotExactly;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CollectionSizeConditionsTest {

    @Test
    void conditionsEvaluateEverySizeRelation() {
        for (Case testCase : cases()) {
            var matching = new ProbeContainers.ProbeCollection<Object>(testCase.matchingSize());
            var mismatching = new ProbeContainers.ProbeCollection<Object>(testCase.mismatchingSize());

            Evaluation<?> satisfied = testCase.condition().evaluate(matching);
            assertEquals(SATISFIED, satisfied.status());
            assertSame(matching, satisfied.result());
            assertNull(satisfied.mismatch());
            assertUnsatisfied(testCase.condition().evaluate(mismatching));
            assertFalse(testCase.condition().description().isBlank());
            assertEquals(1, matching.sizeCalls);
            assertEquals(1, mismatching.sizeCalls);
        }
        assertEquals("collection size was 1",
                sizeExactly(2).evaluate(List.of("value")).mismatch());
    }

    @Test
    void nullCollectionRemainsUnsatisfied() {
        FakeTime time = new FakeTime(0);

        assertThrows(AwaitTimeoutException.class,
                () -> timedCollectionAwait((Source<Collection<?>>) () -> null,
                        defaults().withEvery(ofNanos(1)).withUpTo(ofNanos(2)),
                        time, time).until(empty));
    }

    @Test
    void diagnosticsUseCollectionVocabularyAndCapturedSize() {
        var actual = new ProbeContainers.ProbeCollection<Object>(1);
        FakeTime time = new FakeTime(0);

        AwaitTimeoutException failure = assertThrows(AwaitTimeoutException.class,
                () -> timedCollectionAwait(
                        (Source<ProbeContainers.ProbeCollection<Object>>) () -> {
                            time.advanceNanos(2);
                            return actual;
                        }, defaults().withEvery(ofNanos(1)).withUpTo(ofNanos(2)),
                        time, time).until(empty));

        assertTrue(failure.getMessage().contains("collection"));
        assertFalse(failure.getMessage().contains("map"));
        assertEquals(1, actual.sizeCalls);
    }

    @Test
    void throwingSizeIsTheExactFailFastConditionCause() {
        var cause = new IllegalStateException("collection size failed");
        var collection = new ProbeContainers.ProbeCollection<Object>(cause);

        assertSame(cause, assertThrows(AwaitConditionEvaluationException.class,
                () -> await((CollectionSource<ProbeContainers.ProbeCollection<Object>>)
                        () -> collection).until(nonEmpty)).getCause());
        assertEquals(1, collection.sizeCalls);
    }

    @Test
    void sizedFactoriesRejectNegativeBoundsAndAllowZero() {
        assertThrows(IllegalArgumentException.class, () -> sizeExactly(-1));
        assertDoesNotThrow(() -> sizeExactly(0));
    }

    @Test
    void nullConditionIsRejectedBeforeSourceRetrieval() {
        FakeTime time = new FakeTime(0);
        int[] sourceCalls = {0};
        Source<List<String>> source = () -> {
            sourceCalls[0]++;
            return List.of();
        };

        assertTrue(assertThrows(NullPointerException.class,
                () -> timedCollectionAwait(source, defaults(), time, time)
                        .until((CollectionCondition) null))
                .getMessage().contains("condition"));
        assertEquals(0, sourceCalls[0]);
    }

    private static void assertUnsatisfied(Evaluation<?> evaluation) {
        assertEquals(UNSATISFIED, evaluation.status());
        assertNull(evaluation.result());
        assertFalse(evaluation.mismatch().isBlank());
    }

    private static List<Case> cases() {
        return List.of(new Case(empty, 0, 1), new Case(nonEmpty, 1, 0),
                new Case(sizeExactly(2), 2, 1),
                new Case(sizeNotExactly(2), 1, 2),
                new Case(sizeGreaterThan(2), 3, 2),
                new Case(sizeAtLeast(2), 2, 1),
                new Case(sizeLessThan(2), 1, 2),
                new Case(sizeAtMost(2), 2, 3));
    }

    private record Case(CollectionCondition condition, int matchingSize,
            int mismatchingSize) {}
}
