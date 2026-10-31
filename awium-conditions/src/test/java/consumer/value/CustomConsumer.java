package consumer.value;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.CollectionConditions.firstElement;
import static io.github.gromoff97.awium.conditions.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.Conditions.asserted;
import static io.github.gromoff97.awium.conditions.Conditions.captured;
import static io.github.gromoff97.awium.conditions.Conditions.condition;
import static io.github.gromoff97.awium.conditions.Conditions.conditionPerWait;
import static io.github.gromoff97.awium.conditions.Conditions.isNotNull;
import static io.github.gromoff97.awium.conditions.Conditions.matches;
import static io.github.gromoff97.awium.conditions.MapConditions.allEntries;
import static io.github.gromoff97.awium.conditions.MapConditions.hasEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.hasNoEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.singleEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.time.Duration.ofNanos;
import static java.util.function.Predicate.not;

public final class CustomConsumer {
    private CustomConsumer() {}

    public record User(int id) {}

    public static int selectedId(User user) {
        return await(() -> user).until(condition("id", actual -> satisfied(actual.id()))).intValue();
    }

    public static int lazySelectedId(User user) {
        return await(() -> user).until(conditionPerWait("id", () -> actual -> satisfied(actual.id()))).intValue();
    }

    public static int optionalId(User user) {
        return await(() -> Optional.of(user)).until(hasValue(condition("id", actual -> satisfied(actual.id())))).intValue();
    }

    public static int elementId(User user) {
        return await(() -> List.of(user)).until(singleElement(condition("id", actual -> satisfied(actual.id())))).intValue();
    }

    public static int valueId(User user) {
        return await(() -> Map.of("user", user)).until(valueFor("user", condition("id", actual -> satisfied(actual.id())))).intValue();
    }

    public static int entryId(User user) {
        return await(() -> Map.of("user", user)).until(singleEntry(condition("id", entry -> satisfied(entry.getValue().id())))).intValue();
    }

    public static int firstUserId(User user) {
        return await(() -> List.of(user)).until(firstElement(condition("ready", actual -> satisfied(actual)))).id();
    }

    public static User storedAttempt(User user) {
        AwaitAttempt<User, User> callback = actual -> satisfied(actual);
        return await(() -> user).until(condition("ready", callback).because("user is ready"));
    }

    public static User callableFactory(User user) {
        Callable<AwaitAttempt<User, User>> factory = () -> actual -> satisfied(actual);
        return await(() -> user).until(conditionPerWait("ready", factory).because("user is ready"));
    }

    public static User recordPattern(User user) {
        var result = tryAwait(() -> user).until(condition("ready", actual -> satisfied(actual)));
        var value = switch (result) {
            case AwaitResult.Succeeded(var history, var actual) -> actual;
            case AwaitResult.Failed(var history, var failure) -> throw new AssertionError(failure.message());
        };
        value.id();
        return value;
    }

    public static String nullSelection(User user) {
        Condition<User, String> check = condition("no label", actual -> satisfied(null));
        return await(() -> user).until(check);
    }

    public static AwaitResult<User, String> nullSelectionResult(User user) {
        return tryAwait(() -> user).until(condition("no label", actual -> satisfied((String) null)));
    }

    public static Map<String, User> allSelectedIds(Map<String, User> users) {
        return await(() -> users).until(allEntries(condition("id", entry -> satisfied(entry.getValue().id()))));
    }

    public static Map<String, User> anySelectedId(Map<String, User> users) {
        return await(() -> users).until(hasEntry(condition("id", entry -> satisfied(entry.getValue().id()))));
    }

    public static Map<String, User> noMissingId(Map<String, User> users) {
        return await(() -> users).until(hasNoEntry(condition("missing id", entry -> entry.getValue().id() == 0
                ? satisfied(entry.getValue()) : unsatisfied())));
    }
    public static List<User> inferredIdentities(User user) {
        var check = condition("identity", (User actual) -> satisfied(actual));
        var direct = await(() -> user).until(condition("inline", actual -> satisfied(actual)));
        var created = await(() -> user).until(conditionPerWait("factory", () -> actual -> satisfied(actual)));
        var selected = await(() -> List.of(user)).until(singleElement(check));
        var optional = await(() -> Optional.of(user)).until(hasValue(check));
        var mapped = await(() -> Map.of("key", user)).until(valueFor("key", check));
        var sequence = await(() -> user).every(ofNanos(1)).until(captured(check, condition("second", actual -> satisfied(actual))));
        direct.id();
        created.id();
        selected.id();
        optional.id();
        mapped.id();
        sequence.getLast().id();
        return List.of(direct, created, selected, optional, mapped, sequence.getLast());
    }

    public static List<String> composedCallbacks() {
        Predicate<String> nonNull = value -> value != null;
        Predicate<String> text = nonNull.and(value -> !value.isBlank());
        Function<String, String> trim = String::trim;
        var calls = new ArrayList<String>();
        Consumer<String> first = value -> calls.add("first " + value);
        BiPredicate<String, Integer> entry = (key, value) -> value > 0;
        String matched = await(() -> "ready").until(matches(not(text).negate()::test));
        String transformed = await(() -> trim.apply(" ready ")).until(isNotNull);
        String custom = await(() -> trim.apply(" ready ")).until(condition("ready", actual -> satisfied(actual)));
        String checked = await(() -> "ready").until(asserted(first.andThen(value -> calls.add("second " + value))::accept));
        Map<String, Integer> entries = await(() -> Map.of("ready", 1)).until(hasEntry(entry.and((key, value) -> !key.isBlank())::test));
        return List.of(matched, transformed, custom, checked, calls.getFirst(), calls.getLast(), entries.get("ready").toString());
    }

    public static List<CharSequence> evaluatorResults() {
        Callable<AwaitAttempt<CharSequence, CharSequence>> broad = () -> actual -> satisfied(actual);
        var waiting = await(() -> "ready").every(ofNanos(1));
        String direct = waiting.until(condition("direct", actual -> satisfied(actual)));
        String created = waiting.until(conditionPerWait("created", CustomConsumer::evaluator));
        CharSequence selected = waiting.until(conditionPerWait("broad", broad));
        List<String> captured = waiting.until(captured(conditionPerWait("first", CustomConsumer::evaluator),
                conditionPerWait("second", CustomConsumer::evaluator)));
        return List.of(direct, created, selected, captured.getFirst(), captured.getLast());
    }

    private static AwaitAttempt<String, String> evaluator() { return actual -> satisfied(actual); }

}
