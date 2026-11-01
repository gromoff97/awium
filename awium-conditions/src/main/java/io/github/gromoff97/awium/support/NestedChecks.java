package io.github.gromoff97.awium.support;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.execution.ConditionExecution;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.Unsatisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

public final class NestedChecks {
    private NestedChecks() {}

    public static <INPUT, VALUE, RESULT> Callable<AwaitAttempt<INPUT, RESULT>> evaluation(
            AwaitCondition extractionCondition,
            Supplier<? extends ConditionExecution<? super VALUE, ? extends RESULT>> evaluatorFactory, String selection) {
        return () -> {
            var extraction = ConditionExecution.<INPUT, VALUE>bind(extractionCondition);
            var evaluator = evaluatorFactory.get();
            return actual -> switch (extraction.probe(actual)) {
                case Satisfied<? extends VALUE> success -> {
                    var evaluated = evaluator.probeAt(success.value(), selection);
                    yield evaluated instanceof Unsatisfied<?> ? mismatch(selection, evaluated) : evaluated;
                }
                case Unsatisfied<?> failure -> unsatisfied(failure.mismatch(), failure.cause());
            };
        };
    }

    public static <RESULT> Unsatisfied<RESULT> mismatch(String mismatch, ConditionResult<?> evaluated) {
        if (!(evaluated instanceof Unsatisfied<?> failure)) return unsatisfied(mismatch);
        return unsatisfied(failure.mismatch() == null ? mismatch : mismatch + ": " + failure.mismatch(), failure.cause());
    }
}
