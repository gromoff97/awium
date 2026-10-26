package consumer;

import io.github.gromoff97.awium.AttemptRecord;
import io.github.gromoff97.awium.AwaitResult;
import io.github.gromoff97.awium.ConditionResult;
import io.github.gromoff97.awium.StringConditions;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.MapConditions.*;
import static io.github.gromoff97.awium.OptionalConditions.*;
import static io.github.gromoff97.awium.StringConditions.*;
import static java.time.Duration.ofNanos;

public final class ArtifactConsumer {
    public static Object[] run() {
        var values = List.of("ready");
        String optional = ao(() -> Optional.of("ready")).until(present.because("optional %s", "value"));
        String selected = ac(() -> values).until(single.because("one value"));
        int length = a(() -> length(text())).until(isNotNull);
        String narrowed = await((Callable<Object>) ArtifactConsumer::text).until(instanceOf(String.class));
        long[] time = {0};
        List<String> stages = await(ArtifactConsumer::text).usingTime(() -> time[0], nanos -> time[0] += nanos).every(ofNanos(1)).upTo(ofNanos(5)).until(
                equalTo("ready").because("value"), contentEquals("ready").because("text"));
        boolean preserved = awaitCollection(() -> values).until(asserted(actual -> {
            if (actual.size() != 1) throw new AssertionError("one value expected");
        })) == values;
        var result = awaitOptional(() -> Optional.of("ready")).tryUntil(present);
        String captured = switch (result) {
            case AwaitResult.Satisfied(var attempts, var value) -> value;
            case AwaitResult.Failed(var attempts, var failure) -> throw new AssertionError(failure.reason());
        };
        Number mapped = am(() -> Map.<String, Number>of("key", 1)).until(valueFor("key").because("number"));
        Map.Entry<String, Integer> entry = awaitMap(() -> Map.of("key", 1)).until(singleEntry.because("entry"));
        String text = await(ArtifactConsumer::text).until(StringConditions.contains("read"));
        String regex = await(ArtifactConsumer::text).until(matchesRegex("ready"));
        return new Object[]{optional, selected, length, narrowed, stages, preserved, captured,
                mapped, entry, text, regex, observed(result.attempts().getFirst())};
    }

    private static String observed(AttemptRecord<Optional<String>, String> attempt) {
        return switch (attempt.outcome()) {
            case AttemptRecord.Outcome.Evaluated(var value, var evaluation) -> switch (evaluation) {
                case ConditionResult.Satisfied(var result) -> result;
                case ConditionResult.Unsatisfied(var mismatch, var cause) -> value.orElse(null);
            };
            default -> null;
        };
    }

    private static String text() throws IOException { return "ready"; }
    private static int length(String value) throws IOException { return value.length(); }
}
