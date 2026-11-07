package consumer.value;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.dsl.TypedCondition;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.github.gromoff97.awium.dsl.Await.await;
import static io.github.gromoff97.awium.dsl.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.all;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.hasSize;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.captured;
import static io.github.gromoff97.awium.conditions.map.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.hasValue;
import static java.time.Duration.ofNanos;

public final class OpenConditionConsumer {
    private OpenConditionConsumer() {}

    public static List<Integer> nestedResults(Condition<CharSequence, Integer> condition) {
        int collection = await(() -> List.of("ready")).until(singleElement(condition));
        int optional = await(() -> Optional.of("ready")).until(hasValue(condition));
        int map = await(() -> Map.of("key", "ready")).until(valueFor("key", condition));
        return List.of(collection, optional, map);
    }

    public static List<Integer> terminalResults(Condition<CharSequence, Integer> condition) {
        int direct = await(() -> "ready").until(condition.because("DSL entry"));
        int recorded = tryAwait(() -> "ready").until(condition).orThrow();
        int core = io.github.gromoff97.awium.Await.await(() -> "ready").until(condition);
        AwaitResult<String, Number> widened = io.github.gromoff97.awium.Await.tryAwait(() -> "ready").until(condition);
        return List.of(direct, recorded, core, widened.orThrow().intValue());
    }

    public static AwaitResult<List<String>, List<List<String>>> adaptedStages(
            List<String> values, Condition<CharSequence, Integer> condition) {
        var check = TypedCondition.from(condition);
        return tryAwait(() -> values).every(ofNanos(1))
                .until(captured(hasSize(1), singleElement(condition), all(check)));
    }

    public static AwaitResult<List<String>, List<List<String>>> mixedStages(List<String> values, TypedCondition<CharSequence, Integer> condition) {
        return tryAwait(() -> values).every(ofNanos(1)).until(captured(hasSize(1), singleElement(condition), all(condition)));
    }
}
