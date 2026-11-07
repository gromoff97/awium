package consumer.collection;

import io.github.gromoff97.awium.conditions.common.CommonConditions;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.LinkedHashSet;
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
import static io.github.gromoff97.awium.conditions.common.CommonConditions.asserted;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.equalTo;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.instanceOf;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.isNotNull;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.isNull;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.matches;
import static io.github.gromoff97.awium.conditions.map.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.hasValue;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public final class SelectionConsumer {
    private SelectionConsumer() {}

    public record User(int id) {
        public boolean positive() { return id > 0; }
    }

    public static List<Integer> narrowedIds() {
        List<Object> values = List.of(new User(1), 99, new User(2));
        var waiting = await(() -> values);
        var result = tryAwait(() -> values).until(firstElement(instanceOf(User.class)));
        int recorded = switch (result) {
            case AwaitResult.Succeeded(var history, var user) -> user.id();
            case AwaitResult.Failed(var history, var failure) -> throw new AssertionError(failure.message());
        };
        return List.of(waiting.until(firstElement(instanceOf(User.class))).id(),
                waiting.until(lastElement(instanceOf(User.class))).id(),
                waiting.until(elementAt(0, instanceOf(User.class))).id(), recorded);
    }

    public static List<Integer> checkedIds() {
        var first = new User(1);
        var last = new User(2);
        var values = List.of(first, new User(-1), last);
        var waiting = await(() -> values);
        return List.of(waiting.until(firstElement(matches(User::positive))).id(),
                waiting.until(lastElement(matches(User::positive))).id(),
                waiting.until(elementAt(0, matches(User::positive))).id(),
                waiting.until(firstElement(equalTo(first))).id(),
                waiting.until(lastElement(equalTo(last))).id(),
                waiting.until(elementAt(2, equalTo(last))).id(),
                waiting.until(firstElement(isNotNull)).id(),
                waiting.until(lastElement(isNotNull)).id(),
                waiting.until(elementAt(0, isNotNull)).id());
    }

    public static List<Integer> nestedIds() {
        List<List<Object>> nested = List.of(List.of(new User(1)), List.of("skip"), List.of(new User(2)));
        return List.of(await(() -> nested).until(firstElement(firstElement(instanceOf(User.class)))).id(),
                await(() -> nested).until(lastElement(lastElement(instanceOf(User.class)))).id(),
                await(() -> Optional.of(nested.getFirst())).until(hasValue(firstElement(instanceOf(User.class)))).id(),
                await(() -> Map.of("users", nested.get(2))).until(valueFor("users", elementAt(0, instanceOf(User.class)))).id(),
                await(() -> nested.subList(0, 1)).until(singleElement(firstElement(instanceOf(User.class)))).id());
    }

    public static List<Integer> dequeIds() {
        var queue = new ArrayDeque<>(List.of(new User(1), new User(2)));
        var waiting = await(() -> queue);
        return List.of(waiting.until(elementAt(1)).id(),
                waiting.until(elementAt(1, User::positive)).id(),
                waiting.until(elementAt(1, matches(User::positive))).id(),
                waiting.until(elementAt(1, equalTo(queue.getLast())).because("second user must arrive")).id(),
                tryAwait(() -> queue).until(elementAt(1, instanceOf(User.class))).orThrow().id(),
                await(queue::reversed).until(elementAt(1)).id());
    }

    public static List<List<User>> capturedSelectors(List<User> values) {
        var check = CommonConditions.<List<User>>captured(singleElement(equalTo(values.getFirst())),
                singleElement(User::positive)).because("selected user is valid");
        var observations = await(() -> values).every(ofNanos(1)).until(check);
        observations.getFirst().getFirst().id();
        return observations;
    }

    public static List<List<User>> recordedSelectors(List<User> values) {
        var check = CommonConditions.<List<User>>captured(singleElement(equalTo(values.getFirst())),
                singleElement(User::positive)).because("selected user is valid");
        var observations = tryAwait(() -> values).every(ofNanos(1)).until(check).orThrow();
        observations.getFirst().getFirst().id();
        return observations;
    }

    public static List<List<User>> capturedComparison(List<User> values) {
        var check = captured(equalTo(values), singleElement(User::positive)).because("list and user agree");
        var observations = await(() -> values).every(ofNanos(1)).until(check);
        observations.getFirst().getFirst().id();
        return observations;
    }

    public static List<List<User>> recordedComparison(List<User> values) {
        var check = captured(equalTo(values), singleElement(User::positive)).because("list and user agree");
        var observations = tryAwait(() -> values).every(ofNanos(1)).until(check).orThrow();
        observations.getFirst().getFirst().id();
        return observations;
    }

    public static List<ArrayDeque<User>> capturedQueue(ArrayDeque<User> queue) {
        var check = captured(firstElement, elementAt(1), lastElement).because("all positions must exist");
        var observations = tryAwait(() -> queue).every(ofNanos(1)).until(check).orThrow();
        observations.getLast().peekLast().id();
        return observations;
    }

    public static List<LinkedHashSet<User>> capturedSet(LinkedHashSet<User> values) {
        var check = captured(firstElement, elementAt(1), lastElement).because("all positions must exist");
        var observations = await(() -> values).every(ofNanos(1)).until(check);
        observations.getLast().getLast().id();
        return observations;
    }

    public static int narrowerExpectedType() {
        List<CharSequence> values = List.of("ready");
        return await(() -> values).until(firstElement(equalTo("ready"))).length();
    }

    public static List<User> singleChecks(ArrayList<User> actual) {
        Callable<ArrayList<User>> source = () -> actual;
        var waiting = await(source::call);
        return List.of(waiting.until(singleElement(matches(user -> user.id() > 0))),
                waiting.until(singleElement(asserted(user -> { if (!user.positive()) throw new AssertionError("not positive"); }))),
                waiting.until(singleElement(equalTo(actual.getFirst()))));
    }

    public static int singleNarrowed(List<Number> actual) {
        return await(() -> actual).until(singleElement(instanceOf(Integer.class))).intValue();
    }

    public static List<String> nullableSingle() {
        var values = singletonList((String) null);
        return asList(await(() -> values).until(singleElement),
                await(() -> values).until(singleElement(matches(value -> value == null))),
                await(() -> values).until(singleElement(isNull)));
    }

    public static String singleMapKey() {
        var entries = Map.of("key", 42);
        return await(entries::keySet).until(singleElement);
    }
}
