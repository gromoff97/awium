package io.github.gromoff97.awium.conditions;

import consumer.ArtifactConsumer.Length;
import consumer.CountedCondition;
import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.execution.AbstractConditionRun;
import io.github.gromoff97.awium.execution.ConditionRun;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.CollectionConditions.all;
import static io.github.gromoff97.awium.conditions.CollectionConditions.hasSize;
import static io.github.gromoff97.awium.conditions.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.rejection;
import static io.github.gromoff97.awium.conditions.Conditions.captured;
import static io.github.gromoff97.awium.conditions.Conditions.equalTo;
import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class OpenConditionTest {

    @Test
    void typedUserConditionWorksWithReasonsSelectorsAndMixedStages() {
        var original = new Length();
        var reason = original.because("first");
        var replaced = reason.because("second");
        assertNull(original.explanation());
        assertEquals("first", reason.explanation());
        assertEquals("second", replaced.explanation());

        int direct = await(() -> "ready").until(replaced);
        int collection = await(() -> List.of("ready")).until(singleElement(replaced));
        int optional = await(() -> Optional.of("ready")).until(hasValue(replaced));
        int map = await(() -> Map.of("key", "ready")).until(valueFor("key", replaced));
        assertEquals(List.of(5, 5, 5, 5), List.of(direct, collection, optional, map));

        var values = List.of("ready");

        var result = tryAwait(() -> values).every(ofNanos(1)).until(captured(hasSize(1), singleElement(replaced), all(replaced)));
        assertEquals(List.of(values, values, values), result.orThrow());
        assertThat(result.conditions()).hasSize(3);
        assertSame(values, result.orThrow().getFirst());
    }

    @Test
    void customLifecycleSurvivesReasonsReuseNestingAndCaptured() {
        var condition = new CountedCondition(2).because("twice");
        var waiting = await(() -> "ready").every(ofNanos(1));
        assertEquals(10, waiting.until(condition));
        var result = tryAwait(() -> "ready").every(ofNanos(1)).until(condition);
        assertEquals(10, result.orThrow());
        assertSame(condition, result.conditions().getFirst().condition());
        assertEquals(2L, result.conditions().getFirst().attempts().lastKey());

        assertEquals(10, await(() -> List.of("ready")).every(ofNanos(1))
                .until(singleElement(condition)));
        var stages = tryAwait(() -> "ready").every(ofNanos(1)).until(captured(condition, equalTo("ready")));
        assertEquals(List.of("ready", "ready"), stages.orThrow());
        assertEquals(2L, stages.conditions().getFirst().attempts().lastKey());
        assertEquals(3L, stages.conditions().getLast().attempts().lastKey());

        var incomplete = new CountedCondition(100).because("still waiting");
        rejection(incomplete, "ready");
        assertEquals("observe text 100 times", incomplete.description());
        assertEquals("still waiting", incomplete.explanation());
    }

    @Test
    void finalVerdictIsUsedEvenWhenIndividualObservationsAreUnsatisfied() {
        var condition = new Condition<String, Integer>() {
            @Override public String description() { return "measure text"; }
            @Override public ConditionResult<Integer> probe(String actual) { return unsatisfied("observed"); }
            @Override public ConditionRun<String, Integer> newRun(AwaitCondition definition, boolean recording) {
                return new AbstractConditionRun<>(definition, recording) {
                    private Integer length;
                    @Override public ConditionResult<?> probe(String actual) {
                        length = actual.length();
                        return unsatisfied("observed");
                    }
                    @Override public boolean complete() { return length != null; }
                    @Override public Integer result() { return length; }
                };
            }
        };
        assertEquals(5, await(() -> "ready").until(condition));
        assertEquals(5, await(() -> List.of("ready")).until(singleElement(condition)));
    }

    @Test
    void statefulEvaluatorIsLazyAndIndependentAtEachUse() {
        int[] factories = {0};
        var condition = new Condition<String, String>() {
            @Override public String description() { return "second observation"; }
            @Override public ConditionResult<String> probe(String actual) { return satisfied(actual); }
            @Override public AwaitAttempt<String, String> newAttempt() {
                factories[0]++;
                int[] calls = {0};
                return actual -> ++calls[0] == 2 ? satisfied(actual) : unsatisfied();
            }
        }.because("fresh state");
        var sequence = captured(condition, condition);

        assertEquals(0, factories[0]);
        for (int run = 1; run <= 2; run++) {
            var result = tryAwait(() -> "ready").every(ofNanos(1)).until(sequence);
            assertEquals(List.of("ready", "ready"), result.orThrow());
            assertEquals(4L, result.conditions().getLast().attempts().lastKey());
            assertEquals(run * 2, factories[0]);
        }
    }
}
