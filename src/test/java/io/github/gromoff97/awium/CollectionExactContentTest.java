package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.AttemptResult;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static java.util.stream.Stream.concat;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.Condition.PreservingCondition;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionTestRuntime.mismatch;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.ProbeContainers.Directional;
import static io.github.gromoff97.awium.ProbeContainers.ThrowingEquals;
import static io.github.gromoff97.awium.ProbeContainers.once;
import static io.github.gromoff97.awium.internal.AttemptResult.Satisfied;
import static io.github.gromoff97.awium.internal.AttemptResult.Unsatisfied;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionExactContentTest {

    private final FakeTime time = new FakeTime(0);

    @ParameterizedTest
    @MethodSource("pairs")
    void positiveAndNegativeExactConditionsAreComplements(
            PreservingCondition<? super List<String>> positive,
            PreservingCondition<? super List<String>> negative,
            List<String> matching, List<String> mismatching) {
        assertSame(matching, result(evaluate(positive, matching)));
        assertThat(mismatch(evaluate(negative, matching))).isNotBlank();
        assertSame(mismatching, result(evaluate(negative, mismatching)));
        assertThat(mismatch(evaluate(positive, mismatching))).isNotBlank();
    }

    @Test
    void orderedAndAnyOrderFormsKeepTheirDistinctSemantics() {
        assertStatus(containsExactly(List.of("a", "b")), List.of("b", "a"),
                Unsatisfied.class);
        assertStatus(containsExactlyInAnyOrder(List.of("a", "b")),
                List.of("b", "a"), Satisfied.class);
        assertStatus(containsExactly(List.of()), List.of(), Satisfied.class);
        assertStatus(containsExactly(List.of("a", "a")), List.of("a", "a"),
                Satisfied.class);
    }

    @TestFactory
    Stream<DynamicTest> exactContentRequiresEqualSizesWithoutStartingTraversal() {
        return Stream.of(List.<String>of(), List.of("a"), List.of("a", "b", "c"))
                .flatMap(elements -> exactMismatches("size " + elements.size(), () -> new ArrayList<>(elements) {
                    @Override
                    public Iterator<String> iterator() {
                        throw new AssertionError("different sizes must not start traversal");
                    }
                }));
    }

    @Test
    void onlyElementsRejectMissingAndExtraValuesButIgnoreDuplicates() {
        var condition = containsOnly(List.of("a", "b", "a"));
        assertStatus(condition, List.of(), Unsatisfied.class);
        assertStatus(condition, List.of("a"), Unsatisfied.class);
        assertStatus(condition, List.of("b"), Unsatisfied.class);
        assertStatus(condition, List.of("a", "b", "c"), Unsatisfied.class);
        assertStatus(condition, List.of("b", "a", "b"), Satisfied.class);
        assertStatus(containsOnly(List.of()), List.of(), Satisfied.class);
        assertStatus(containsOnly(List.of()), List.of("a"), Unsatisfied.class);
    }

    @TestFactory
    Stream<DynamicTest> exactComparisonRejectsChangesBetweenSizeAndTraversal() {
        var mutations = List.<Named<Consumer<ArrayList<String>>>>of(
                named("grow", values -> values.add("c")), named("shrink", List::removeLast));
        return mutations.stream().flatMap(mutation -> exactMismatches(mutation.getName(), () -> new ArrayList<>(List.of("a", "b")) {
            private final Runnable change = once(() -> mutation.getPayload().accept(this));

            @Override
            public Iterator<String> iterator() {
                change.run();
                return super.iterator();
            }
        }));
    }

    private static Stream<DynamicTest> exactMismatches(String scenario, Supplier<ArrayList<String>> values) {
        var expected = List.of("a", "b");
        return concat(
                Stream.<PreservingCondition<? super ArrayList<String>>>of(containsExactly(expected), containsExactlyInAnyOrder(expected))
                        .map(condition -> dynamicTest(scenario + " / " + condition.description(),
                                () -> assertInstanceOf(Unsatisfied.class, evaluate(condition, values.get())))),
                Stream.<PreservingCondition<? super ArrayList<String>>>of(
                        doesNotContainExactly(expected), doesNotContainExactlyInAnyOrder(expected))
                        .map(condition -> dynamicTest(scenario + " / " + condition.description(), () -> {
                            var actual = values.get();
                            assertSame(actual, result(evaluate(condition, actual)));
                        })));
    }

    @Test
    void exactEqualityIsActualFirstArrayAwareAndDoesNotHash() {
        Directional actual = new Directional(true);
        Directional expected = new Directional(false);
        assertStatus(containsExactlyInAnyOrder(asList(expected)), List.of(actual),
                Satisfied.class);
        assertEquals(1, actual.equalsCalls);

        String nil = null;
        assertStatus(containsExactly(asList(nil)), asList((String) null), Satisfied.class);
        assertStatus(containsExactly(java.util.Collections.singletonList(new int[] {1, 2})),
                List.<Object>of(new int[] {1, 2}), Satisfied.class);

        Directional onlyActual = new Directional(true);
        Directional onlyExpected = new Directional(false);
        assertStatus(containsOnly(asList(onlyExpected)), List.of(onlyActual), Satisfied.class);
        assertEquals(2, onlyActual.equalsCalls);
        assertEquals(0, onlyExpected.equalsCalls);
    }

    @Test
    void anyOrderMatchingIgnoresPermutationButRetainsMultiplicity() {
        var actual = new ArrayList<>(List.of("a", "b", "a"));
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1));

        for (var expected : List.of(List.of("a", "b", "a"), List.of("b", "a", "a"), List.of("a", "a", "b"))) {
            assertSame(actual, waiting.until(containsExactlyInAnyOrder(expected)));
        }
        var differentCounts = List.of("a", "b", "b");
        var failed = assertInstanceOf(AwaitResult.Failed.class,
                diagnostic(waiting).until(containsExactlyInAnyOrder(differentCounts)));
        assertEquals(TIMEOUT, failed.failure().reason());
        assertSame(actual, waiting.until(doesNotContainExactlyInAnyOrder(differentCounts)));
    }

    @Test
    void negativeExactConditionsDoNotHideTraversalOrEqualityFailures() {
        var iteratorCause = new IllegalStateException("iterator failed");
        var equalityCause = new IllegalStateException("equals failed");
        var brokenIterator = probeList(List.of("a"));
        when(brokenIterator.iterator()).thenThrow(iteratorCause);
        var brokenEquality = probeList(List.of(new ThrowingEquals(() -> { throw equalityCause; })));
        java.util.concurrent.Callable<List<String>> iteratorSource = () -> brokenIterator;
        java.util.concurrent.Callable<List<ThrowingEquals>> equalitySource = () -> brokenEquality;

        assertSame(iteratorCause, assertFailure(CONDITION_FAILED,
                () -> await(iteratorSource::call).usingTime(time, time).until(doesNotContainExactly(List.of("a")))).getCause());
        assertSame(equalityCause, assertFailure(CONDITION_FAILED,
                () -> await(equalitySource::call).usingTime(time, time).until(doesNotContainExactly(asList(new ThrowingEquals(() -> {}))))).getCause());
    }

    @Test
    void nullActualIsUnsatisfiedAndNullAggregateIsRejected() {
        AttemptResult<?> evaluation = evaluate(
                containsExactly(List.of("a")), null);
        assertInstanceOf(Unsatisfied.class, evaluation);
        assertThat(mismatch(evaluation)).isNotBlank();
        assertFalse(assertThrows(NullPointerException.class,
                () -> containsExactly(null)).getMessage().isBlank());
    }

    @Test
    void terminalDiagnosticsDoNotTraverseTheActualAgain() {
        var actual = probeList(List.of("a"));

        var failure = assertFailure(TIMEOUT,
                () -> await(() -> {
                    time.advanceNanos(2);
                    return actual;
                }).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2))
                        .until(containsExactly(List.of("b"))));

        verify(actual).iterator();
        assertThat(failure.getMessage()).contains("Expected: [b]");
    }

    private static <E> void assertStatus(PreservingCondition<? super List<E>> condition, List<? extends E> elements, Class<?> status) {
        List<E> actual = new ArrayList<>(elements);
        assertEquals(status, evaluate(condition, actual).getClass());
    }

    private static List<Arguments> pairs() {
        return List.of(
                argumentSet("ordered collection",
                        containsExactly(List.of("a", "a")),
                        doesNotContainExactly(List.of("a", "a")),
                        List.of("a", "a"), List.of("a", "b")),
                argumentSet("any-order collection",
                        containsExactlyInAnyOrder(List.of("a", "b")),
                        doesNotContainExactlyInAnyOrder(
                                List.of("a", "b")),
                        List.of("b", "a"), List.of("a", "a")));
    }

    private static <ELEMENT> List<ELEMENT> probeList(List<ELEMENT> elements) {
        List<ELEMENT> list = mock();
        when(list.size()).thenAnswer(ignored -> elements.size());
        when(list.isEmpty()).thenAnswer(ignored -> list.size() == 0);
        when(list.get(anyInt())).thenThrow(new AssertionError("get must not be called"));
        when(list.iterator()).thenAnswer(ignored -> elements.iterator());
        when(list.toString()).thenReturn("probe list");
        return list;
    }
}
