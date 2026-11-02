package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.AwaitCondition.MapValueCondition;
import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SelectorLifecycleTest {
    @ParameterizedTest(name = "{displayName} [{index}] explained={0}")
    @ValueSource(booleans = {false, true})
    void elementSelectorsUseCustomRuns(boolean explained) throws Throwable {
        var definition = afterTwoObservations((List<String> actual) -> actual.getFirst());
        var selector = new SelectedCondition<List<String>>() {
            @Override public String description() { return definition.description(); }
            @Override public AttemptResult<?> probe(List<String> actual) throws Throwable { return definition.probe(actual); }
            @Override public Condition<List<String>, ?> newRun() {
                return definition.newRun();
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
            @Override public AttemptResult<?> probe(Map<String, String> actual) throws Throwable { return definition.probe(actual); }
            @Override public Condition<Map<String, String>, ?> newRun() {
                return definition.newRun();
            }
        };

        assertIndependentRuns(explained ? selector.because("key value") : selector, Map.of("key", "ready"));
    }

    private static <INPUT> void assertIndependentRuns(AwaitCondition selector, INPUT actual) throws Throwable {
        var first = AwaitAttempt.<INPUT>bind(selector);
        var second = AwaitAttempt.<INPUT>bind(selector);

        assertEquals(unsatisfied("condition is not yet complete"), first.probe(actual));
        assertEquals(unsatisfied("condition is not yet complete"), second.probe(actual));
        assertEquals(satisfied("ready"), first.probe(actual));
        assertEquals(satisfied("ready"), second.probe(actual));
    }

    private static <INPUT> Condition<INPUT, String> afterTwoObservations(Function<INPUT, String> extract) {
        return new Condition<>() {
            @Override public String description() { return "two observations"; }
            @Override public AttemptResult<String> probe(INPUT actual) {
                throw new AssertionError("the custom run must perform the check");
            }
            @Override public Condition<INPUT, String> newRun() {
                return new Condition<>() {
                    private int observations;

                    @Override public String description() { return "two observations"; }

                    @Override public AttemptResult<String> probe(INPUT actual) {
                        observations++;
                        var value = extract.apply(actual);
                        return observations == 2 ? satisfied(value) : unsatisfied("condition is not yet complete");
                    }
                };
            }
        };
    }
}
