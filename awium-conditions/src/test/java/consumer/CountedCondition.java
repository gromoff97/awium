package consumer;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.execution.ConditionRun;
import io.github.gromoff97.awium.result.AttemptResult;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;

public record CountedCondition(int observations) implements Condition<String, Integer> {
    @Override
    public String description() { return "observe text " + observations + " times"; }

    @Override
    public AttemptResult<Integer> probe(String actual) { return satisfied(actual.length()); }

    @Override
    public ConditionRun<String, Integer> newRun(AwaitCondition definition) {
        return new ConditionRun<>(definition) {
            private int count;
            private int totalLength;

            @Override
            public AttemptResult<Integer> probe(String actual) {
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
