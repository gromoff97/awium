package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition.ApplicableCondition;
import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.execution.ConditionExecution;
import io.github.gromoff97.awium.execution.ProbeFailure;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.Collection;
import java.util.List;

import static io.github.gromoff97.awium.execution.ConditionExecution.bind;
import static io.github.gromoff97.awium.execution.ProbeFailure.locatedFailure;
import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;

record QuantifiedCondition<ELEMENT, INPUT extends Collection<? extends ELEMENT>>(Quantifier quantifier,
        ApplicableCondition<? super ELEMENT, ?> nested) implements PreservingCondition<INPUT> {
    enum Quantifier {
        ALL("all", "not all"), ANY("some", "no"), NONE("no", "some");
        final String description;
        final String mismatch;
        Quantifier(String description, String mismatch) { this.description = description; this.mismatch = mismatch; }
        boolean stops(boolean matched) { return this == ALL ? !matched : matched; }
        boolean satisfied(boolean stopped) { return this == ANY ? stopped : !stopped; }
    }

    QuantifiedCondition { requireNonNull(nested, "condition must not be null"); }
    @Override public String description() { return quantifier.description + " collection elements match: " + nested.description(); }
    @Override public List<Reference<?>> references() { return nested.references(); }
    @Override public String explanation() { return nested.explanation(); }
    @Override public ConditionResult<?> probe(INPUT actual) throws Throwable { return newAttempt().probe(actual); }

    @Override public AwaitAttempt<INPUT, INPUT> newAttempt() {
        ConditionExecution<ELEMENT, ?> evaluator = bind(nested);
        return actual -> evaluate(actual, evaluator);
    }

    private ConditionResult<INPUT> evaluate(INPUT actual, ConditionExecution<ELEMENT, ?> evaluator) throws Throwable {
        if (actual == null) return unsatisfied("collection was null");
        ConditionResult<?> last = null;
        long checked = 0;
        try {
            for (ELEMENT value : actual) {
                checked++;
                last = evaluator.probeAt(value, "collection elements[" + (checked - 1) + "]");
                if (quantifier.stops(last instanceof Satisfied<?>)) {
                    return result(actual, quantifier.satisfied(true), checked, last);
                }
            }
        } catch (ProbeFailure failure) {
            throw failure;
        } catch (Throwable failure) {
            throw locatedFailure("collection elements (checked " + checked + ")", failure);
        }
        return result(actual, quantifier.satisfied(false), checked, last);
    }

    private ConditionResult<INPUT> result(INPUT actual, boolean successful, long checked, ConditionResult<?> last) {
        var diagnostic = last == null ? Diagnostic.EMPTY : last.diagnostic();
        if (successful) return satisfied(actual, diagnostic.withMismatch(null));
        String mismatch = quantifier.mismatch + " collection elements matched (checked " + checked + ")";
        return unsatisfied(diagnostic.withMismatch(
                diagnostic.mismatch() == null ? mismatch : mismatch + ": " + diagnostic.mismatch()),
                last == null ? null : last.cause());
    }
}
