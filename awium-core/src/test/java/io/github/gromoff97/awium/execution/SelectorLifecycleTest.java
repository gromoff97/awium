package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.AwaitCondition.MapValueCondition;
import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SelectorLifecycleTest {
    @ParameterizedTest(name = "{displayName} [{index}] explained={0}")
    @ValueSource(booleans = {false, true})
    void elementSelectorsUseCustomRuns(boolean explained) throws Throwable {
        var definition = afterTwoObservations((List<String> actual) -> actual.getFirst());
        var selector = new SelectedCondition<List<String>>() {
            @Override public String description() { return definition.description(); }
            @Override public ConditionResult<?> probe(List<String> actual) throws Throwable { return definition.probe(actual); }
            @Override public ConditionRun<List<String>, ?> newRun(AwaitCondition original) {
                return definition.newRun(original);
            }
        };

        assertIndependentRuns(explained ? selector.because("first value") : selector, List.of("ready"));
    }

    @ParameterizedTest(name = "{displayName} [{index}] explained={0}")
    @ValueSource(booleans = {false, true})
    void mapValueSelectorsUseCustomRuns(boolean explained) throws Throwable {
        var definition = afterTwoObservations((Map<String, String> actual) -> actual.get("key"));
        var selector = new MapValueCondition<Map<String, String>>() {
            @Override public String description() { return definition.description(); }
            @Override public ConditionResult<?> probe(Map<String, String> actual) throws Throwable { return definition.probe(actual); }
            @Override public ConditionRun<Map<String, String>, ?> newRun(AwaitCondition original) {
                return definition.newRun(original);
            }
        };

        assertIndependentRuns(explained ? selector.because("key value") : selector, Map.of("key", "ready"));
    }

    private static <INPUT> void assertIndependentRuns(AwaitCondition selector, INPUT actual) throws Throwable {
        var first = ConditionExecution.<INPUT, String>bind(selector);
        var second = ConditionExecution.<INPUT, String>bind(selector);

        assertEquals(unsatisfied("condition is not yet complete"), first.probe(actual));
        assertEquals(unsatisfied("condition is not yet complete"), second.probe(actual));
        assertEquals(satisfied("ready"), first.probe(actual));
        assertEquals(satisfied("ready"), second.probe(actual));
    }

    private static <INPUT> Condition<INPUT, String> afterTwoObservations(Function<INPUT, String> extract) {
        return new Condition<>() {
            @Override public String description() { return "two observations"; }
            @Override public ConditionResult<String> probe(INPUT actual) {
                throw new AssertionError("the custom run must perform the check");
            }
            @Override public ConditionRun<INPUT, String> newRun(AwaitCondition definition) {
                return new AbstractConditionRun<>(definition) {
                    private int observations;
                    private String value;

                    @Override public ConditionResult<?> probe(INPUT actual) {
                        observations++;
                        value = extract.apply(actual);
                        return satisfied(value);
                    }
                    @Override public boolean complete() { return observations == 2; }
                    @Override public String result() { return value; }
                };
            }
        };
    }
}
