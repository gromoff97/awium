package consumer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static io.github.gromoff97.awium.Await.CollectionViewSource;
import static io.github.gromoff97.awium.Await.MapViewSource;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.conditions.CollectionConditions.firstElement;
import static io.github.gromoff97.awium.conditions.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.Conditions.isNotNull;
import static io.github.gromoff97.awium.conditions.Conditions.matches;
import static io.github.gromoff97.awium.conditions.MapConditions.entryFor;
import static io.github.gromoff97.awium.conditions.MapConditions.singleEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;

public final class SourceConsumer {
    private SourceConsumer() {}

    public static List<Number> typedViews() {
        Callable<List<? extends Number>> values = () -> List.of(1);
        Callable<Map<CharSequence, ? extends Number>> entries = () -> Map.of("key", 1);
        var collection = new CollectionViewSource<Number, List<? extends Number>>(values);
        var map = new MapViewSource<CharSequence, Number, Map<CharSequence, ? extends Number>>(entries);
        Number value = await(collection).until(singleElement(isNotNull));
        Number mapped = await(map).until(valueFor("key", isNotNull));
        Map.Entry<? extends CharSequence, ? extends Number> entry = await(map).until(entryFor("key"));
        Map.Entry<String, Number> checked = await(() -> Map.<String, Number>of("key", 1)).until(singleEntry(matches(
                (Map.Entry<? extends String, ? extends Number> candidate) -> candidate.getValue() != null)));
        Integer subtype = await(() -> List.of(1)).until(firstElement((Number number) -> true));
        return List.of(value, mapped, subtype, entry.getValue(), checked.getValue());
    }
}
