package io.github.gromoff97.awium.support;

import io.github.gromoff97.awium.execution.ConditionExecution;
import io.github.gromoff97.awium.execution.ProbeFailure;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.function.Function;

import static io.github.gromoff97.awium.execution.ProbeFailure.locatedFailure;
import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

public enum Quantifier {
    ALL("all", "not all"), ANY("some", "no"), NONE("no", "some");

    private final String descriptionPrefix;
    private final String mismatchPrefix;

    Quantifier(String descriptionPrefix, String mismatchPrefix) {
        this.descriptionPrefix = descriptionPrefix;
        this.mismatchPrefix = mismatchPrefix;
    }

    public String description(String elements) {
        return descriptionPrefix + " " + elements + " match";
    }

    public String mismatch(String elements) {
        return mismatchPrefix + " " + elements + " matched";
    }

    private boolean stops(boolean matched) {
        return switch (this) {
            case ALL -> !matched;
            case ANY, NONE -> matched;
        };
    }

    private boolean isSatisfied(boolean stopped) {
        return switch (this) {
            case ALL, NONE -> !stopped;
            case ANY -> stopped;
        };
    }

    public <INPUT, ELEMENT> ConditionResult<INPUT> probe(
            INPUT actual, Function<? super INPUT, ? extends Iterable<? extends ELEMENT>> elements,
            ConditionExecution<? super ELEMENT, ?> evaluator,
            String mismatch, String elementsDescription) throws Throwable {
        ConditionResult<?> last = null;
        long checked = 0;
        try {
            for (ELEMENT value : elements.apply(actual)) {
                checked++;
                var evaluated = evaluator.probeAt(value, elementsDescription + "[" + (checked - 1) + "]");
                last = evaluated;
                if (stops(evaluated instanceof Satisfied<?>)) {
                    return quantifiedResult(actual, isSatisfied(true), mismatch, checked, evaluated);
                }
            }
        } catch (ProbeFailure failure) {
            throw failure;
        } catch (Throwable failure) {
            throw locatedFailure(elementsDescription + " (checked " + checked + ")", failure);
        }
        return quantifiedResult(actual, isSatisfied(false), mismatch, checked, last);
    }

    private static <INPUT> ConditionResult<INPUT> quantifiedResult(INPUT actual, boolean successful,
            String mismatch, long checked, ConditionResult<?> evaluated) {
        return successful ? satisfied(actual)
                : NestedChecks.mismatch(mismatch + " (checked " + checked + ")", evaluated);
    }

}
