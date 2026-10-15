package consumer;

import io.github.gromoff97.awium.AwaitAttempt;
import io.github.gromoff97.awium.AwaitResult;
import io.github.gromoff97.awium.ConditionResult;
import io.github.gromoff97.awium.Source;
import io.github.gromoff97.awium.StringConditions;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.MapConditions.*;
import static io.github.gromoff97.awium.OptionalConditions.*;
import static io.github.gromoff97.awium.StringConditions.*;
import static java.time.Duration.ofNanos;

public final class ArtifactConsumer {
    public static Object[] run() {
        var values = List.of("ready");
        String optional = await(() -> Optional.of("ready")).until(present.because("optional %s", "value"));
        String selected = await(() -> values).until(single.because("one value"));
        int length = await(() -> length(text())).until(isNotNull);
        String narrowed = await((Source<Object>) ArtifactConsumer::text).until(instanceOf(String.class));
        long[] time = {0};
        List<String> stages = await(ArtifactConsumer::text).usingTime(() -> time[0], nanos -> time[0] += nanos).every(ofNanos(1)).upTo(ofNanos(5)).until(
                equalTo("ready"), equalTo("ready"));
        boolean preserved = await(() -> values).until(asserted(actual -> {
            if (actual.size() != 1) throw new AssertionError("one value expected");
        })) == values;
        AwaitResult<Optional<String>, String> result = await(() -> Optional.of("ready")).tryUntil(present);
        String captured = switch (result) {
            case AwaitResult.Satisfied<Optional<String>, String> success -> success.result();
            case AwaitResult.Failed<Optional<String>, String> failure -> throw new AssertionError(failure.failure().reason());
        };
        Number mapped = await(() -> Map.<String, Number>of("key", 1)).until(valueFor("key").because("number"));
        Map.Entry<String, Integer> entry = await(() -> Map.of("key", 1)).until(singleEntry.because("entry"));
        String text = await(ArtifactConsumer::text).until(StringConditions.contains("read"));
        String regex = await(ArtifactConsumer::text).until(matchesRegex("ready"));
        return new Object[]{optional, selected, length, narrowed, stages, preserved, captured,
                mapped, entry, text, regex, observed(result.attempts().getFirst())};
    }

    private static String observed(AwaitAttempt<Optional<String>, String> attempt) {
        return switch (attempt.outcome()) {
            case AwaitAttempt.Outcome.Evaluated<Optional<String>, String> value -> {
                ConditionResult<? extends String> evaluation = value.evaluation();
                yield evaluation instanceof ConditionResult.Satisfied<? extends String> success
                        ? success.result() : value.observed().orElse(null);
            }
            default -> null;
        };
    }

    private static String text() throws IOException { return "ready"; }
    private static int length(String value) throws IOException { return value.length(); }
}
