package consumer;

import io.github.gromoff97.awium.conditions.map.MapConditions;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import static io.github.gromoff97.awium.Await.CollectionViewSource;
import static io.github.gromoff97.awium.Await.MapViewSource;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.all;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.firstElement;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.lastElement;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.nonEmpty;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.Conditions.captured;
import static io.github.gromoff97.awium.conditions.Conditions.isNotNull;
import static io.github.gromoff97.awium.conditions.Conditions.matches;
import static io.github.gromoff97.awium.conditions.map.MapConditions.allEntries;
import static io.github.gromoff97.awium.conditions.map.MapConditions.entryFor;
import static io.github.gromoff97.awium.conditions.map.MapConditions.singleEntry;
import static io.github.gromoff97.awium.conditions.map.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.present;
import static java.time.Duration.ofNanos;

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
    public static Number selectedCollectionView(Callable<List<? extends Number>> source, Number expected) {
        var view = new CollectionViewSource<Number, List<? extends Number>>(source);
        var check = singleElement(expected::equals).because("the expected number must arrive");
        var selected = await(view).every(ofNanos(1)).until(check);
        selected.intValue();
        return selected;
    }

    public static AwaitResult<List<? extends Number>, Number> recordedCollectionView(Callable<List<? extends Number>> source, Number expected) {
        var view = new CollectionViewSource<Number, List<? extends Number>>(source);
        var check = singleElement(expected::equals).because("the expected number must arrive");
        return tryAwait(view).every(ofNanos(1)).until(check);
    }

    public static List<List<? extends Number>> capturedCollectionView(Callable<List<? extends Number>> source, Number expected) {
        var view = new CollectionViewSource<Number, List<? extends Number>>(source);
        var check = singleElement(expected::equals).because("the expected number must arrive");
        var observations = await(view).every(ofNanos(1)).until(captured(nonEmpty, check));
        observations.getLast().getFirst().intValue();
        return observations;
    }

    public static Number selectedMapView(Callable<Map<String, ? extends Number>> source, Number expected) {
        var view = new MapViewSource<String, Number, Map<String, ? extends Number>>(source);
        var check = valueFor("answer", expected::equals).because("the answer must arrive");
        var selected = await(view).until(check);
        selected.intValue();
        return selected;
    }

    public static AwaitResult<Map<String, ? extends Number>, Number> recordedMapView(Callable<Map<String, ? extends Number>> source, Number expected) {
        var view = new MapViewSource<String, Number, Map<String, ? extends Number>>(source);
        var check = valueFor("answer", expected::equals).because("the answer must arrive");
        return tryAwait(view).until(check);
    }

    public static List<Map<String, ? extends Number>> capturedMapView(Callable<Map<String, ? extends Number>> source, Number expected) {
        var view = new MapViewSource<String, Number, Map<String, ? extends Number>>(source);
        var check = valueFor("answer", expected::equals).because("the answer must arrive");
        var observations = tryAwait(view).every(ofNanos(1))
                .until(captured(MapConditions.nonEmpty, check)).orThrow();
        observations.getLast().get("answer").intValue();
        return observations;
    }

    public record User(boolean active) {}

    public static List<User> inlineSelections(User user) {
        var users = new ArrayList<>(List.of(user));
        User matched = await(() -> user).until(matches(value -> value.active()));
        ArrayList<User> allUsers = await(() -> users).until(all(value -> value.active()));
        User selected = await(() -> users).until(singleElement(value -> value.active()));
        User optional = await(() -> Optional.of(user)).until(hasValue(value -> value.active()));
        User mapped = await(() -> Map.of("id", user)).until(valueFor("id", value -> value.active()));
        ArrayList<User> nested = await(() -> users).until(all(matches(value -> value.active())));
        Map<String, User> entries = await(() -> Map.of("id", user))
                .until(allEntries((key, value) -> key.length() == 2 && value.active()));
        List<ArrayList<User>> stages = await(() -> users).every(ofNanos(1)).until(captured(
                singleElement(value -> value.active()), singleElement(value -> value.active())));
        return List.of(matched, selected, optional, mapped, allUsers.getFirst(), nested.getFirst(), entries.get("id"),
                stages.getFirst().getFirst(), stages.getLast().getFirst());
    }

    public static List<ArrayList<String>> callableCollectionCapture(Callable<ArrayList<String>> source) {
        return await(source::call).every(ofNanos(1)).until(captured(firstElement, lastElement));
    }

    public static List<Optional<String>> covariantOptionalCapture(Callable<? extends Optional<String>> source) {
        return await(source::call).every(ofNanos(1)).until(captured(present, present));
    }

}
