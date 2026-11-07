package consumer.value;

import io.github.gromoff97.awium.result.AwaitResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.github.gromoff97.awium.dsl.Await.await;
import static io.github.gromoff97.awium.dsl.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.elementAt;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.firstElement;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.lastElement;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.captured;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.equalTo;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.in;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.notEqualTo;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.notIn;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.notSameAs;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.sameAs;
import static io.github.gromoff97.awium.conditions.map.MapConditions.entryFor;
import static io.github.gromoff97.awium.conditions.map.MapConditions.singleEntry;
import static io.github.gromoff97.awium.conditions.map.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.present;
import static java.time.Duration.ofNanos;

public final class ComparisonConsumer {
    private ComparisonConsumer() {}

    public static List<Number> values(Number actual) {
        var waiting = await(() -> actual).every(ofNanos(1));
        var results = new ArrayList<Number>();
        for (var condition : List.of(equalTo(1000), notEqualTo(0), sameAs(actual),
                notSameAs(0), in(List.of(1000, 2)), notIn(List.of(0, -1)))) {
            var check = condition.because("numeric identity must be preserved");
            Number value = waiting.until(check);
            AwaitResult<Number, Number> result = tryAwait(() -> actual).until(check);
            List<Number> captured = waiting.until(captured(check, check, check));
            AwaitResult<Number, List<Number>> recorded = tryAwait(() -> actual).every(ofNanos(1)).until(captured(check, check, check));
            results.add(value);
            results.add(result.orThrow());
            results.addAll(captured);
            results.addAll(recorded.orThrow());
        }
        return results;
    }

    public static List<Number> optionalValues(Number actual) {
        var waiting = await(() -> Optional.of(actual)).every(ofNanos(1));
        var check = hasValue(equalTo(1)).because("optional contains the expected number");
        Number value = waiting.until(check);
        AwaitResult<Optional<Number>, Number> result = tryAwait(() -> Optional.of(actual)).until(check);
        List<Optional<Number>> captured = waiting.until(captured(present, check, check));
        AwaitResult<Optional<Number>, List<Optional<Number>>> recorded = tryAwait(() -> Optional.of(actual))
                .every(ofNanos(1)).until(captured(check, check, check));
        var results = new ArrayList<>(List.of(value, result.orThrow()));
        captured.forEach(optional -> results.add(optional.orElseThrow()));
        recorded.orThrow().forEach(optional -> results.add(optional.orElseThrow()));
        return results;
    }

    public static List<Number> collectionElements(Number actual) {
        var waiting = await(() -> List.of(actual)).every(ofNanos(1));
        Number nested = waiting.until(singleElement(equalTo(1)));
        Number indexed = waiting.until(elementAt(0));
        Number first = waiting.until(firstElement((Number number) -> true));
        Number last = waiting.until(lastElement((Number number) -> true));
        AwaitResult<List<Number>, Number> result = tryAwait(() -> List.of(actual)).until(singleElement(equalTo(1)));
        List<List<Number>> captured = waiting.until(captured(singleElement, singleElement(equalTo(1)), elementAt(0)));
        AwaitResult<List<Number>, List<List<Number>>> recorded = tryAwait(() -> List.of(actual)).every(ofNanos(1))
                .until(captured(firstElement, singleElement(equalTo(1)), lastElement));
        var results = new ArrayList<>(List.of(nested, indexed, first, last, result.orThrow()));
        captured.forEach(results::addAll);
        recorded.orThrow().forEach(results::addAll);
        return results;
    }

    public static List<Number> mapValues(Number actual) {
        var waiting = await(() -> Map.of("key", actual)).every(ofNanos(1));
        var check = valueFor("key", equalTo(1)).because("key selects the expected number");
        Number value = waiting.until(valueFor("key"));
        AwaitResult<Map<String, Number>, Number> result = tryAwait(() -> Map.of("key", actual)).until(check);
        List<Map<String, Number>> captured = waiting.until(captured(valueFor("key"), check));
        AwaitResult<Map<String, Number>, List<Map<String, Number>>> recorded = tryAwait(() -> Map.of("key", actual))
                .every(ofNanos(1)).until(captured(check, check));
        var results = new ArrayList<>(List.of(value, result.orThrow()));
        captured.forEach(map -> results.add(map.get("key")));
        recorded.orThrow().forEach(map -> results.add(map.get("key")));
        return results;
    }

    public static List<Map.Entry<String, Number>> mapEntries(Number actual) {
        var waiting = await(() -> Map.of("key", actual));
        Map.Entry<String, Number> entry = waiting.until(singleEntry);
        Map.Entry<String, Number> matching = waiting.until(singleEntry(equalTo(Map.entry("key", 1))));
        Map.Entry<String, Number> byKey = waiting.until(entryFor("key"));
        return List.of(entry, matching, byKey);
    }
}
