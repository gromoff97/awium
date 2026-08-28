package io.github.gromoff97.awium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompilationContractTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesOneEntryPointAndFocusedConditionCatalogues() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.await.Await.*;
                import static io.github.gromoff97.awium.conditioning.conditions.Conditions.*;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionConditions.single;
                import static io.github.gromoff97.awium.conditioning.conditions.MapConditions.singleEntry;
                import static io.github.gromoff97.awium.conditioning.conditions.OptionalConditions.present;
                import static io.github.gromoff97.awium.conditioning.conditions.StringConditions.nonBlank;
                import io.github.gromoff97.awium.results.AwaitResult;
                import io.github.gromoff97.awium.sources.Source;
                import java.util.*;

                final class Contract {
                    record Payment(String status) {}

                    void check(Source<Payment> payment,
                            Source.OptionalSource<Payment> optional,
                            Source.CollectionSource<List<Payment>> collection,
                            Source.MapSource<Map<String, Payment>> map,
                            Source<String> text,
                            Source<Integer> number) {
                        Payment presentPayment = await(optional).until(present);
                        Payment onlyPayment = await(collection).until(single);
                        Map.Entry<String, Payment> onlyEntry = await(map).until(singleEntry);
                        List<Payment> lifecycle = await(payment).until(captured(
                                value -> value.status().equals("created"),
                                value -> value.status().equals("finished")));
                        AwaitResult<String, String> diagnostic = tryAwait(text).until(nonBlank);
                        await(number).until(atLeast(1));
                        await(payment).until(asserted(value -> {}));
                    }
                }
                """));
    }

    @Test
    void conditionCallbacksComposeWithJdkInterfaces() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
                import static io.github.gromoff97.awium.conditioning.conditions.Conditions.*;
                import static io.github.gromoff97.awium.conditioning.conditions.MapConditions.anyEntry;
                import java.util.function.*;

                final class Contract {
                    void check() {
                        Predicate<String> present = value -> value != null;
                        Predicate<String> text = present.and(value -> !value.isBlank());
                        matches(Predicate.not(text).negate());

                        Function<String, String> trim = String::trim;
                        yields(Function.<String>identity().andThen(trim));
                        condition("trimmed", trim.andThen(value -> satisfied(value)));

                        Consumer<String> first = value -> {};
                        asserted(first.andThen(value -> {}));

                        BiPredicate<String, Integer> entry = (key, value) -> value > 0;
                        anyEntry(entry.and((key, value) -> !key.isBlank()));
                    }
                }
                """));
    }

    @Test
    void excludedDirectAndJdkSourceTypesDoNotCompile() throws IOException {
        for (String declaration : new String[] {
                "String source = \"value\";",
                "java.util.function.Supplier<String> source = () -> \"value\";"
        }) {
            assertFalse(compiles("""
                    import static io.github.gromoff97.awium.await.Await.await;
                    final class Contract {
                        void check() {
                            %s
                            await(source);
                        }
                    }
                    """.formatted(declaration)), declaration);
        }
    }

    @Test
    void ambiguousNullSourcesAndConditionsDoNotCompile() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                final class Contract { void check() { await(() -> null); } }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                final class Contract { void check() { await(null); } }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import io.github.gromoff97.awium.sources.Source;
                final class Contract {
                    void check(Source<String> source) {
                        await(source).until(null);
                    }
                }
                """));
    }

    @Test
    void categorySpecificTerminalsRejectWrongConditions() throws IOException {
        for (String type : List.of(
                "SelectedCondition<java.util.Optional<?>, Source.OptionalSource<?>>",
                "SelectedCondition<java.util.Collection<?>, Source.CollectionSource<?>>",
                "SelectedCondition<java.util.Map<?, ?>, Source.MapSource<?>>")) {
            assertFalse(compiles("""
                    import static io.github.gromoff97.awium.await.Await.await;
                    import io.github.gromoff97.awium.sources.Source;
                    import io.github.gromoff97.awium.conditioning.conditions.*;
                    import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedCondition;
                    final class Contract {
                        void check(Source<String> source, %s condition) {
                            await(source).until(condition);
                        }
                    }
                    """.formatted(type)), type);
        }
    }

    @Test
    void plainSourcesRejectSelectedConditionCategoryEscapes() throws IOException {
        for (String condition : List.of("present", "single", "first", "last", "singleEntry")) {
            assertFalse(compiles("""
                    import static io.github.gromoff97.awium.await.Await.await;
                    import static io.github.gromoff97.awium.conditioning.conditions.CollectionConditions.*;
                    import static io.github.gromoff97.awium.conditioning.conditions.MapConditions.singleEntry;
                    import static io.github.gromoff97.awium.conditioning.conditions.OptionalConditions.present;
                    import io.github.gromoff97.awium.sources.Source;
                    import java.util.*;
                    final class Contract {
                        void check(Source<Optional<String>> optional,
                                Source<List<String>> collection,
                                Source<Map<String, String>> map) {
                            await(%s).until(%s);
                        }
                    }
                    """.formatted(condition.equals("present") ? "optional"
                                    : condition.equals("singleEntry") ? "map" : "collection",
                            condition)), condition);
        }
    }

    @Test
    void externalCallersCannotConstructSelectedConditions() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
                import static io.github.gromoff97.awium.conditioning.conditions.Conditions.condition;
                import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedCondition;
                import java.util.Optional;
                final class Contract {
                    void check() {
                        new SelectedCondition<>(condition("selected",
                                (Optional<?> value) -> satisfied(value.orElse(null))));
                    }
                }
                """));
    }

    @Test
    void sourceSelectedFieldsShareOneSourceTypedCondition() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.conditioning.conditions.OptionalConditions.present;
                import io.github.gromoff97.awium.conditioning.conditions.CollectionConditions;
                import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedCondition;
                import io.github.gromoff97.awium.conditioning.conditions.MapConditions;
                import java.util.Collection;
                import java.util.Map;
                import java.util.Optional;
                final class Contract {
                    SelectedCondition<Optional<?>, io.github.gromoff97.awium.sources.Source.OptionalSource<?>> optional = present;
                    SelectedCondition<Collection<?>, io.github.gromoff97.awium.sources.Source.CollectionSource<?>> collection = CollectionConditions.single;
                    SelectedCondition<Map<?, ?>, io.github.gromoff97.awium.sources.Source.MapSource<?>> map = MapConditions.singleEntry;
                }
                """));
    }

    @Test
    void collectionAndMapConditionsCannotBeMixed() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import io.github.gromoff97.awium.conditioning.conditions.MapConditions;
                import io.github.gromoff97.awium.sources.Source.CollectionSource;
                import java.util.List;
                final class Contract {
                    void check(CollectionSource<List<String>> source) {
                        await(source).until(MapConditions.nonEmpty);
                    }
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import io.github.gromoff97.awium.conditioning.conditions.CollectionConditions;
                import io.github.gromoff97.awium.sources.Source.MapSource;
                import java.util.Map;
                final class Contract {
                    void check(MapSource<Map<String, String>> source) {
                        await(source).until(CollectionConditions.nonEmpty);
                    }
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import io.github.gromoff97.awium.conditioning.conditions.MapConditions;
                import io.github.gromoff97.awium.sources.Source.CollectionSource;
                import java.util.List;
                final class Contract {
                    void check(CollectionSource<List<String>> source) {
                        await(source).until(MapConditions.singleEntry);
                    }
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import io.github.gromoff97.awium.conditioning.conditions.CollectionConditions;
                import io.github.gromoff97.awium.sources.Source.MapSource;
                import java.util.Map;
                final class Contract {
                    void check(MapSource<Map<String, String>> source) {
                        await(source).until(CollectionConditions.single);
                    }
                }
                """));
    }

    @Test
    void singleTerminalsInferElementKeyAndValueTypes() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import io.github.gromoff97.awium.conditioning.conditions.CollectionConditions;
                import io.github.gromoff97.awium.conditioning.conditions.MapConditions;
                import java.time.Duration;
                import java.util.ArrayList;
                import java.util.LinkedHashMap;
                import java.util.Map;

                final class Contract {
                    static ArrayList<String> collection() {
                        return new ArrayList<>();
                    }
                    static LinkedHashMap<String, Integer> map() {
                        return new LinkedHashMap<>();
                    }
                    void check() {
                        String element = await(Contract::collection).until(CollectionConditions.single);
                        Map.Entry<String, Integer> entry =
                                await(Contract::map).every(Duration.ofMillis(1)).until(MapConditions.singleEntry.because("one entry"));
                    }
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionConditions.single;
                import java.util.List;
                final class Contract {
                    void check() {
                        Integer wrong = await((io.github.gromoff97.awium.sources.Source.CollectionSource<List<String>>)
                                () -> List.of("value")).until(single);
                    }
                }
                """));
    }

    @Test
    void specializedSourcesRetainSelectedResultTypes() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionConditions.first;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionConditions.single;
                import static io.github.gromoff97.awium.conditioning.conditions.MapConditions.singleEntry;
                import static io.github.gromoff97.awium.conditioning.conditions.OptionalConditions.present;
                import io.github.gromoff97.awium.sources.Source.*;
                import java.util.*;
                final class Contract {
                    void check(OptionalSource<String> optional,
                            CollectionSource<List<String>> collection,
                            MapSource<Map<String, Integer>> map) {
                        String optionalResult = await(optional).until(present);
                        String collectionResult = await(collection).until(single);
                        String firstResult = await(collection).until(first);
                        Map.Entry<String, Integer> mapResult = await(map).until(singleEntry);
                    }
                }
                """));
    }

    @Test
    void singleElementIsBothAFieldAndAnOverloadedSelector() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionConditions.single;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionConditions.singleElementOfType;
                import io.github.gromoff97.awium.conditioning.conditions.MapConditions;
                import io.github.gromoff97.awium.sources.Source.CollectionSource;
                import io.github.gromoff97.awium.sources.Source.MapSource;
                import java.util.List;
                import java.util.Map;

                final class Contract {
                    void check(CollectionSource<List<String>> strings,
                            CollectionSource<List<Object>> objects,
                            MapSource<Map<String, Integer>> map) {
                        String only = await(strings).until(single);
                        String matching = await(strings).until(single(value -> !value.isBlank()));
                        String narrowed = await(objects).until(singleElementOfType(String.class));
                        Map.Entry<String, Integer> entry = await(map).until(MapConditions.singleEntry);
                        Map.Entry<String, Integer> selected = await(map).until(MapConditions.singleEntry((key, value) -> value > 0));
                    }
                }
                """));
    }

    @Test
    void firstAndLastAreTypedFieldsWithPredicateOverloads() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionConditions.first;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionConditions.last;
                import io.github.gromoff97.awium.sources.Source.CollectionSource;
                import java.util.List;
                final class Contract {
                    void check(CollectionSource<List<String>> source) {
                        String firstValue = await(source).until(first);
                        String lastValue = await(source).until(last.because("latest business result"));
                        String firstMatch = await(source).until(first(value -> !value.isBlank()));
                        String lastMatch = await(source).until(last(value -> !value.isBlank()));
                    }
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionConditions.first;
                import io.github.gromoff97.awium.sources.Source.CollectionSource;
                import java.util.List;
                final class Contract {
                    void check(CollectionSource<List<String>> source) {
                        Integer wrong = await(source).until(first);
                    }
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionConditions.first;
                import io.github.gromoff97.awium.sources.Source.CollectionSource;
                import java.util.HashSet;
                final class Contract {
                    void check(CollectionSource<HashSet<String>> source) {
                        await(source).until(first);
                    }
                }
                """));
    }

    @Test
    void collectionElementAndAggregateFactoriesAreUnambiguous() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionConditions.*;
                import java.util.List;

                final class Contract {
                    static List<List<String>> nested() {
                        return List.of(List.of("a"), List.of("b"));
                    }

                    void check() {
                        List<String> expectedElement = List.of("a");
                        List<List<String>> expectedElements = List.of(List.of("a"), List.of("b"));
                        await(Contract::nested).until(contains(expectedElement));
                        await(Contract::nested).until(containsAll(expectedElements));
                        await(Contract::nested).until(containsExactlyElementsOf(expectedElements));
                    }
                }
                """));
    }

    @Test
    void optionalOverloadsAllowExplicitCallbackValues() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditioning.conditions.Conditions.yields;
                import static io.github.gromoff97.awium.conditioning.conditions.Conditions.equalTo;
                import static io.github.gromoff97.awium.conditioning.conditions.OptionalConditions.*;
                import io.github.gromoff97.awium.conditioning.conditions.Condition;
                import io.github.gromoff97.awium.conditioning.conditions.OptionalConditions;
                import io.github.gromoff97.awium.sources.Source.OptionalSource;
                import java.util.function.Predicate;

                final class Contract {
                    void check(OptionalSource<Class<?>> classes, Class<?> expectedClass,
                            OptionalSource<Condition<String, String>> conditions,
                            Condition<String, String> expectedCondition,
                            OptionalSource<Predicate<String>> predicates,
                            Predicate<String> expectedPredicate,
                            OptionalSource<Object> objects, OptionalSource<String> strings) {
                        Class<?> classValue = await(classes).until(hasValue(expectedClass));
                        Condition<String, String> conditionValue =
                                await(conditions).until(OptionalConditions.<Condition<String, String>>hasValue(expectedCondition));
                        Predicate<String> predicateValue =
                                await(predicates).until(OptionalConditions.<Predicate<String>>hasValue(expectedPredicate));
                        String typed = await(objects).until(containsInstanceOf(String.class));
                        String matching = await(strings).until(hasValue(value -> !value.isBlank()));
                        String satisfying = await(strings).until(hasValue(equalTo("ready")));
                        Integer transformed = await(strings).until(hasValue(yields(String::length)));
                    }
                }
                """));
    }

    @Test
    void wildcardImportedNamespacesAllowQualifiedNameCollisions() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionConditions.*;
                import static io.github.gromoff97.awium.conditioning.conditions.Conditions.*;
                import static io.github.gromoff97.awium.conditioning.conditions.MapConditions.*;
                import static io.github.gromoff97.awium.conditioning.conditions.OptionalConditions.*;
                import static io.github.gromoff97.awium.conditioning.conditions.StringConditions.*;
                import io.github.gromoff97.awium.conditioning.conditions.CollectionConditions;
                import io.github.gromoff97.awium.conditioning.conditions.MapConditions;
                import io.github.gromoff97.awium.conditioning.conditions.StringConditions;
                import io.github.gromoff97.awium.sources.Source;
                import io.github.gromoff97.awium.sources.Source.CollectionSource;
                import io.github.gromoff97.awium.sources.Source.MapSource;
                import io.github.gromoff97.awium.sources.Source.OptionalSource;
                import java.util.List;
                import java.util.Map;

                final class Contract {
                    void check(Source<String> text, Source<Object> object,
                            OptionalSource<String> optional,
                            CollectionSource<List<String>> collection,
                            MapSource<Map<String, Integer>> map) {
                        await(text).until(StringConditions.empty);
                        await(text).until(StringConditions.contains("x"));
                        await(text).until(matchesRegex("x"));
                        await(text).until(StringConditions.length(1));
                        await(object).until(matches(value -> true));
                        await(optional).until(hasValue("x"));
                        await(collection).until(CollectionConditions.nonEmpty);
                        await(collection).until(single);
                        await(collection).until(all(value -> true));
                        await(collection).until(CollectionConditions.size(1));
                        await(collection).until(CollectionConditions.startsWith("x"));
                        await(collection).until(containsExactly("x"));
                        await(map).until(MapConditions.nonEmpty);
                        await(map).until(singleEntry);
                        await(map).until(allEntries((key, value) -> true));
                        await(map).until(MapConditions.size(1));
                        await(map).until(valueFor("x"));
                        await(map).until(containsExactlyEntriesOf(Map.of("x", 1)));
                    }
                }
                """));
    }

    @Test
    void collectionExactFactoriesRespectOrderedSourceTyping()
            throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionConditions.*;
                import io.github.gromoff97.awium.sources.Source.CollectionSource;
                import java.util.*;

                final class Contract {
                    static List<String> list() { return List.of("a", "b"); }

                    void check(CollectionSource<Collection<String>> collection,
                            Collection<String> expected) {
                        List<String> ordered = await(Contract::list).until(containsExactly("a", "b"));
                        await(Contract::list).until(doesNotContainExactly("b", "a")
                                .because("ordered"));
                        await(Contract::list).until(containsExactlyElementsOf(expected));
                        await(Contract::list).until(doesNotContainExactlyElementsOf(
                                expected).because("ordered"));

                        Collection<String> anyOrder = await(collection).until(containsExactlyInAnyOrder("a", "b"));
                        await(collection).until(doesNotContainExactlyInAnyOrder(
                                "a", "b").because("any order"));
                        await(collection).until(
                                containsExactlyInAnyOrderElementsOf(expected));
                        await(collection).until(
                                doesNotContainExactlyInAnyOrderElementsOf(expected)
                                        .because("any order"));

                    }
                }
                """));
    }

    @Test
    void orderedExactFactoriesRejectCollectionOnlySources() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionConditions.*;
                import io.github.gromoff97.awium.sources.Source.CollectionSource;
                import java.util.*;

                final class Contract {
                    void check(CollectionSource<Set<String>> source) {
                        await(source).until(containsExactly("a"));
                    }
                }
                """));
    }

    @Test
    void callbackFactoriesPreserveTheirResultTypesAndMayBeDecoratedOnce() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditioning.conditions.Conditions.*;

                final class Contract {
                    record Payment(long id) {}

                    static Payment payment() { return new Payment(42); }

                    void check() {
                        Payment unchanged = await(Contract::payment).until(asserted((Payment value) -> {}));
                        long id = await(Contract::payment).until(yields((Payment value) -> {
                            return value.id();
                        }));
                        asserted((Payment value) -> {}).because("first");
                        yields((Payment value) -> {
                            return value;
                        }).because("first");
                    }
                }
                """));
    }

    @Test
    void explainedConditionsCannotBeDecoratedAgain() throws IOException {
        for (String condition : List.of(
                "condition(\"x\", (Object value) -> Evaluation.satisfied(value))",
                "asserted((Object value) -> {})",
                "yields((Object value) -> { return value; })",
                "present",
                "CollectionConditions.nonEmpty",
                "CollectionConditions.single",
                "MapConditions.nonEmpty",
                "MapConditions.singleEntry")) {
            assertFalse(compiles("""
                    import static io.github.gromoff97.awium.conditioning.conditions.Conditions.*;
                    import static io.github.gromoff97.awium.conditioning.conditions.OptionalConditions.*;
                    import io.github.gromoff97.awium.conditioning.Evaluation;
                    import io.github.gromoff97.awium.conditioning.conditions.CollectionConditions;
                    import io.github.gromoff97.awium.conditioning.conditions.MapConditions;
                    final class Contract {
                        void check() {
                            %s.because("first").because("second");
                        }
                    }
                    """.formatted(condition)), condition);
        }
    }

    @Test
    void constantConditionsAreFieldsNotFactories() throws IOException {
        for (String condition : List.of("CollectionConditions.empty()",
                "CollectionConditions.nonEmpty()", "MapConditions.empty()",
                "MapConditions.nonEmpty()",
                "CollectionConditions.single()",
                "CollectionConditions.first()", "CollectionConditions.last()",
                "MapConditions.singleEntry()")) {
            assertFalse(compiles("""
                    import io.github.gromoff97.awium.conditioning.conditions.CollectionConditions;
                    import io.github.gromoff97.awium.conditioning.conditions.MapConditions;
                    final class Contract {
                        void check() { Object condition = %s; }
                    }
                    """.formatted(condition)), condition);
        }
    }

    private boolean compiles(String source) throws IOException {
        return CompilationSupport.compiles(temporaryDirectory, source);
    }
}
