package consumer;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.execution.AbstractConditionRun;
import io.github.gromoff97.awium.execution.ConditionRun;
import io.github.gromoff97.awium.result.ConditionResult;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;

public record CountedCondition(int observations) implements Condition<String, Integer> {
    @Override
    public String description() { return "observe text " + observations + " times"; }

    @Override
    public ConditionResult<Integer> probe(String actual) { return satisfied(actual.length()); }

    @Override
    public ConditionRun<String, Integer> newRun(AwaitCondition definition) {
        return new AbstractConditionRun<>(definition) {
            private int count;
            private int totalLength;

            @Override
            public ConditionResult<Integer> probe(String actual) {
                count++;
                totalLength += actual.length();
                return CountedCondition.this.probe(actual);
            }

            @Override
            public boolean complete() { return count >= observations; }

            @Override
            public Integer result() { return totalLength; }
        };
    }
}
