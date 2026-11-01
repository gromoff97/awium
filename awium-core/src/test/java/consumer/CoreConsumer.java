package consumer;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AttemptResult;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.result.AwaitResult.Succeeded;
import static io.github.gromoff97.awium.result.AttemptResult.Satisfied;
import static io.github.gromoff97.awium.function.AwaitAttempt.bind;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static java.time.Duration.ofMillis;

public final class CoreConsumer {
    private CoreConsumer() {}

    public static Object[] run() throws Throwable {
        var length = new Length().because("name received");
        var successful = tryAwait(() -> "ready").until(length);
        var failed = tryAwait(() -> "").upTo(ofMillis(1)).until(length);
        AwaitAttempt<String, Number> attempt = bind(length);
        return new Object[]{await(() -> "ready").until(length), successful.orThrow(),
                successful.conditions().size(), failed.conditions().size(), failed instanceof Succeeded<?, ?>,
                ((Satisfied<? extends Number>) attempt.probeAt("ready", "name")).value()};
    }

    private static final class Length implements Condition<CharSequence, Integer> {
        @Override public String description() { return "nonempty name"; }
        @Override public AttemptResult<Integer> probe(CharSequence actual) {
            return actual.isEmpty() ? unsatisfied("name was empty") : satisfied(actual.length());
        }
    }
}
