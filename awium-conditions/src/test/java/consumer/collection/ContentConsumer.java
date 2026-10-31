package consumer.collection;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.CollectionConditions.contains;
import static io.github.gromoff97.awium.conditions.CollectionConditions.containsAll;
import static io.github.gromoff97.awium.conditions.CollectionConditions.containsOnly;
import static io.github.gromoff97.awium.conditions.CollectionConditions.doesNotContain;
import static io.github.gromoff97.awium.conditions.CollectionConditions.doesNotContainAny;

public final class ContentConsumer {
    private ContentConsumer() {}

    public static List<List<String>> nestedLists(List<List<String>> actual, List<String> expected) {
        return await(() -> actual).until(contains(expected));
    }

    public static List<String[]> arrays(List<String[]> actual, String[] expected) {
        return await(() -> actual).until(contains(expected));
    }

    public static List<String[]> onlyArrays(List<String[]> actual, String[] expected) {
        return await(() -> actual).until(containsOnly(java.util.Collections.singletonList(expected)));
    }

    public static List<String[]> excludedArrays(List<String[]> actual, String[] unexpected) {
        return await(() -> actual).until(doesNotContain(unexpected));
    }

    public static List<List<String>> excludedLists(List<List<String>> actual, List<String> unexpected) {
        return await(() -> actual).until(doesNotContain(unexpected));
    }

    public static ArrayList<Number> narrowerExpectedElements(ArrayList<Number> actual) {
        Callable<ArrayList<Number>> source = () -> actual;
        PreservingCondition<Collection<? super Integer>> condition = containsAll(List.of(1, 2));
        return await(source::call).until(condition);
    }

    public static List<ArrayList<String>> emptyExpectations(ArrayList<String> actual) {
        ArrayList<String> all = await(() -> actual).until(containsAll(List.of()));
        ArrayList<String> none = await(() -> actual).until(doesNotContainAny(List.of()));
        ArrayList<String> recorded = tryAwait(() -> actual).until(containsAll(List.of())).orThrow();
        return List.of(all, none, recorded);
    }
}
