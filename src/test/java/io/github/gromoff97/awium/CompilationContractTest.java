package io.github.gromoff97.awium;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompilationContractTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void unrelatedFixtureFailuresCannotMasqueradeAsTypeRejections() {
        assertThrows(AssertionError.class,
                () -> compiles("final class Contract { void broken( }"));
        assertThrows(AssertionError.class, () -> compiles("""
                import missing.Type;
                final class Contract {}
                """));
        assertThrows(AssertionError.class, () -> compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import io.github.gromoff97.awium.Source;

                final class Contract {
                    void check(Source<String> source) {
                        await(source).utnil();
                    }
                }
                """));
    }

    @Test
    void exposesOneEntryPointAndFocusedConditionCatalogues() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.Await.*;
                import static io.github.gromoff97.awium.Conditions.*;
                import static io.github.gromoff97.awium.CollectionConditions.single;
                import static io.github.gromoff97.awium.MapConditions.singleEntry;
                import static io.github.gromoff97.awium.OptionalConditions.present;
                import static io.github.gromoff97.awium.StringConditions.nonBlank;
                import io.github.gromoff97.awium.AwaitResult;
                import io.github.gromoff97.awium.Source;
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
                        List<Payment> lifecycle = await(payment).until(matches(value -> value.status().equals("created")),
                                matches(value -> value.status().equals("finished")));
                        AwaitResult<String, String> diagnostic = await(text).tryUntil(nonBlank);
                        await(number).until(atLeast(1));
                        await(payment).until(asserted(value -> {}));
                    }
                }
                """));
    }

    @Test
    void conditionCallbacksComposeWithJdkInterfaces() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.ConditionResult.satisfied;
                import static io.github.gromoff97.awium.Conditions.*;
                import static io.github.gromoff97.awium.MapConditions.anyEntry;
                import java.util.function.*;

                final class Contract {
                    void check() {
                        Predicate<String> present = value -> value != null;
                        Predicate<String> text = present.and(value -> !value.isBlank());
                        matches(Predicate.not(text).negate());

                        Function<String, String> trim = String::trim;
                        yields(Function.<String>identity().andThen(trim)::apply);
                        condition("trimmed", trim.andThen(value -> satisfied(value))::apply);

                        Consumer<String> first = value -> {};
                        asserted(first.andThen(value -> {})::accept);

                        BiPredicate<String, Integer> entry = (key, value) -> value > 0;
                        anyEntry(entry.and((key, value) -> !key.isBlank()));
                    }
                }
                """));
    }

    @Test
    void methodReferencesRecoverSelectionFromPlainTypedSources() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.Await.*;
                import static io.github.gromoff97.awium.OptionalConditions.present;
                import static io.github.gromoff97.awium.CollectionConditions.single;
                import static io.github.gromoff97.awium.MapConditions.singleEntry;
                import io.github.gromoff97.awium.AwaitResult;
                import io.github.gromoff97.awium.Source;
                import java.util.*;

                final class Contract {
                    void check(Source<Optional<String>> optional, Source<List<String>> collection,
                            Source<Map<String, String>> map) {
                        String first = await(optional::get).until(present.because("required value"));
                        String second = await(collection::get).until(single.because("unique value"));
                        Map.Entry<String, String> entry = await(map::get).until(singleEntry.because("unique entry"));
                        AwaitResult<Optional<String>, String> result = await(optional::get).tryUntil(present);
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
                    import static io.github.gromoff97.awium.Await.await;
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
                import static io.github.gromoff97.awium.Await.await;
                final class Contract { void check() { await(() -> null); } }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                final class Contract { void check() { await(null); } }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import io.github.gromoff97.awium.Source;
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
                    import static io.github.gromoff97.awium.Await.await;
                    import io.github.gromoff97.awium.Source;
                    import io.github.gromoff97.awium.*;
                    import io.github.gromoff97.awium.Condition.SelectedCondition;
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
                    import static io.github.gromoff97.awium.Await.await;
                    import static io.github.gromoff97.awium.CollectionConditions.*;
                    import static io.github.gromoff97.awium.MapConditions.singleEntry;
                    import static io.github.gromoff97.awium.OptionalConditions.present;
                    import io.github.gromoff97.awium.Source;
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
    void sourceSelectedFieldsShareOneSourceTypedCondition() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.OptionalConditions.present;
                import io.github.gromoff97.awium.CollectionConditions;
                import io.github.gromoff97.awium.Condition.SelectedCondition;
                import io.github.gromoff97.awium.MapConditions;
                import java.util.Collection;
                import java.util.Map;
                import java.util.Optional;
                final class Contract {
                    SelectedCondition<Optional<?>, io.github.gromoff97.awium.Source.OptionalSource<?>> optional = present;
                    SelectedCondition<Collection<?>, io.github.gromoff97.awium.Source.CollectionSource<?>> collection = CollectionConditions.single;
                    SelectedCondition<Map<?, ?>, io.github.gromoff97.awium.Source.MapSource<?>> map = MapConditions.singleEntry;
                }
                """));
    }

    @Test
    void collectionAndMapConditionsCannotBeMixed() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import io.github.gromoff97.awium.MapConditions;
                import io.github.gromoff97.awium.Source.CollectionSource;
                import java.util.List;
                final class Contract {
                    void check(CollectionSource<List<String>> source) {
                        await(source).until(MapConditions.nonEmpty);
                    }
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import io.github.gromoff97.awium.CollectionConditions;
                import io.github.gromoff97.awium.Source.MapSource;
                import java.util.Map;
                final class Contract {
                    void check(MapSource<Map<String, String>> source) {
                        await(source).until(CollectionConditions.nonEmpty);
                    }
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import io.github.gromoff97.awium.MapConditions;
                import io.github.gromoff97.awium.Source.CollectionSource;
                import java.util.List;
                final class Contract {
                    void check(CollectionSource<List<String>> source) {
                        await(source).until(MapConditions.singleEntry);
                    }
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import io.github.gromoff97.awium.CollectionConditions;
                import io.github.gromoff97.awium.Source.MapSource;
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
                import static io.github.gromoff97.awium.Await.await;
                import io.github.gromoff97.awium.CollectionConditions;
                import io.github.gromoff97.awium.MapConditions;
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
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.single;
                import java.util.List;
                final class Contract {
                    void check() {
                        Integer wrong = await((io.github.gromoff97.awium.Source.CollectionSource<List<String>>)
                                () -> List.of("value")).until(single);
                    }
                }
                """));
    }

    @Test
    void specializedSourcesRetainSelectedResultTypes() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.first;
                import static io.github.gromoff97.awium.CollectionConditions.single;
                import static io.github.gromoff97.awium.Conditions.isNull;
                import static io.github.gromoff97.awium.MapConditions.singleEntry;
                import static io.github.gromoff97.awium.OptionalConditions.present;
                import io.github.gromoff97.awium.Source.*;
                import java.util.*;
                final class Contract {
                    void check(OptionalSource<String> optional,
                            CollectionSource<List<String>> collection,
                            MapSource<Map<String, Integer>> map) {
                        String optionalResult = await(optional).until(present);
                        OptionalSource<String> nullSource = () -> null;
                        Void nullResult = await(nullSource).until(isNull);
                        String collectionResult = await(collection).until(single);
                        String firstResult = await(collection).until(first);
                        Map.Entry<String, Integer> mapResult = await(map).until(singleEntry);
                    }
                }
                """));
    }

    @Test
    void wildcardStructuredSourcesRetainSelectedTypes() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.single;
                import static io.github.gromoff97.awium.MapConditions.singleEntry;
                import io.github.gromoff97.awium.AwaitResult;
                import io.github.gromoff97.awium.Source.CollectionViewSource;
                import io.github.gromoff97.awium.Source.MapViewSource;
                import java.util.List;
                import java.util.Map;

                final class Contract {
                    static List<? extends Number> collection() {
                        return List.of(1);
                    }
                    static Map<? extends Number, ? extends CharSequence> map() {
                        return Map.of(1, "one");
                    }

                    void create() {
                        check(new CollectionViewSource<Number,
                                        List<? extends Number>>(Contract::collection),
                                new MapViewSource<Number, CharSequence,
                                        Map<? extends Number, ? extends CharSequence>>(Contract::map));
                    }

                    void check(CollectionViewSource<Number, List<? extends Number>> collection,
                            MapViewSource<Number, CharSequence,
                                    Map<? extends Number, ? extends CharSequence>> map) {
                        Number element = await(collection).until(single);
                        AwaitResult<List<? extends Number>, Number> collectionResult =
                                await(collection).tryUntil(single);
                        Map.Entry<? extends Number, ? extends CharSequence> entry =
                                await(map).until(singleEntry);
                        AwaitResult<Map<? extends Number, ? extends CharSequence>,
                                Map.Entry<? extends Number, ? extends CharSequence>> mapResult =
                                await(map).tryUntil(singleEntry);
                    }
                }
                """));
    }

    @Test
    void singleElementIsBothAFieldAndAnOverloadedSelector() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.Conditions.instanceOf;
                import static io.github.gromoff97.awium.Conditions.matches;
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.single;
                import io.github.gromoff97.awium.MapConditions;
                import io.github.gromoff97.awium.Source.CollectionSource;
                import io.github.gromoff97.awium.Source.MapSource;
                import java.util.List;
                import java.util.Map;

                final class Contract {
                    void check(CollectionSource<List<String>> strings,
                            CollectionSource<List<Object>> objects,
                            MapSource<Map<String, Integer>> map) {
                        String only = await(strings).until(single);
                        String matching = await(strings).until(single(matches(value -> !value.isBlank())));
                        String narrowed = await(objects).until(single(instanceOf(String.class)));
                        Map.Entry<String, Integer> entry = await(map).until(MapConditions.singleEntry);
                        Map.Entry<String, Integer> selected = await(map).until(MapConditions.singleEntry(matches(candidate -> candidate.getValue() > 0)));
                    }
                }
                """));
    }

    @Test
    void firstAndLastAreTypedFieldsWithPredicateOverloads() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.first;
                import static io.github.gromoff97.awium.CollectionConditions.last;
                import io.github.gromoff97.awium.Source.CollectionSource;
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
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.first;
                import io.github.gromoff97.awium.Source.CollectionSource;
                import java.util.List;
                final class Contract {
                    void check(CollectionSource<List<String>> source) {
                        Integer wrong = await(source).until(first);
                    }
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.first;
                import io.github.gromoff97.awium.Source.CollectionSource;
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
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.*;
                import java.util.List;

                final class Contract {
                    static List<List<String>> nested() {
                        return List.of(List.of("a"), List.of("b"));
                    }

                    void check() {
                        List<String> expectedElement = List.of("a");
                        List<List<String>> expectedElements = List.of(List.of("a"), List.of("b"));
                        await(Contract::nested).until(contains(List.of(expectedElement)));
                        await(Contract::nested).until(contains(expectedElements));
                        await(Contract::nested).until(containsExactly(expectedElements));
                    }
                }
                """));
    }

    @Test
    void optionalOverloadsAllowExplicitCallbackValues() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.Conditions.instanceOf;
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.Conditions.equalTo;
                import static io.github.gromoff97.awium.Conditions.matches;
                import static io.github.gromoff97.awium.Conditions.yields;
                import static io.github.gromoff97.awium.OptionalConditions.*;
                import io.github.gromoff97.awium.Condition;
                import io.github.gromoff97.awium.OptionalConditions;
                import io.github.gromoff97.awium.Source.OptionalSource;
                import java.util.function.Predicate;

                final class Contract {
                    void check(OptionalSource<Class<?>> classes, Class<?> expectedClass,
                            OptionalSource<Condition<String, String>> conditions,
                            Condition<String, String> expectedCondition,
                            OptionalSource<Predicate<String>> predicates,
                            Predicate<String> expectedPredicate,
                            OptionalSource<Object> objects, OptionalSource<String> strings) {
                        Class<?> classValue = await(classes).until(hasValue(equalTo(expectedClass)));
                        Condition<String, String> conditionValue =
                                await(conditions).until(hasValue(equalTo(expectedCondition)));
                        Predicate<String> predicateValue =
                                await(predicates).until(hasValue(equalTo(expectedPredicate)));
                        String typed = await(objects).until(hasValue(instanceOf(String.class)));
                        String matching = await(strings).until(hasValue(matches(value -> !value.isBlank())));
                        String satisfying = await(strings).until(hasValue(equalTo("ready")));
                        Integer transformed = await(strings).until(hasValue(yields(String::length)));
                    }
                }
                """));
    }

    @Test
    void wildcardImportedNamespacesAllowQualifiedNameCollisions() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.*;
                import static io.github.gromoff97.awium.Conditions.*;
                import static io.github.gromoff97.awium.MapConditions.*;
                import static io.github.gromoff97.awium.OptionalConditions.*;
                import static io.github.gromoff97.awium.StringConditions.*;
                import io.github.gromoff97.awium.CollectionConditions;
                import io.github.gromoff97.awium.MapConditions;
                import io.github.gromoff97.awium.StringConditions;
                import io.github.gromoff97.awium.Source;
                import io.github.gromoff97.awium.Source.CollectionSource;
                import io.github.gromoff97.awium.Source.MapSource;
                import io.github.gromoff97.awium.Source.OptionalSource;
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
                        await(optional).until(hasValue(equalTo("x")));
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
                        Map<String, Integer> keys = await(map).until(containsKeys("x"));
                        Map<String, Integer> values = await(map).until(containsValues(1));
                        await(map).until(containsKeys("x", "y"));
                        await(map).until(containsValues(1, 2));
                        await(map).tryUntil(doesNotContainKeys("z"));
                        await(map).tryUntil(doesNotContainValues(3));
                        Map<String, Integer> entry = await(map).until(containsEntries("x", 1));
                        Map<String, Integer> entries = await(map).until(containsEntries(Map.of("x", 1, "y", 2)));
                        await(map).tryUntil(doesNotContainEntries("z", 3));
                        await(map).tryUntil(doesNotContainEntries(Map.of("z", 3)));
                        await(map).until(containsExactlyEntries(Map.of("x", 1)));
                    }
                }
                """));
    }

    @Test
    void mapMembershipConditionsRejectIncompatibleKeysAndValues() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String condition : List.of("containsKeys(1)", "containsValues(\"wrong\")",
                    "containsEntries(1, 1)", "containsEntries(\"x\", \"wrong\")",
                    "containsEntries(Map.of(1, 1))", "containsEntries(Map.of(\"x\", \"wrong\"))")) {
                assertFalse(compiles("""
                        import static io.github.gromoff97.awium.Await.await;
                        import static io.github.gromoff97.awium.MapConditions.*;
                        import io.github.gromoff97.awium.Source.MapSource;
                        import java.util.Map;

                        final class Contract {
                            void check(MapSource<Map<String, Integer>> source) {
                                await(source).%s(%s);
                            }
                        }
                        """.formatted(terminal, condition)), terminal + ": " + condition);
            }
        }
    }

    @Test
    void conditionNamesAcceptEvaluatorsFactoriesAndMethodReferences() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.Conditions.*;
                import io.github.gromoff97.awium.CheckedFunction;
                import io.github.gromoff97.awium.ConditionResult;
                import io.github.gromoff97.awium.Source;
                import java.util.List;
                import java.util.concurrent.Callable;

                final class Contract {
                    static CheckedFunction<String, ConditionResult<String>> evaluator() {
                        return ConditionResult::satisfied;
                    }
                    void check(Source<String> source,
                            Callable<CheckedFunction<String, ConditionResult<Integer>>> factory,
                            Callable<CheckedFunction<CharSequence, ConditionResult<CharSequence>>> broadFactory) {
                        String direct = await(source).until(condition("direct", ConditionResult::satisfied));
                        String created = await(source).until(condition("created", Contract::evaluator));
                        Integer transformed = await(source).until(condition("transformed", factory));
                        String preserved = await(source).until(preserving("preserved", Contract::evaluator));
                        String concrete = await(source).until(preserving("broad", broadFactory));
                        String explained = await(source).until(condition("explained", Contract::evaluator).because("ready"));
                        List<String> captured = await(source).until(condition("first", Contract::evaluator),
                                condition("second", Contract::evaluator));
                        String stateful = await(source).until(condition("stateful", () -> {
                            int[] attempts = {0};
                            return (String value) -> ++attempts[0] == 2
                                    ? ConditionResult.satisfied(value) : ConditionResult.unsatisfied("not yet");
                        }));
                    }
                }
                """));
    }

    @Test
    void lazyConditionsRejectAnIncompatibleObservedType() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String factory : List.of("condition", "preserving")) {
                assertFalse(compiles("""
                        import static io.github.gromoff97.awium.Await.await;
                        import static io.github.gromoff97.awium.Conditions.*;
                        import io.github.gromoff97.awium.CheckedFunction;
                        import io.github.gromoff97.awium.ConditionResult;
                        import io.github.gromoff97.awium.Source;

                        final class Contract {
                            static CheckedFunction<String, ConditionResult<String>> evaluator() {
                                return ConditionResult::satisfied;
                            }
                            void check(Source<Integer> source) {
                                await(source).%s(%s("typed", Contract::evaluator));
                            }
                        }
                        """.formatted(terminal, factory)), terminal + ": " + factory);
            }
        }
    }

    @Test
    void collectionExactFactoriesRespectOrderedSourceTyping()
            throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.*;
                import io.github.gromoff97.awium.Source.CollectionSource;
                import java.util.*;

                final class Contract {
                    static List<String> list() { return List.of("a", "b"); }

                    void check(CollectionSource<Collection<String>> collection,
                            Collection<String> expected) {
                        List<String> ordered = await(Contract::list).until(containsExactly("a", "b"));
                        await(Contract::list).until(doesNotContainExactly("b", "a")
                                .because("ordered"));
                        await(Contract::list).until(containsExactly(expected));
                        await(Contract::list).until(doesNotContainExactly(
                                expected).because("ordered"));

                        Collection<String> anyOrder = await(collection).until(containsExactlyInAnyOrder("a", "b"));
                        await(collection).until(doesNotContainExactlyInAnyOrder(
                                "a", "b").because("any order"));
                        await(collection).until(containsExactlyInAnyOrder(expected));
                        await(collection).until(doesNotContainExactlyInAnyOrder(expected).because("any order"));

                    }
                }
                """));
    }

    @Test
    void orderedExactFactoriesRejectCollectionOnlySources() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.*;
                import io.github.gromoff97.awium.Source.CollectionSource;
                import java.util.*;

                final class Contract {
                    void check(CollectionSource<Set<String>> source) {
                        await(source).until(containsExactly("a"));
                    }
                }
                """));
    }

    @Test
    void callbackFactoriesPreserveTheirResultTypesAndMayBeExplained() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.Conditions.*;

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
    void explainedConditionsCanReplaceTheirExplanation() throws IOException {
        for (String condition : List.of(
                "condition(\"x\", (Object value) -> ConditionResult.satisfied(value))",
                "asserted((Object value) -> {})",
                "yields((Object value) -> { return value; })",
                "present",
                "CollectionConditions.nonEmpty",
                "CollectionConditions.single",
                "MapConditions.nonEmpty",
                "MapConditions.singleEntry")) {
            assertTrue(compiles("""
                    import static io.github.gromoff97.awium.Conditions.*;
                    import static io.github.gromoff97.awium.OptionalConditions.*;
                    import io.github.gromoff97.awium.ConditionResult;
                    import io.github.gromoff97.awium.CollectionConditions;
                    import io.github.gromoff97.awium.MapConditions;
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
            String method = condition.substring(condition.lastIndexOf('.') + 1,
                    condition.indexOf('('));
            assertFalse(compiles("""
                    import io.github.gromoff97.awium.CollectionConditions;
                    import io.github.gromoff97.awium.MapConditions;
                    final class Contract {
                        void check() { Object condition = %s; }
                    }
                    """.formatted(condition), method), condition);
        }
    }

    private boolean compiles(String source) throws IOException {
        return CompilationSupport.compiles(temporaryDirectory, source);
    }

    private boolean compiles(String source, String expectedMissingMethod)
            throws IOException {
        return CompilationSupport.compiles(temporaryDirectory, source,
                expectedMissingMethod);
    }
}
