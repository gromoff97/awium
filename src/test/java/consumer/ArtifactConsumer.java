package consumer;

import io.github.gromoff97.awium.StringConditions;
import io.github.gromoff97.awium.result.HistoryEntry;
import io.github.gromoff97.awium.result.AttemptStatus;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import static io.github.gromoff97.awium.Await.tryAwait;
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
        String optional = await(() -> Optional.of("ready")).until(present);
        String selected = await(() -> values).until(singleElement);
        int length = await(() -> length(text())).until(isNotNull);
        String narrowed = await((Callable<Object>) ArtifactConsumer::text).until(instanceOf(String.class));
        long[] time = {0};
        List<String> stages = await(ArtifactConsumer::text).usingTime(() -> time[0], nanos -> time[0] += nanos).every(ofNanos(1)).upTo(ofNanos(5)).until(captured(
                equalTo("ready"), contentEquals("ready")));
        boolean preserved = await(() -> values).until(asserted(actual -> {
            if (actual.size() != 1) throw new AssertionError("one value expected");
        })) == values;
        var result = tryAwait(() -> Optional.of("ready")).until(present);
        String captured = result.orThrow();
        Number mapped = await(() -> Map.<String, Number>of("key", 1)).until(valueFor("key"));
        Map.Entry<String, Integer> entry = await(() -> Map.of("key", 1)).until(singleEntry);
        String text = await(ArtifactConsumer::text).until(StringConditions.contains("read"));
        String regex = await(ArtifactConsumer::text).until(matchesRegex("ready"));
        return new Object[]{optional, selected, length, narrowed, stages, preserved, captured,
                mapped, entry, text, regex, observed(result.attempts().getFirst().last())};
    }

    private static String observed(HistoryEntry<Optional<String>> attempt) {
        return switch (attempt.result()) {
            case AttemptStatus.Satisfied ignored -> attempt.observed().orElseThrow();
            case AttemptStatus.Unsatisfied ignored -> attempt.observed().orElse(null);
            case AttemptStatus.Uncontrolled ignored -> null;
        };
    }

    private static String text() throws IOException { return "ready"; }
    private static int length(String value) throws IOException { return value.length(); }
}
