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
        for (String type : List.of("PresentCondition", "CollectionCondition.SingleElement",
                "MapCondition.SingleEntry")) {
            assertFalse(compiles("""
                    import static io.github.gromoff97.awium.await.Await.await;
                    import io.github.gromoff97.awium.sources.Source;
                    import io.github.gromoff97.awium.conditioning.conditions.*;
                    import io.github.gromoff97.awium.conditioning.conditions.Condition.PresentCondition;
                    final class Contract {
                        void check(Source<String> source, %s condition) {
                            await(source).until(condition);
                        }
                    }
                    """.formatted(type)), type);
        }
    }

    @Test
    void collectionAndMapConditionsCannotBeMixed() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import io.github.gromoff97.awium.conditioning.conditions.MapCondition;
                import io.github.gromoff97.awium.sources.Source.CollectionSource;
                import java.util.List;
                final class Contract {
                    void check(CollectionSource<List<String>> source) {
                        await(source).until(MapCondition.hasEntries);
                    }
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import io.github.gromoff97.awium.conditioning.conditions.CollectionCondition;
                import io.github.gromoff97.awium.sources.Source.MapSource;
                import java.util.Map;
                final class Contract {
                    void check(MapSource<Map<String, String>> source) {
                        await(source).until(CollectionCondition.hasElements);
                    }
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import io.github.gromoff97.awium.conditioning.conditions.MapCondition;
                import io.github.gromoff97.awium.sources.Source.CollectionSource;
                import java.util.List;
                final class Contract {
                    void check(CollectionSource<List<String>> source) {
                        await(source).until(MapCondition.singleEntry);
                    }
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import io.github.gromoff97.awium.conditioning.conditions.CollectionCondition;
                import io.github.gromoff97.awium.sources.Source.MapSource;
                import java.util.Map;
                final class Contract {
                    void check(MapSource<Map<String, String>> source) {
                        await(source).until(CollectionCondition.singleElement);
                    }
                }
                """));
    }

    @Test
    void singleTerminalsInferElementKeyAndValueTypes() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import io.github.gromoff97.awium.conditioning.conditions.CollectionCondition;
                import io.github.gromoff97.awium.conditioning.conditions.MapCondition;
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
                        String element = await(Contract::collection).until(CollectionCondition.singleElement);
                        Map.Entry<String, Integer> entry = await(Contract::map).every(Duration.ofMillis(1)).until(MapCondition.singleEntry.because("one entry"));
                    }
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.singleElement;
                import java.util.List;
                final class Contract {
                    void check() {
                        Integer wrong = await((io.github.gromoff97.awium.sources.Source.CollectionSource<List<String>>)
                                () -> List.of("value")).until(singleElement);
                    }
                }
                """));
    }

    @Test
    void singleElementIsBothAFieldAndAnOverloadedSelector() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.singleElement;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.singleElementOfType;
                import io.github.gromoff97.awium.conditioning.conditions.MapCondition;
                import io.github.gromoff97.awium.sources.Source.CollectionSource;
                import io.github.gromoff97.awium.sources.Source.MapSource;
                import java.util.List;
                import java.util.Map;

                final class Contract {
                    void check(CollectionSource<List<String>> strings,
                            CollectionSource<List<Object>> objects,
                            MapSource<Map<String, Integer>> map) {
                        String only = await(strings).until(singleElement);
                        String matching = await(strings).until(singleElement(value -> !value.isBlank()));
                        String narrowed = await(objects).until(singleElementOfType(String.class));
                        Map.Entry<String, Integer> entry = await(map).until(MapCondition.singleEntry);
                        Map.Entry<String, Integer> selected = await(map).until(MapCondition.singleEntry((key, value) -> value > 0));
                    }
                }
                """));
    }

    @Test
    void collectionElementAndAggregateFactoriesAreUnambiguous() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.*;
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
    void allConditionNamespacesCanBeWildcardImportedTogether() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.*;
                import static io.github.gromoff97.awium.conditioning.conditions.ComparableCondition.*;
                import static io.github.gromoff97.awium.conditioning.conditions.MapCondition.*;
                import static io.github.gromoff97.awium.conditioning.conditions.ObjectCondition.*;
                import static io.github.gromoff97.awium.conditioning.conditions.OptionalCondition.*;
                import static io.github.gromoff97.awium.conditioning.conditions.StringCondition.*;
                import static io.github.gromoff97.awium.conditioning.conditions.Condition.*;
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
                        await(text).until(empty);
                        await(text).until(containsText("x"));
                        await(text).until(matchesRegex("x"));
                        await(text).until(length(1));
                        await(object).until(matches(value -> true));
                        await(object).until(extracting(Object::toString, equalTo("x")));
                        await(optional).until(hasValue("x"));
                        await(collection).until(hasElements);
                        await(collection).until(singleElement);
                        await(collection).until(allMatch(value -> true));
                        await(collection).until(elementCount(1));
                        await(collection).until(startsWithElements("x"));
                        await(collection).until(containsExactly("x"));
                        await(map).until(hasEntries);
                        await(map).until(singleEntry);
                        await(map).until(allEntries((key, value) -> true));
                        await(map).until(entryCount(1));
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
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.*;
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
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.*;
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
                import static io.github.gromoff97.awium.conditioning.conditions.Condition.*;

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
                "CollectionCondition.hasElements",
                "CollectionCondition.singleElement",
                "MapCondition.hasEntries",
                "MapCondition.singleEntry")) {
            assertFalse(compiles("""
                    import static io.github.gromoff97.awium.conditioning.conditions.Condition.*;
                    import static io.github.gromoff97.awium.conditioning.conditions.OptionalCondition.*;
                    import io.github.gromoff97.awium.conditioning.Evaluation;
                    import io.github.gromoff97.awium.conditioning.conditions.CollectionCondition;
                    import io.github.gromoff97.awium.conditioning.conditions.MapCondition;
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
        for (String condition : List.of("CollectionCondition.noElements()",
                "CollectionCondition.hasElements()", "MapCondition.noEntries()",
                "MapCondition.hasEntries()",
                "CollectionCondition.singleElement()",
                "MapCondition.singleEntry()")) {
            assertFalse(compiles("""
                    import io.github.gromoff97.awium.conditioning.conditions.CollectionCondition;
                    import io.github.gromoff97.awium.conditioning.conditions.MapCondition;
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
