package consumer.collection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.all;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.any;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.firstElement;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.none;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.Conditions.equalTo;
import static io.github.gromoff97.awium.conditions.Conditions.instanceOf;
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
import static io.github.gromoff97.awium.conditions.map.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.absent;
import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.present;
import static java.util.function.Predicate.not;

public final class QuantifierConsumer {
    private QuantifierConsumer() {}

    public record User(int id, boolean active) {}

    public static ArrayList<Optional<User>> allPresent(ArrayList<Optional<User>> actual) {
        return await(() -> actual).until(all(present).because("each user is available"));
    }

    public static ArrayList<Optional<User>> somePresent(ArrayList<Optional<User>> actual) {
        return tryAwait(() -> actual).until(any(present)).orThrow();
    }

    public static ArrayList<Optional<User>> nonePresent(ArrayList<Optional<User>> actual) {
        return await(() -> actual).until(none(present));
    }

    public static List<LinkedHashMap<Optional<User>, List<User>>> nestedMapSelections(LinkedHashMap<Optional<User>, List<User>> actual) {
        var waiting = await(() -> actual);
        return List.of(waiting.until(allKeys(present).because("keys are available")),
                tryAwait(() -> actual).until(allValues(singleElement).because("one user per key")).orThrow(),
                waiting.until(anyKey(present)), waiting.until(anyValue(singleElement)),
                waiting.until(noKey(absent)), waiting.until(noValue(firstElement(instanceOf(String.class)))));
    }

    public static Map<String, Map<String, User>> nestedMapValue(Map<String, Map<String, User>> actual) {
        return await(() -> actual).until(allValues(valueFor("user")));
    }

    public static List<ArrayList<User>> allFamilies(ArrayList<User> actual, User expected) {
        var waiting = await(() -> actual);
        return List.of(waiting.until(all(matches(User::active))), waiting.until(all(equalTo(expected))),
                waiting.until(all(instanceOf(User.class))));
    }

    public static List<ArrayList<User>> anyFamilies(ArrayList<User> actual, User expected) {
        var check = matches(User::active);
        var waiting = await(() -> actual);
        return List.of(waiting.until(any(check)), waiting.until(any(equalTo(expected))),
                tryAwait(() -> actual).until(any(check)).orThrow());
    }

    public static List<ArrayList<User>> noFamilies(ArrayList<User> actual, User unexpected) {
        var waiting = await(() -> actual);
        return List.of(waiting.until(none(matches(User::active))), waiting.until(none(equalTo(unexpected))),
                waiting.until(none(instanceOf(String.class))));
    }

    public static List<Object> heterogeneousValues(List<Object> actual) {
        return await(() -> actual).until(any(instanceOf(User.class)));
    }

    public static List<LinkedHashMap<String, User>> mapConditionFamilies(LinkedHashMap<String, User> actual, User active, User pending) {
        var waiting = await(() -> actual);
        return List.of(waiting.until(allValues(matches(User::active))), waiting.until(anyValue(matches(User::active))),
                waiting.until(noValue(equalTo(pending))), waiting.until(allKeys(equalTo("user"))),
                waiting.until(anyKey(instanceOf(String.class))), waiting.until(noKey(matches(String::isBlank))),
                waiting.until(allEntries(matches((Entry<String, User> entry) -> entry.getValue().active()))),
                waiting.until(hasEntry(equalTo(Map.entry("user", active)))), waiting.until(hasNoEntry(instanceOf(String.class))));
    }

    public static List<Map<String, Number>> narrowerExpectedValues(Map<String, Number> actual) {
        var waiting = await(() -> actual);
        return List.of(waiting.until(allEntries(equalTo(Map.entry("count", 1)))), waiting.until(allValues(equalTo(1))));
    }
    public static List<LinkedHashMap<String, Integer>> supertypeEntryPredicates(LinkedHashMap<String, Integer> actual) {
        var waiting = await(() -> actual);
        BiPredicate<CharSequence, Number> valid = (key, value) -> !key.isEmpty() && value.intValue() > 0;
        return List.of(waiting.until(allEntries(valid::test)), waiting.until(hasEntry(valid::test)),
                waiting.until(hasNoEntry(valid.negate()::test)));
    }

    public static List<ArrayList<String>> supertypeElementPredicates(ArrayList<String> actual) {
        var waiting = await(() -> actual);
        return List.of(waiting.until(all(CharSequence::isEmpty)), waiting.until(any(CharSequence::isEmpty)),
                waiting.until(none(not(CharSequence::isEmpty)::test)));
    }

}
