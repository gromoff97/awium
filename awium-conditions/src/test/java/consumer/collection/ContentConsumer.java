package consumer.collection;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SequencedCollection;
import java.util.concurrent.Callable;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.CollectionConditions.contains;
import static io.github.gromoff97.awium.conditions.CollectionConditions.containsAll;
import static io.github.gromoff97.awium.conditions.CollectionConditions.containsOnly;
import static io.github.gromoff97.awium.conditions.CollectionConditions.containsSequence;
import static io.github.gromoff97.awium.conditions.CollectionConditions.containsSubsequence;
import static io.github.gromoff97.awium.conditions.CollectionConditions.doesNotContain;
import static io.github.gromoff97.awium.conditions.CollectionConditions.doesNotContainAny;
import static io.github.gromoff97.awium.conditions.CollectionConditions.doesNotContainSequence;
import static io.github.gromoff97.awium.conditions.CollectionConditions.doesNotContainSubsequence;
import static io.github.gromoff97.awium.conditions.CollectionConditions.endsWith;
import static io.github.gromoff97.awium.conditions.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.CollectionConditions.sorted;
import static io.github.gromoff97.awium.conditions.CollectionConditions.startsWith;
import static io.github.gromoff97.awium.conditions.CollectionConditions.subsetOf;
import static io.github.gromoff97.awium.conditions.Conditions.captured;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparingInt;
import static java.util.Comparator.naturalOrder;

public final class ContentConsumer {
    private ContentConsumer() {}

    public static List<List<String>> nestedLists(List<List<String>> actual, List<String> expected) {
        return await(() -> actual).until(contains(expected));
    }

    public static List<String[]> arrays(List<String[]> actual, String[] expected) {
        return await(() -> actual).until(contains(expected));
    }

    public static List<String[]> onlyArrays(List<String[]> actual, String[] expected) {
        return await(() -> actual).until(containsOnly(singletonList(expected)));
    }

    public static int iterableArrayElements(int[] expected) {
        Iterable<int[]> candidates = List.of(expected);
        Callable<List<int[]>> source = () -> List.of(expected.clone());
        return await(source).until(containsOnly(candidates)).getFirst().length;
    }

    public static int iterableListElements(List<String> expected) {
        Iterable<List<String>> candidates = List.of(expected);
        Callable<List<List<String>>> source = () -> List.of(expected);
        return await(source).until(containsOnly(candidates)).getFirst().getFirst().length();
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

    public static List<ArrayList<String>> subsets(ArrayList<String> actual) {
        var plain = await(() -> actual).until(subsetOf(List.of("ready", "done")));
        var selected = await(() -> List.of(actual)).until(singleElement(subsetOf(List.of("ready"))));
        var observations = await(() -> actual).until(captured(subsetOf(List.of("ready")), subsetOf(List.of("ready", "done"))));
        var recorded = tryAwait(() -> actual).until(subsetOf(List.of("ready"))).orThrow();
        plain.getFirst().length();
        selected.getFirst().length();
        observations.getLast().getFirst().length();
        recorded.getFirst().length();
        return List.of(plain, selected, observations.getLast(), recorded);
    }

    public static List<CharSequence> narrowerSubset(List<CharSequence> actual) {
        return await(() -> actual).until(subsetOf(List.of("ready")));
    }

    public static List<ArrayList<Integer>> orderedChecks(ArrayList<Integer> actual) {
        Callable<ArrayList<Integer>> source = () -> actual;
        var waiting = await(source::call);
        return List.of(waiting.until(startsWith(List.of(1, 2))), waiting.until(endsWith(List.of(3, 5))),
                waiting.until(containsSequence(List.of(2, 3))), waiting.until(containsSubsequence(List.of(1, 3, 5))),
                waiting.until(sorted(Integer::compareTo)));
    }

    public static List<ArrayList<String>> emptyOrderedPatterns(ArrayList<String> actual) {
        var checks = List.<PreservingCondition<SequencedCollection<? super String>>>of(
                startsWith(List.of()), endsWith(List.of()), containsSequence(List.of()), containsSubsequence(List.of()));
        return checks.stream().flatMap(check -> List.of(
                await(() -> actual).until(check), tryAwait(() -> actual).until(check).orThrow()).stream()).toList();
    }

    public static List<List<String[]>> arrayPatterns(List<String[]> actual) {
        var expected = singletonList(new String[] {"a", "b"});
        var unrelated = singletonList(new String[] {"other"});
        var checks = List.of(startsWith(expected), endsWith(expected), containsSequence(expected), containsSubsequence(expected),
                doesNotContainSequence(unrelated), doesNotContainSubsequence(unrelated));
        return checks.stream().flatMap(check -> {
            var selected = await(() -> actual).until(check);
            var recorded = tryAwait(() -> actual).until(check).orThrow();
            selected.getFirst()[0].length();
            return List.of(selected, recorded).stream();
        }).toList();
    }
    public static ArrayList<Integer> storedSortOrder(ArrayList<Integer> actual) {
        var ordered = sorted(comparingInt(Number::intValue));
        return await(() -> actual).until(ordered);
    }

    public static ArrayList<String> wordOrder(ArrayList<String> actual) {
        return await(() -> actual).until(sorted(String::compareTo));
    }

    public static List<Integer> naturalOrderResult(List<Integer> actual) {
        return await(() -> actual).until(sorted(naturalOrder()));
    }

}
