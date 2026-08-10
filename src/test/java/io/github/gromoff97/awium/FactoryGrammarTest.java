package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.conditioning.providers.ConditionProvider;
import io.github.gromoff97.awium.await.Await;
import io.github.gromoff97.awium.await.OptionalAwait;
import io.github.gromoff97.awium.await.StructuralAwait;
import io.github.gromoff97.awium.sources.CollectionSource;
import io.github.gromoff97.awium.sources.MapSource;
import io.github.gromoff97.awium.sources.OptionalSource;
import io.github.gromoff97.awium.sources.Source;

import io.github.gromoff97.awium.exceptions.*;

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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class FactoryGrammarTest {

    private static final Duration SECOND = Duration.ofSeconds(1);

    @Test
    void awiumOwnsExactlyFourStaticAwaitOverloads() {
        assertTrue(isPublic(Awium.class.getModifiers()));
        assertTrue(isFinal(Awium.class.getModifiers()));
        Constructor<?>[] constructors = Awium.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(isPrivate(constructors[0].getModifiers()));
        assertEquals(0, constructors[0].getParameterCount());

        List<Method> methods = List.of(Awium.class.getDeclaredMethods())
                .stream()
                .filter(method -> !method.isSynthetic())
                .toList();
        assertEquals(4, methods.size());
        assertTrue(methods.stream().allMatch(method -> method.getName().equals("await")));
        assertTrue(methods.stream().allMatch(method -> isPublic(method.getModifiers())
                && isStatic(method.getModifiers())));
    }

    @Test
    void everyFluentStateIsSealedAndClosedByFinalAdapters() {
        List<Class<?>> stages = List.of(
                Await.Until.class, Await.class,
                Await.AfterEvery.class, Await.AfterUpTo.class,
                OptionalAwait.Until.class, OptionalAwait.class,
                OptionalAwait.AfterEvery.class, OptionalAwait.AfterUpTo.class,
                StructuralAwait.Until.class, StructuralAwait.class,
                StructuralAwait.AfterEvery.class, StructuralAwait.AfterUpTo.class);

        assertEquals(12, stages.size());
        assertTrue(stages.stream().allMatch(Class::isSealed));
        for (Class<?> stage : stages) {
            for (Class<?> permitted : stage.getPermittedSubclasses()) {
                assertTrue(permitted.isSealed()
                        || isFinal(permitted.getModifiers()),
                        () -> stage + " permits open subtype " + permitted);
            }
        }
    }

    @Test
    void everyTypedNullSourceUsesTheExactValidationMessage() {
        assertNullSource(() -> Awium.await((Source<Object>) null));
        assertNullSource(() -> Awium.await((OptionalSource<Object>) null));
        assertNullSource(() -> Awium.await(
                (CollectionSource<Collection<Object>>) null));
        assertNullSource(() -> Awium.await(
                (MapSource<Map<Object, Object>>) null));
    }

    @Test
    void configurationOnlyBuildsStagesAndFailedBranchesDoNotPoisonTheOriginal() {
        AtomicInteger calls = new AtomicInteger();
        Await<String> initial = Awium.await(() -> {
            calls.incrementAndGet();
            return "value";
        });

        Await.AfterEvery<String> every = initial.every(Duration.ofSeconds(20));
        initial.upTo(SECOND);
        initial.stableFor(Duration.ZERO);
        assertEquals(0, calls.get());

        assertThrows(AwaitConfigurationConflictException.class,
                () -> every.upTo(Duration.ofSeconds(10)));
        String value = every.upTo(Duration.ofSeconds(30))
                .until(ConditionProvider.isNotNull);

        assertEquals("value", value);
        assertEquals(1, calls.get());
    }

    @Test
    void everyTerminalOverloadValidatesTheFinalConfigurationPair() {
        AtomicInteger sourceCalls = new AtomicInteger();
        Await.AfterEvery<String> object = Awium.await(
                (Source<String>) () -> {
                    sourceCalls.incrementAndGet();
                    return "value";
                }).every(Duration.ofSeconds(20));
        OptionalAwait.AfterEvery<String> optional = Awium.await(
                (OptionalSource<String>) () -> {
                    sourceCalls.incrementAndGet();
                    return Optional.of("value");
                }).every(Duration.ofSeconds(20));
        StructuralAwait.AfterEvery<List<String>> collection =
                Awium.await((CollectionSource<List<String>>) () -> {
                            sourceCalls.incrementAndGet();
                            return List.of("value");
                        }).every(Duration.ofSeconds(20));
        StructuralAwait.AfterEvery<Map<String, String>> map =
                Awium.await((MapSource<Map<String, String>>) () -> {
                            sourceCalls.incrementAndGet();
                            return Map.of("key", "value");
                        }).every(Duration.ofSeconds(20));
        Condition<String, String> selecting = ConditionProvider.condition(
                "selecting", Evaluation::satisfied);

        List<Executable> terminals = List.of(
                () -> object.until(ConditionProvider.isNotNull),
                () -> object.until(
                        ConditionProvider.isNotNull.because("preserving")),
                () -> object.until(selecting),
                () -> object.until(selecting.because("selecting")),
                () -> optional.until(ConditionProvider.present),
                () -> optional.until(
                        ConditionProvider.present.because("present")),
                () -> collection.until(ConditionProvider.nonEmpty),
                () -> collection.until(
                        ConditionProvider.nonEmpty.because("collection")),
                () -> map.until(ConditionProvider.nonEmpty),
                () -> map.until(ConditionProvider.nonEmpty.because("map")));

        for (Executable terminal : terminals) {
            assertThrows(AwaitConfigurationConflictException.class, terminal);
        }
        assertEquals(0, sourceCalls.get());
    }

    @Test
    void nullConditionWinsOverFinalConfigurationConflictForEveryOverload() {
        Await.AfterEvery<String> object = Awium
                .await((Source<String>) () -> "value")
                .every(Duration.ofSeconds(20));
        assertNullCondition(() -> object.until((PreservingCondition<String>) null));
        assertNullCondition(() -> object.until(
                (PreservingCondition.ExplainedCondition<String>) null));
        assertNullCondition(() -> object.until((Condition<String, String>) null));
        assertNullCondition(() -> object.until(
                (Condition.ExplainedCondition<String, String>) null));

        OptionalAwait.AfterEvery<String> optional = Awium
                .await((OptionalSource<String>) Optional::empty)
                .every(Duration.ofSeconds(20));
        assertNullCondition(() -> optional.until((PresentCondition) null));
        assertNullCondition(() -> optional.until((PresentCondition.ExplainedCondition) null));

        StructuralAwait.AfterEvery<Collection<String>> collection =
                Awium.await((CollectionSource<Collection<String>>) List::of)
                        .every(Duration.ofSeconds(20));
        assertNullCondition(() -> collection.until((StructuralCondition) null));
        assertNullCondition(() -> collection.until(
                (StructuralCondition.ExplainedCondition) null));

        StructuralAwait.AfterEvery<Map<String, String>> map =
                Awium.await((MapSource<Map<String, String>>) Map::of)
                        .every(Duration.ofSeconds(20));
        assertNullCondition(() -> map.until((StructuralCondition) null));
        assertNullCondition(() -> map.until((StructuralCondition.ExplainedCondition) null));
    }

    @Test
    void javaEvaluationOrderPrecedesFinalConfigurationValidation() {
        AtomicInteger conditionExpressions = new AtomicInteger();
        Await.AfterEvery<String> stage = Awium
                .await((Source<String>) () -> "value")
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

        assertThrows(AwaitConfigurationConflictException.class, () -> Awium
                .await((Source<String>) () -> "value")
                .every(Duration.ofSeconds(20))
                .upTo(Duration.ofSeconds(10))
                .until(countingCondition(conditionExpressions)));

        assertEquals(0, conditionExpressions.get());
    }

    @Test
    void terminalUsesTheSameConcreteImplementationBehindTheNarrowType() {
        Await.Until<String> terminal = Awium
                .await((Source<String>) () -> "value")
                .stableFor(Duration.ZERO);

        assertTrue(((Object) terminal) instanceof Await<?>);
    }

    private static Condition<String, String> countingCondition(AtomicInteger calls) {
        calls.incrementAndGet();
        return ConditionProvider.condition("value", Evaluation::satisfied);
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
