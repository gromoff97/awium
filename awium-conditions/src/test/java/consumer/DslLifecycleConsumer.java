package consumer;

import io.github.gromoff97.awium.dsl.AwaitCondition.CollectingCondition;
import io.github.gromoff97.awium.dsl.AwaitCondition.InputCondition;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import static io.github.gromoff97.awium.dsl.Await.await;
import static io.github.gromoff97.awium.dsl.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.firstElement;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.isNotNull;
import static io.github.gromoff97.awium.conditions.map.MapConditions.singleEntry;
import static io.github.gromoff97.awium.conditions.map.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.present;
import static java.time.Duration.ZERO;
import static java.time.Duration.ofNanos;
import static java.time.Duration.ofSeconds;

public final class DslLifecycleConsumer {
    private DslLifecycleConsumer() {}

    public static String collected(CollectingCondition<InputCondition<Object>> condition, boolean diagnostic) {
        if (diagnostic) return tryAwait(() -> "ready").until(condition).orThrow().getFirst();
        return await(() -> "ready").until(condition).getFirst();
    }

    public static List<BigDecimal> configuredSelections(BigDecimal value) {
        var values = new ArrayList<>(List.of(value));
        var entries = new LinkedHashMap<>(Map.of("key", value));
        return List.of(
                await(() -> values).every(ofNanos(1)).upTo(ofSeconds(1)).persisting(ZERO).until(firstElement),
                tryAwait(() -> values).every(ofNanos(1)).upTo(ofSeconds(1)).persisting(ZERO).until(firstElement).orThrow(),
                await(() -> Optional.of(value)).every(ofNanos(1)).upTo(ofSeconds(1)).persisting(ZERO).until(present),
                tryAwait(() -> Optional.of(value)).every(ofNanos(1)).upTo(ofSeconds(1)).persisting(ZERO).until(present).orThrow(),
                await(() -> entries).every(ofNanos(1)).upTo(ofSeconds(1)).persisting(ZERO).until(singleEntry).getValue(),
                tryAwait(() -> entries).every(ofNanos(1)).upTo(ofSeconds(1)).persisting(ZERO).until(singleEntry).orThrow().getValue(),
                await(() -> entries).every(ofNanos(1)).upTo(ofSeconds(1)).persisting(ZERO).until(valueFor("key")),
                tryAwait(() -> entries).every(ofNanos(1)).upTo(ofSeconds(1)).persisting(ZERO).until(valueFor("key")).orThrow());
    }

    public static List<String> callableSelections() {
        Callable<Optional<String>> optional = () -> Optional.of("ready");
        Callable<ArrayList<String>> collection = () -> new ArrayList<>(List.of("ready"));
        Callable<LinkedHashMap<String, Integer>> map = () -> new LinkedHashMap<>(Map.of("ready", 1));
        return List.of(
                await(optional::call).until(present), await(() -> optional.call()).until(present),
                tryAwait(optional::call).until(present).orThrow(), await(collection::call).until(firstElement),
                await(() -> collection.call()).until(firstElement), tryAwait(collection::call).until(firstElement).orThrow(),
                await(map::call).until(singleEntry).getKey(), await(() -> map.call()).until(singleEntry).getKey(),
                tryAwait(map::call).until(singleEntry).orThrow().getKey());
    }

    public static List<Integer> wildcardSelections(List<? extends Number> values,
            Map<? extends Number, ? extends CharSequence> entries) {
        var selected = await(() -> entries).until(singleEntry);
        return List.of(await(() -> values).until(firstElement).intValue(),
                tryAwait(() -> values).until(firstElement).orThrow().intValue(),
                selected.getKey().intValue(), selected.getValue().length(),
                await(() -> values).until(isNotNull).getFirst().intValue());
    }

    public static List<Integer> constructorLambdas() {
        return List.of(await(() -> new StringBuilder()).until(isNotNull).length(),
                await(() -> new ArrayList<String>()).until(isNotNull).size(),
                await(() -> new LinkedHashMap<String, Integer>()).until(isNotNull).size(),
                await(() -> Optional.empty()).until(isNotNull).isEmpty() ? 0 : 1);
    }
}
