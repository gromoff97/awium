package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.AwaitTestAccess.uncontrolled;
import static io.github.gromoff97.awium.CollectionConditions.all;
import static io.github.gromoff97.awium.CollectionConditions.any;
import static io.github.gromoff97.awium.CollectionConditions.none;
import static io.github.gromoff97.awium.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.Condition.PreservingCondition;
import static io.github.gromoff97.awium.Condition.Reference;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.MapConditions.allEntries;
import static io.github.gromoff97.awium.MapConditions.allKeys;
import static io.github.gromoff97.awium.MapConditions.allValues;
import static io.github.gromoff97.awium.MapConditions.anyKey;
import static io.github.gromoff97.awium.MapConditions.anyValue;
import static io.github.gromoff97.awium.MapConditions.hasEntry;
import static io.github.gromoff97.awium.MapConditions.hasNoEntry;
import static io.github.gromoff97.awium.MapConditions.noKey;
import static io.github.gromoff97.awium.MapConditions.noValue;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.ProbeContainers.EntryMap;
import static io.github.gromoff97.awium.result.Attempt.Operation.CONDITION;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.time.Duration.ofNanos;
import static java.util.AbstractMap.SimpleImmutableEntry;
import static java.util.Arrays.asList;
import static java.util.Map.Entry;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompositionDiagnosticsTest {

    private final FakeTime time = new FakeTime(0);

    @ParameterizedTest
    @ValueSource(strings = {"billing", "shipping"})
    void selectedMapValuesRetainTheLookupKeyAlongsideTheNestedExpectation(String key) {
        var check = valueFor(key, equalTo("ready"));
        var result = tryAwait(() -> Map.of("billing", "pending", "shipping", "pending"))
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).until(check);
        var failure = failure(result);

        assertThat(failure.message()).contains("Key: \"" + key + "\"", "Expected: \"ready\"");
        assertSame(check, result.conditions().getFirst().condition());
        assertThat(result.conditions().getFirst().toString()).contains("Key: \"" + key + "\"");
    }

    @Test
    void composedReferencesAreImmutableAndDoNotFormatKeysDuringPolling() {
        int[] renders = {0};
        Object key = new Object() {
            @Override public String toString() {
                renders[0]++;
                throw new IllegalStateException("key formatting failed");
            }
        };
        var check = valueFor(key, equalTo("ready")).because("selected value must be ready");
        var observations = List.of(Map.of(key, "pending"), Map.of(key, "ready")).iterator();
        var result = tryAwait(observations::next).usingTime(time, time).every(ofNanos(1)).until(check);

        assertEquals("ready", result.orThrow());
        assertEquals(0, renders[0]);
        assertSame(key, check.references().getFirst().value());
        assertEquals("ready", check.references().getLast().value());
        assertThrows(UnsupportedOperationException.class, () -> check.references().clear());
        String operands = check.references().toString();
        assertThat(operands).contains("Key=<unprintable>", "Expected=\"ready\"");
        String report = result.conditions().getFirst().toString();
        assertThat(report).contains(
                "Key: <unprintable>",
                "Expected: \"ready\"",
                "Because: selected value must be ready");
    }

    @Test
    void nestedLookupsKeepTheSourceConditionAcrossEveryFailureAndSuccess() {
        List<Map<String, Map<String, Object>>> values = List.of(Map.of(), Map.of("account", Map.of()),
                Map.of("account", Map.of("state", 1)), Map.of("account", Map.of("state", "ready")));
        var observations = values.iterator();
        var check = valueFor("account", valueFor("state", instanceOf(String.class)));
        var result = tryAwait(observations::next).usingTime(time, time).every(ofNanos(1)).until(check);

        assertEquals("ready", result.orThrow());
        assertEquals(4, attempts(result).size());
        assertSame(check, result.conditions().getFirst().condition());
        String report = result.conditions().getFirst().toString();
        assertThat(report).contains("Key: \"account\"", "Key: \"state\"", "value is an instance of java.lang.String");

    }

    @Test
    void nestedExtractionFailuresIdentifyTheCompletedOuterSelections() {
        var cause = new IllegalStateException("iterator failed");
        Collection<Collection<String>> brokenOuter = brokenCollection(cause);
        Collection<String> brokenInner = brokenCollection(cause);
        var check = singleElement(singleElement(instanceOf(String.class)));
        var outer = tryAwait(() -> brokenOuter).usingTime(time, time).until(check);
        var inner = tryAwait(() -> List.of(brokenInner)).usingTime(time, time).until(check);

        var outerFailure = failure(outer);
        var innerFailure = failure(inner);
        assertSame(cause, outerFailure.cause());
        assertSame(cause, innerFailure.cause());
        assertThat(outerFailure.message()).doesNotContain("Within:");
        assertThat(innerFailure.message()).contains("Within: single element");
        assertThat(attempts(inner).getFirst().toString()).contains("Within: single element");
    }

    @Test
    void nestedQuantifierErrorsKeepTheirPositionsAlongsideCapturedProgress() {
        var cause = new IllegalArgumentException("probe failed");
        Function<String, ConditionResult<String>> probe = mock();
        when(probe.apply(anyString())).thenAnswer(call -> satisfied(call.getArgument(0, String.class)));
        when(probe.apply("bad")).thenAnswer(ignored -> { throw cause; });
        var observed = Optional.of(List.of(List.of("ready"), List.of("bad", "unused")));
        var check = hasValue(all(all(condition("value is ready", probe::apply))));
        var result = tryAwait(() -> observed).usingTime(time, time).every(ofNanos(1))
                .until(captured(isNotNull, check));
        var failure = failure(result);
        var last = attempts(result).getLast();
        String location = "optional value → collection elements[1] → collection elements[0]";

        assertSame(cause, failure.cause());
        verify(probe, times(2)).apply(anyString());
        assertSame(check, result.conditions().getLast().condition());
        assertEquals(2L, result.conditions().getLast().attempts().firstKey());
        assertEquals(location, uncontrolled(CONDITION, last).location());
        assertThat(failure.message()).contains("Within: " + location);
        assertThat(last.toString()).contains("Within: " + location);
    }

    @Test
    void iteratorCreationFailureReportsNoCheckedCandidates() {
        var cause = new AssertionError("iterator failed");
        assertTraversalFailure(brokenCollection(cause), cause, 0);
    }

    @Test
    void traversalFailureReportsTheAlreadyCheckedCandidate() {
        var cause = new AssertionError("iterator failed");
        var observed = new AbstractCollection<String>() {
            @Override public int size() { return 2; }
            @Override public Iterator<String> iterator() {
                Iterator<String> iterator = mock();
                when(iterator.hasNext()).thenReturn(true);
                when(iterator.next()).thenAnswer(ignored -> {
                    doAnswer(call -> { throw cause; }).when(iterator).hasNext();
                    return "ready";
                }).thenAnswer(ignored -> {
                    doReturn(true).when(iterator).hasNext();
                    return "ready";
                });
                return iterator;
            }
            @Override public String toString() { return "unavailable collection"; }
        };
        assertTraversalFailure(observed, cause, 1);
    }

    private void assertTraversalFailure(Collection<String> observed, Throwable cause, int completed) {
        int[] calls = {0};
        var result = tryAwait(() -> observed).usingTime(time, time).until(all(
                condition("ready", value -> { calls[0]++; return satisfied(value); })));
        var failure = failure(result);

        assertEquals(completed, calls[0]);
        assertSame(cause, failure.cause());
        assertEquals(CONDITION_FAILED, failure.reason());
        assertThat(failure.message()).contains("Within: collection elements (checked " + completed + ")");
    }

    private static <ELEMENT> Collection<ELEMENT> brokenCollection(Throwable cause) {
        return new AbstractCollection<>() {
            @Override public int size() { return 1; }
            @Override public Iterator<ELEMENT> iterator() { return rethrow(cause); }
            @Override public String toString() { return "unavailable collection"; }
        };
    }

    @Test
    void failedMapViewReportsItsScopeWithoutInitializingTheNestedCheck() {
        var cause = new IllegalStateException("entries unavailable");
        var observed = new AbstractMap<String, String>() {
            @Override public Set<Entry<String, String>> entrySet() { throw cause; }
            @Override public String toString() { return "unavailable map"; }
        };
        var result = tryAwait(() -> observed).usingTime(time, time).until(allEntries(
                conditionPerWait("ready", () -> {
                    fail("nested check must not initialize before obtaining an entry");
                    return ignored -> satisfied(ignored);
                })));
        var failure = failure(result);
        var status = uncontrolled(CONDITION, attempts(result).getFirst());

        assertSame(cause, failure.cause());
        assertEquals("map entries (checked 0)", status.location());
        assertThat(failure.message()).contains("Within: " + status.location());
    }

    @ParameterizedTest
    @MethodSource("collectionQuantifierFailures")
    void collectionQuantifiersReportTheirScopeAndNamedCheck(PreservingCondition<Collection<?>> check, String scope, String mismatch) {
        var result = tryAwait(() -> List.of("pending")).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).until(check);
        assertQuantifierFailure(result, scope, mismatch);
    }

    private static List<Arguments> collectionQuantifierFailures() {
        var mismatch = condition("account is active", ignored -> unsatisfied());
        var match = condition("account is active", ignored -> satisfied(ignored));
        return List.of(
                arguments(all(mismatch), "all collection elements match", "not all collection elements matched (checked 1)"),
                arguments(any(mismatch), "some collection elements match", "no collection elements matched (checked 1)"),
                arguments(none(match), "no collection elements match", "some collection elements matched (checked 1)"));
    }

    @ParameterizedTest
    @MethodSource("mapQuantifierFailures")
    void mapQuantifiersReportTheirScopeAndNamedCheck(PreservingCondition<Map<?, ?>> check, String scope, String mismatch) {
        var result = tryAwait(() -> Map.of("account", "pending"))
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).until(check);
        assertQuantifierFailure(result, scope, mismatch);
    }

    private static List<Arguments> mapQuantifierFailures() {
        var mismatch = condition("account is active", ignored -> unsatisfied());
        var match = condition("account is active", ignored -> satisfied(ignored));
        return List.of(
                arguments(allEntries(mismatch), "all map entries match", "not all map entries matched (checked 1)"),
                arguments(hasEntry(mismatch), "some map entries match", "no map entries matched (checked 1)"),
                arguments(hasNoEntry(match), "no map entries match", "some map entries matched (checked 1)"),
                arguments(allKeys(mismatch), "all map keys match", "not all map keys matched (checked 1)"),
                arguments(anyKey(mismatch), "some map keys match", "no map keys matched (checked 1)"),
                arguments(noKey(match), "no map keys match", "some map keys matched (checked 1)"),
                arguments(allValues(mismatch), "all map values match", "not all map values matched (checked 1)"),
                arguments(anyValue(mismatch), "some map values match", "no map values matched (checked 1)"),
                arguments(noValue(match), "no map values match", "some map values matched (checked 1)"));
    }

    @Test
    void nestedQuantifiersReportBothScopesAndTheNamedCheck() {
        var mismatch = condition("account is active", ignored -> unsatisfied());
        var result = tryAwait(() -> List.of(List.of("pending")))
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2)).until(all(any(mismatch)));
        assertQuantifierFailure(result, "all collection elements match: some collection elements match",
                "not all collection elements matched (checked 1): no collection elements matched (checked 1)");
    }

    @Test
    void capturedQuantifiersReportTheFailedStageScopeAndNamedCheck() {
        var mismatch = condition("account is active", ignored -> unsatisfied());
        var result = tryAwait(() -> List.of("pending")).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2))
                .until(captured(isNotNull, all(mismatch)));
        assertQuantifierFailure(result, "all collection elements match", "not all collection elements matched (checked 1)");
    }

    private static void assertQuantifierFailure(AwaitResult<?, ?> result, String scope, String mismatch) {
        var failure = failure(TIMEOUT, result);
        String description = scope + ": account is active";
        assertThat(failure.message()).contains(description, mismatch);
        var last = attempts(result).getLast();
        var condition = result.conditions().getLast().condition();
        assertEquals(description, condition.description());
        assertEquals(mismatch, assertInstanceOf(UnsatisfiedObservation.class, last).mismatch());
    }

    @ParameterizedTest
    @MethodSource("missingOptionals")
    void missingOptionalKeepsItsCompleteDefinitionAndThePresenceMismatch(Optional<String> actual, String mismatch) {
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
        var selected = hasValue(equalTo("Bob"));
        var typed = hasValue(instanceOf(Integer.class));
        var selectedResult = diagnostic(waiting).until(selected);
        var typedResult = diagnostic(waiting).until(typed);

        for (var result : List.of(selectedResult, typedResult)) {
            var failure = failure(result);
            var entry = attempts(result).getLast();
            assertEquals(TIMEOUT, failure.reason());
            assertEquals(mismatch, assertInstanceOf(UnsatisfiedObservation.class, entry).mismatch());
        }
        assertSame(selected, selectedResult.conditions().getFirst().condition());
        assertSame(typed, typedResult.conditions().getFirst().condition());
        assertEquals(failure(selectedResult).message(),
                assertFailure(TIMEOUT, () -> waiting.until(selected)).getMessage());
        assertEquals(failure(typedResult).message(),
                assertFailure(TIMEOUT, () -> waiting.until(typed)).getMessage());
    }

    private static List<Arguments> missingOptionals() {
        return List.of(arguments(null, "optional was null"), arguments(Optional.empty(), "optional was empty"));
    }

    @Test
    void optionalHistoryKeepsItsDefinitionWhilePresenceAndValueChange() {
        var observations = List.of(Optional.<String>empty(), Optional.of("Alice"), Optional.of("Bob")).iterator();
        var nested = equalTo("Bob");
        var selected = hasValue(nested);
        var result = tryAwait(observations::next).usingTime(time, time).every(ofNanos(1)).until(selected);

        assertEquals("Bob", result.orThrow());
        assertEquals(3, attempts(result).size());
        assertSame(selected, result.conditions().getFirst().condition());
        assertEquals(nested.references(), result.conditions().getFirst().condition().references());
    }

    @ParameterizedTest
    @MethodSource("missingValues")
    void missingKeyAndNestedMismatchRetainBothExpectedOperands(Map<String, String> actual) {
        var result = tryAwait(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3)).until(
                valueFor("customerId", equalTo("Bob")));
        var failure = failure(TIMEOUT, result);
        assertThat(failure.message()).contains("Key: \"customerId\"", "Expected: \"Bob\"");
        assertEquals(1, attempts(result).size());
        assertEquals(3, result.conditions().getFirst().attempts().firstKey());
    }

    private static List<Map<String, String>> missingValues() {
        return asList(null, Map.of(), Map.of("customerId", "Alice"));
    }

    @Test
    void historyRetainsLookupAndNestedExpectationsAfterTheKeyAppears() {
        var observations = List.of(Map.<String, String>of(), Map.of("customerId", "Alice"), Map.of("customerId", "Bob")).iterator();
        var check = valueFor("customerId", equalTo("Bob"));
        var result = tryAwait(observations::next).usingTime(time, time).every(ofNanos(1)).until(check);

        assertEquals("Bob", result.orThrow());
        assertEquals(3, attempts(result).size());
        assertSame(check, result.conditions().getFirst().condition());
        assertEquals(List.of(new Reference<>("Key", "customerId"),
                new Reference<>("Expected", "Bob")), result.conditions().getFirst().condition().references());
    }

    @Test
    void missingKeyInsideCapturedKeepsLookupContextAndDoesNotCreateTheNestedSession() {
        var selected = valueFor("customerId", conditionPerWait("nested", () -> {
            throw new AssertionError("nested factory must not run before extraction succeeds");
        }));
        var failure = assertFailure(TIMEOUT, () -> await(() -> Map.<String, String>of())
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3)).until(captured(selected, selected)));
        assertThat(failure.getMessage()).contains("Key: \"customerId\"", "Sequence: captured 0/2");
    }

    @Test
    void lookupKeyIsFormattedOnlyOnFailureInsideTheProtectedRenderer() {
        var formattingFailure = new IllegalStateException("key formatting failed");
        int[] renders = {0};
        Object key = new Object() {
            @Override public String toString() {
                renders[0]++;
                throw formattingFailure;
            }
        };
        var selected = valueFor(key, equalTo("Bob"));
        assertEquals(0, renders[0]);
        var failure = assertFailure(TIMEOUT, () -> await(() -> Map.<Object, String>of())
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3)).until(selected));
        assertNull(failure.getCause());
        assertArrayEquals(new Throwable[]{formattingFailure}, failure.getSuppressed());
        assertEquals(1, renders[0]);
    }

    @ParameterizedTest
    @MethodSource("extractionFailures")
    void failedExtractionRetainsTheKeyAndDoesNotInitializeTheNestedCallback(Throwable cause) {
        var entry = new SimpleImmutableEntry<>("customerId", "Alice") {
            @Override public String getValue() { return rethrow(cause); }
        };
        var actual = new EntryMap<String, String>(List.of(entry));
        var selected = valueFor("customerId", conditionPerWait("nested", () -> {
            throw new AssertionError("nested factory must not run before extraction succeeds");
        })).because("customer lookup must succeed");
        var waiting = await(() -> actual);

        var result = diagnostic(waiting).until(selected);
        var failure = failure(CONDITION_FAILED, result);
        assertSame(cause, failure.cause());
        assertThat(failure.message()).contains("Key: \"customerId\"", "Because: customer lookup must succeed");
        uncontrolled(CONDITION, attempts(result).getFirst());
        assertEquals("customerId", result.conditions().getFirst().condition().references().getFirst().value());
        assertSame(cause, assertFailure(CONDITION_FAILED, () -> waiting.until(selected)).getCause());

        var captured = diagnostic(waiting).until(captured(selected, selected));
        var capturedOutcome = uncontrolled(CONDITION, attempts(captured).getFirst());
        assertSame(cause, capturedOutcome.cause());
        assertThat(captured.conditions().getLast().attempts()).isEmpty();
        assertEquals("customerId", captured.conditions().getFirst().condition().references().getFirst().value());
        assertEquals("customer lookup must succeed", captured.conditions().getFirst().condition().explanation());
    }

    private static List<Throwable> extractionFailures() {
        return List.of(new IllegalStateException("value failed"), new AssertionError("getter assertion"), new Throwable("getter throwable"));
    }
}
