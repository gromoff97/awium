package io.github.gromoff97.awium.conditions;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.CollectionConditions.hasSize;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.rejection;
import static io.github.gromoff97.awium.conditions.Conditions.captured;
import static io.github.gromoff97.awium.conditions.Conditions.equalTo;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConditionHistoryFormattingTest {

    @Test
    void capturedHistoryLabelsEachConditionAndItsChanges() {
        var values = List.of("created", "pending", "pending", "paid").iterator();
        var result = tryAwait(values::next).every(ofNanos(1))
                .until(captured(equalTo("created"), equalTo("paid").because("payment accepted"))
                        .because("order lifecycle"));

        String report = result.toString();
        assertEquals(2, report.lines().filter(line -> line.startsWith("Condition ")).count());
        assertThat(report).contains(
                "Condition 1:",
                "Condition 2:",
                "Expected: \"created\"",
                "Expected: \"paid\"",
                "Because: payment accepted",
                "Through #3",
                "Observed now: \"pending\"");
    }

    @Test
    void returningToAnEarlierConditionRepeatsItsDefinitionInTheReport() {
        var ready = equalTo("ready");
        var values = List.of("ready", "retry", "ready").iterator();
        var result = tryAwait(values::next).every(ofNanos(1))
                .until(captured(ready, equalTo("retry"), ready));

        String report = result.toString();
        assertEquals(3, report.lines().filter(line -> line.startsWith("Condition ")).count());
        assertEquals(2, report.lines().filter(line -> line.contains("Expected: \"ready\"")).count());
        assertEquals(List.of("ready", "retry", "ready"), result.orThrow());
    }

    @Test
    void sizeMismatchesKeepTheSizeObservedDuringTheirProbe() {
        var values = new ArrayList<Integer>();
        var check = hasSize(4);
        var mismatches = new ArrayList<String>();
        for (int value : List.of(1, 3, 5)) {
            values.add(value);
            mismatches.add(rejection(check, values).mismatch());
        }
        values.add(99);
        assertThat(mismatches).containsExactly("collection size was 1", "collection size was 2", "collection size was 3");
    }
}
