package io.github.gromoff97.assertility;

import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isPrivate;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class FactoryGrammarTest {

    private static final Duration SECOND = Duration.ofSeconds(1);

    @Test
    void assertilityOwnsExactlyFiveStaticAwaitOverloads() {
        assertTrue(isPublic(Assertility.class.getModifiers()));
        assertTrue(isFinal(Assertility.class.getModifiers()));
        Constructor<?>[] constructors = Assertility.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(isPrivate(constructors[0].getModifiers()));
        assertEquals(0, constructors[0].getParameterCount());

        List<Method> methods = List.of(Assertility.class.getDeclaredMethods())
                .stream()
                .filter(method -> !method.isSynthetic())
                .toList();
        assertEquals(5, methods.size());
        assertTrue(methods.stream().allMatch(method -> method.getName().equals("await")));
        assertTrue(methods.stream().allMatch(method -> isPublic(method.getModifiers())
                && isStatic(method.getModifiers())));
    }

    @Test
    void everyFluentStateIsSealedAndClosedByFinalAdapters() {
        List<Class<?>> stages = List.of(
                ObjectUntil.class, ObjectAwait.class,
                ObjectAwait.AfterEvery.class, ObjectAwait.AfterUpTo.class,
                OptionalUntil.class, OptionalAwait.class,
                OptionalAwait.AfterEvery.class, OptionalAwait.AfterUpTo.class,
                CollectionUntil.class, CollectionAwait.class,
                CollectionAwait.AfterEvery.class, CollectionAwait.AfterUpTo.class,
                SequencedCollectionUntil.class, SequencedCollectionAwait.class,
                SequencedCollectionAwait.AfterEvery.class,
                SequencedCollectionAwait.AfterUpTo.class,
                MapUntil.class, MapAwait.class,
                MapAwait.AfterEvery.class, MapAwait.AfterUpTo.class);

        assertEquals(20, stages.size());
        assertTrue(stages.stream().allMatch(Class::isSealed));
        for (Class<?> stage : stages) {
            for (Class<?> permitted : stage.getPermittedSubclasses()) {
                assertTrue(permitted.isSealed()
                        || (!isPublic(permitted.getModifiers())
                        && isFinal(permitted.getModifiers())),
                        () -> stage + " permits open subtype " + permitted);
            }
        }
    }

    @Test
    void everyTypedNullSourceUsesTheExactValidationMessage() {
        assertNullSource(() -> Assertility.await((AwaitSources.Source<Object>) null));
        assertNullSource(() -> Assertility.await(
                (AwaitSources.OptionalSource<Object>) null));
        assertNullSource(() -> Assertility.await(
                (AwaitSources.CollectionSource<Object, Collection<Object>>) null));
        assertNullSource(() -> Assertility.await(
                (AwaitSources.SequencedCollectionSource<Object,
                        SequencedCollection<Object>>) null));
        assertNullSource(() -> Assertility.await(
                (AwaitSources.MapSource<Object, Object, Map<Object, Object>>) null));
    }

    @Test
    void configurationOnlyBuildsStagesAndFailedBranchesDoNotPoisonTheOriginal() {
        AtomicInteger calls = new AtomicInteger();
        ObjectAwait<String> initial = Assertility.await(() -> {
            calls.incrementAndGet();
            return "value";
        });

        ObjectAwait.AfterEvery<String> every = initial.every(Duration.ofSeconds(20));
        initial.upTo(SECOND);
        initial.stableFor(Duration.ZERO);
        assertEquals(0, calls.get());

        assertThrows(AwaitConfigurationConflictException.class,
                () -> every.upTo(Duration.ofSeconds(10)));
        String value = every.upTo(Duration.ofSeconds(30))
                .until(AwaitConditions.isNotNull);

        assertEquals("value", value);
        assertEquals(1, calls.get());
    }

    @Test
    void everyTerminalOverloadValidatesTheFinalConfigurationPair() {
        AtomicInteger sourceCalls = new AtomicInteger();
        ObjectAwait.AfterEvery<String> object = Assertility.await(
                (AwaitSources.Source<String>) () -> {
                    sourceCalls.incrementAndGet();
                    return "value";
                }).every(Duration.ofSeconds(20));
        OptionalAwait.AfterEvery<String> optional = Assertility.await(
                (AwaitSources.OptionalSource<String>) () -> {
                    sourceCalls.incrementAndGet();
                    return Optional.of("value");
                }).every(Duration.ofSeconds(20));
        CollectionAwait.AfterEvery<String, List<String>> collection =
                Assertility.await((AwaitSources.CollectionSource<String,
                        List<String>>) () -> {
                            sourceCalls.incrementAndGet();
                            return List.of("value");
                        }).every(Duration.ofSeconds(20));
        MapAwait.AfterEvery<String, String, Map<String, String>> map =
                Assertility.await((AwaitSources.MapSource<String, String,
                        Map<String, String>>) () -> {
                            sourceCalls.incrementAndGet();
                            return Map.of("key", "value");
                        }).every(Duration.ofSeconds(20));
        Condition<String, String> selecting = AwaitConditions.condition(
                "selecting", Evaluation::satisfied);

        List<Executable> terminals = List.of(
                () -> object.until(AwaitConditions.isNotNull),
                () -> object.until(
                        AwaitConditions.isNotNull.because("preserving")),
                () -> object.until(selecting),
                () -> object.until(selecting.because("selecting")),
                () -> optional.until(AwaitConditions.present),
                () -> optional.until(
                        AwaitConditions.present.because("present")),
                () -> collection.until(AwaitConditions.nonEmpty),
                () -> collection.until(
                        AwaitConditions.nonEmpty.because("collection")),
                () -> map.until(AwaitConditions.nonEmpty),
                () -> map.until(AwaitConditions.nonEmpty.because("map")));

        for (Executable terminal : terminals) {
            assertThrows(AwaitConfigurationConflictException.class, terminal);
        }
        assertEquals(0, sourceCalls.get());
    }

    @Test
    void nullConditionWinsOverFinalConfigurationConflictForEveryOverload() {
        ObjectAwait.AfterEvery<String> object = Assertility
                .await((AwaitSources.Source<String>) () -> "value")
                .every(Duration.ofSeconds(20));
        assertNullCondition(() -> object.until((PreservingCondition<String>) null));
        assertNullCondition(() -> object.until(
                (ExplainedPreservingCondition<String>) null));
        assertNullCondition(() -> object.until((Condition<String, String>) null));
        assertNullCondition(() -> object.until(
                (ExplainedCondition<String, String>) null));

        OptionalAwait.AfterEvery<String> optional = Assertility
                .await((AwaitSources.OptionalSource<String>) Optional::empty)
                .every(Duration.ofSeconds(20));
        assertNullCondition(() -> optional.until((Present) null));
        assertNullCondition(() -> optional.until((ExplainedPresent) null));

        CollectionAwait.AfterEvery<String, Collection<String>> collection =
                Assertility.await((AwaitSources.CollectionSource<String,
                        Collection<String>>) List::of).every(Duration.ofSeconds(20));
        assertNullCondition(() -> collection.until((StructuralCondition) null));
        assertNullCondition(() -> collection.until(
                (ExplainedStructuralCondition) null));

        SequencedCollectionAwait.AfterEvery<String, SequencedCollection<String>>
                sequenced = Assertility.await(
                        (AwaitSources.SequencedCollectionSource<String,
                                SequencedCollection<String>>) List::of)
                        .every(Duration.ofSeconds(20));
        assertNullCondition(() -> sequenced.until((StructuralCondition) null));
        assertNullCondition(() -> sequenced.until(
                (ExplainedStructuralCondition) null));

        MapAwait.AfterEvery<String, String, Map<String, String>> map =
                Assertility.await((AwaitSources.MapSource<String, String,
                        Map<String, String>>) Map::of).every(Duration.ofSeconds(20));
        assertNullCondition(() -> map.until((StructuralCondition) null));
        assertNullCondition(() -> map.until((ExplainedStructuralCondition) null));
    }

    @Test
    void javaEvaluationOrderPrecedesFinalConfigurationValidation() {
        AtomicInteger conditionExpressions = new AtomicInteger();
        ObjectAwait.AfterEvery<String> stage = Assertility
                .await((AwaitSources.Source<String>) () -> "value")
                .every(Duration.ofSeconds(20));
        RuntimeException expected = new RuntimeException("factory failed");

        RuntimeException actual = assertThrows(RuntimeException.class,
                () -> stage.until(throwingCondition(conditionExpressions, expected)));

        assertEquals(expected, actual);
        assertEquals(1, conditionExpressions.get());
    }

    @Test
    void receiverConflictPreventsConditionExpressionEvaluation() {
        AtomicInteger conditionExpressions = new AtomicInteger();

        assertThrows(AwaitConfigurationConflictException.class, () -> Assertility
                .await((AwaitSources.Source<String>) () -> "value")
                .every(Duration.ofSeconds(20))
                .upTo(Duration.ofSeconds(10))
                .until(countingCondition(conditionExpressions)));

        assertEquals(0, conditionExpressions.get());
    }

    @Test
    void terminalAdapterCannotBeCastBackToAConfigurationState() {
        ObjectUntil<String> terminal = Assertility
                .await((AwaitSources.Source<String>) () -> "value")
                .stableFor(Duration.ZERO);

        assertThrows(ClassCastException.class, () -> {
            ObjectAwait<String> ignored = (ObjectAwait<String>) terminal;
        });
        assertFalse(terminal instanceof ObjectAwait<?>);
    }

    private static Condition<String, String> countingCondition(AtomicInteger calls) {
        calls.incrementAndGet();
        return AwaitConditions.condition("value", Evaluation::satisfied);
    }

    private static Condition<String, String> throwingCondition(
            AtomicInteger calls, RuntimeException failure) {
        calls.incrementAndGet();
        throw failure;
    }

    private static void assertNullSource(Executable action) {
        NullPointerException failure = assertThrows(NullPointerException.class, action);
        assertEquals("source must not be null", failure.getMessage());
    }

    private static void assertNullCondition(Executable action) {
        NullPointerException failure = assertThrows(NullPointerException.class, action);
        assertEquals("condition must not be null", failure.getMessage());
    }
}
