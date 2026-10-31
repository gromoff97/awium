package consumer.value;
import static io.github.gromoff97.awium.conditions.Conditions.captured;
import static java.time.Duration.ofNanos;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.function.CheckedFunction;
import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.CollectionConditions.firstElement;
import static io.github.gromoff97.awium.conditions.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.Conditions.condition;
import static io.github.gromoff97.awium.conditions.Conditions.conditionPerWait;
import static io.github.gromoff97.awium.conditions.MapConditions.allEntries;
import static io.github.gromoff97.awium.conditions.MapConditions.hasEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.hasNoEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.singleEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

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

    public static User checkedFunction(User user) {
        CheckedFunction<User, ConditionResult<User>> callback = actual -> satisfied(actual);
        return await(() -> user).until(condition("ready", callback).because("user is ready"));
    }

    public static User callableFactory(User user) {
        Callable<CheckedFunction<User, ConditionResult<User>>> factory = () -> actual -> satisfied(actual);
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

}
