package consumer.value;

import io.github.gromoff97.awium.Await.CollectionSource;
import io.github.gromoff97.awium.Await.MapSource;
import io.github.gromoff97.awium.Await.OptionalSource;
import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.function.checked.CheckedPredicate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.all;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.instanceOf;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.matches;
import static io.github.gromoff97.awium.conditions.map.MapConditions.allEntries;
import static io.github.gromoff97.awium.conditions.map.MapConditions.hasEntry;
import static io.github.gromoff97.awium.conditions.map.MapConditions.hasNoEntry;
import static io.github.gromoff97.awium.conditions.map.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.present;
import static io.github.gromoff97.awium.function.checked.CheckedPredicate.not;

public final class ExplanationConsumer {
    private ExplanationConsumer() {}

    public record User(boolean active) {}

    public static List<ArrayList<User>> collections(ArrayList<User> actual) {
        CollectionSource<ArrayList<User>> source = () -> actual;
        return List.of(await(source).until(all((User user) -> user.active()).because("active users required")),
                await(source).until(all(User::active).because("active users required")));
    }

    public static List<User> selections(User user) {
        CollectionSource<ArrayList<User>> source = () -> new ArrayList<>(List.of(user));
        OptionalSource<User> optional = () -> Optional.of(user);
        MapSource<Map<String, User>> map = () -> Map.of("id", user);
        PreservingCondition<User> check = matches(User::active);
        return List.of(await(source).until(singleElement((User value) -> value.active()).because("one active user required")),
                await(source).until(singleElement.because("one user required")),
                await(optional).until(present.because("user must exist")),
                await(map).until(valueFor("id", (User value) -> value.active()).because("user must be active")),
                await(() -> (Object) user).until(instanceOf(User.class).because("user value required")),
                await(() -> user).until(check.because("active user required")));
    }

    public static List<LinkedHashMap<String, Integer>> wildcardEntries(LinkedHashMap<String, Integer> actual) {
        CheckedPredicate<Map.Entry<String, ? extends Number>> positive = entry -> entry.getValue().intValue() > 0;
        var check = matches(positive).because("entry requirement");
        var opposite = matches(not(positive)).because("entry requirement");
        var waiting = await(() -> actual);
        return List.of(waiting.until(allEntries(check)), waiting.until(hasEntry(check)), waiting.until(hasNoEntry(opposite)));
    }
}
