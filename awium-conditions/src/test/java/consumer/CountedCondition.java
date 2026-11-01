package consumer;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.AttemptResult;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

public record CountedCondition(int observations) implements Condition<String, Integer> {
    @Override
    public String description() { return "observe text " + observations + " times"; }

    @Override
    public AttemptResult<Integer> probe(String actual) { return satisfied(actual.length()); }

    @Override
    public Condition<String, Integer> newRun() {
        return new Condition<>() {
            private int count;
            private int totalLength;

            @Override
            public String description() { return CountedCondition.this.description(); }

            @Override
            public AttemptResult<Integer> probe(String actual) {
                count++;
                totalLength += actual.length();
                return count >= observations ? satisfied(totalLength)
                        : unsatisfied("condition is not yet complete");
            }
        };
    }
}
