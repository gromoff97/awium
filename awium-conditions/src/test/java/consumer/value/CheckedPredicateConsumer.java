package consumer.value;

import io.github.gromoff97.awium.conditions.function.CheckedBiPredicate;
import io.github.gromoff97.awium.result.AwaitResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.all;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.any;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.none;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.firstElement;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.lastElement;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.elementAt;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.Conditions.matches;
import static io.github.gromoff97.awium.conditions.map.MapConditions.allEntries;
import static io.github.gromoff97.awium.conditions.map.MapConditions.allKeys;
import static io.github.gromoff97.awium.conditions.map.MapConditions.allValues;
import static io.github.gromoff97.awium.conditions.map.MapConditions.anyKey;
import static io.github.gromoff97.awium.conditions.map.MapConditions.anyValue;
import static io.github.gromoff97.awium.conditions.map.MapConditions.hasEntry;
import static io.github.gromoff97.awium.conditions.map.MapConditions.hasNoEntry;
import static io.github.gromoff97.awium.conditions.map.MapConditions.noKey;
import static io.github.gromoff97.awium.conditions.map.MapConditions.noValue;
import static io.github.gromoff97.awium.conditions.map.MapConditions.singleEntry;
import static io.github.gromoff97.awium.conditions.map.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.conditions.function.CheckedPredicate.not;
import static java.time.Duration.ofNanos;

public final class CheckedPredicateConsumer {
    private CheckedPredicateConsumer() {}

    public record User(int id, boolean ready) {
        public boolean checkReady() throws IOException { return ready; }
    }

    public static User negatedMatch(List<User> observations, boolean recorded) {
        var source = observations.iterator();
        if (recorded) return tryAwait(source::next).every(ofNanos(1)).until(matches(not(User::checkReady))).orThrow();
        var selected = await(source::next).every(ofNanos(1)).until(matches(not(User::checkReady)));
        selected.id();
        return selected;
    }

    public static int negatedElement(User user) {
        return await(() -> List.of(user)).until(singleElement(not(User::checkReady))).id();
    }

    public static Entry<String, User> negatedEntry(List<Map<String, User>> observations) {
        var source = observations.iterator();
        var selected = await(source::next).every(ofNanos(1))
                .until(singleEntry(CheckedBiPredicate.not(CheckedPredicateConsumer::readyEntry)));
        selected.getValue().id();
        return selected;
    }

    public static int matchingId(List<User> observations) {
        var source = observations.iterator();
        return await(source::next).every(ofNanos(1)).until(matches(User::checkReady)).id();
    }

    public static AwaitResult<User, User> recordedMatch(List<User> observations) {
        var source = observations.iterator();
        return tryAwait(source::next).every(ofNanos(1)).until(matches(User::checkReady));
    }

    public static int lambdaId(User user) {
        return await(() -> user).until(matches(value -> value.checkReady())).id();
    }

    public static List<Integer> selectedIds(List<User> actual) {
        var waiting = await(() -> actual);
        return List.of(waiting.until(firstElement(User::checkReady)).id(), waiting.until(lastElement(User::checkReady)).id(),
                waiting.until(elementAt(0, User::checkReady)).id(),
                await(() -> List.of(actual.getFirst())).until(singleElement(User::checkReady)).id());
    }

    public static List<User> someReady(List<User> actual) { return await(() -> actual).until(any(User::checkReady)); }

    public static ArrayList<User> allReady(ArrayList<User> actual) { return await(() -> actual).until(all(User::checkReady)); }

    public static List<User> noneReady(List<User> actual) { return await(() -> actual).until(none(User::checkReady)); }

    public static List<LinkedHashMap<String, User>> mapChecks(LinkedHashMap<String, User> actual) {
        var waiting = await(() -> actual);
        return List.of(waiting.until(allEntries(CheckedPredicateConsumer::readyEntry)),
                waiting.until(allEntries(entry -> readyEntry(entry.getKey(), entry.getValue()))),
                waiting.until(hasEntry(CheckedPredicateConsumer::readyEntry)),
                waiting.until(hasEntry(entry -> readyEntry(entry.getKey(), entry.getValue()))),
                waiting.until(hasNoEntry(CheckedPredicateConsumer::pendingEntry)),
                waiting.until(hasNoEntry(entry -> pendingEntry(entry.getKey(), entry.getValue()))),
                waiting.until(allKeys(CheckedPredicateConsumer::validKey)), waiting.until(anyKey(CheckedPredicateConsumer::validKey)),
                waiting.until(noKey(CheckedPredicateConsumer::blankKey)), waiting.until(allValues(User::checkReady)),
                waiting.until(anyValue(User::checkReady)), waiting.until(noValue(user -> !user.checkReady())));
    }

    public static Entry<String, User> checkedEntry(Map<String, User> actual, boolean recorded) {
        if (recorded) {
            var result = tryAwait(() -> actual).until(singleEntry(CheckedPredicateConsumer::firstEntry));
            return switch (result) {
                case AwaitResult.Succeeded(var history, var entry) -> {
                    entry.getValue().id();
                    yield entry;
                }
                case AwaitResult.Failed(var history, var failure) -> throw new IllegalStateException(failure.message());
            };
        }
        return await(() -> actual).until(singleEntry(CheckedPredicateConsumer::firstEntry));
    }

    public static int optionalId(List<Optional<User>> observations) {
        var source = observations.iterator();
        return await(source::next).every(ofNanos(1)).until(hasValue(User::checkReady)).id();
    }

    public static AwaitResult<Map<String, User>, User> valueResult(List<Map<String, User>> observations) {
        var source = observations.iterator();
        return tryAwait(source::next).every(ofNanos(1)).until(valueFor("user", User::checkReady));
    }

    private static boolean readyEntry(String key, User user) throws Throwable { return validKey(key) && user.checkReady(); }

    private static boolean firstEntry(CharSequence key, User user) throws Throwable { return !key.isEmpty() && user.id() == 1 && user.checkReady(); }

    private static boolean pendingEntry(String key, User user) throws Throwable { return validKey(key) && !user.checkReady(); }

    private static boolean validKey(String key) throws Throwable { return !key.isBlank(); }

    private static boolean blankKey(String key) throws Throwable { return key.isBlank(); }
}
