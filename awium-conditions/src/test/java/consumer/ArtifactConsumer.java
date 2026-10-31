package consumer;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.ConditionResult;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.CollectionConditions.*;
import static io.github.gromoff97.awium.conditions.Conditions.*;
import static io.github.gromoff97.awium.conditions.MapConditions.*;
import static io.github.gromoff97.awium.conditions.OptionalConditions.*;
import static io.github.gromoff97.awium.conditions.StringConditions.*;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static java.time.Duration.ofMillis;

public final class ArtifactConsumer {
    public static Object[] run() {
        var values = List.of("ready");
        String optional = await(() -> Optional.of("ready")).until(present);
        String selected = await(() -> values).until(singleElement);
        int length = await(() -> length(text())).until(isNotNull);
        String narrowed = await((Callable<Object>) ArtifactConsumer::text).until(instanceOf(String.class));
        List<String> stages = await(ArtifactConsumer::text).every(ofMillis(1)).until(captured(
                equalTo("ready"), contentEquals("ready")));
        boolean preserved = await(() -> values).until(asserted(actual -> {
            assert actual.size() == 1 : "one value expected";
        })) == values;
        var result = tryAwait(() -> Optional.of("ready")).until(present);
        String captured = result.orThrow();
        Number mapped = await(() -> Map.<String, Number>of("key", 1)).until(valueFor("key"));
        Map.Entry<String, Integer> entry = await(() -> Map.of("key", 1)).until(singleEntry);
        String text = await(ArtifactConsumer::text).until(contains("read"));
        String regex = await(ArtifactConsumer::text).until(matchesRegex("ready"));
        Condition<CharSequence, Integer> custom = new Length().because("text has a length");
        int direct = await(ArtifactConsumer::text).until(custom);
        int nested = await(() -> values).until(singleElement(custom));
        var diagnostic = tryAwait(ArtifactConsumer::text).until(custom);
        List<String> customStages = await(ArtifactConsumer::text).every(ofMillis(1))
                .until(captured(custom, equalTo("ready")));
        assert diagnostic.conditions().getFirst().condition() == custom;
        assert custom.explanation().equals("text has a length");
        assert new Length().explanation() == null;
        assert customStages.equals(List.of("ready", "ready"));
        var counted = new CountedCondition(2).because("two observations");
        int lifecycle = await(ArtifactConsumer::text).every(ofMillis(1)).until(counted);
        int nestedLifecycle = await(() -> values).every(ofMillis(1)).until(singleElement(counted));
        return new Object[]{optional, selected, length, narrowed, stages, preserved, captured,
                mapped, entry, text, regex, observed(result.conditions().getFirst().attempts().firstEntry().getValue()), direct, nested, diagnostic.orThrow(), lifecycle, nestedLifecycle};
    }

public static final class Length implements Condition<CharSequence, Integer> {
        @Override
        public String description() { return "text length"; }

        @Override
        public ConditionResult<Integer> probe(CharSequence actual) { return satisfied(actual.length()); }
    }

    private static String observed(Attempt<Optional<String>> attempt) {
        return switch (attempt) {
            case Attempt.SatisfiedObservation(var optional) -> optional.orElse(null);
            case Attempt.UnsatisfiedObservation(var optional, var mismatch, var cause) -> optional.orElse(null);
            case Attempt.FailedAfterObservation(var optional, var operation, var cause, var location) -> optional.orElse(null);
            case Attempt.FailedBeforeObservation(var operation, var cause) -> null;
        };
    }

    private static String text() throws IOException { return "ready"; }
    private static int length(String value) throws IOException { return value.length(); }
}
