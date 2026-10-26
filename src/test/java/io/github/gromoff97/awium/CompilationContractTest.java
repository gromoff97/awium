package io.github.gromoff97.awium;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CompilationContractTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void nestedQuantifiersAndOnlyValueForRejectIncompatibleInputsAndResults() throws IOException {
        for (String body : List.of(
                "awaitCollection(() -> users).until(all(matches(String::isBlank).because(\"wrong\")));",
                "awaitCollection(() -> users).until(any(equalTo(123)));",
                "awaitCollection(() -> users).until(none(OptionalConditions.hasValue(instanceOf(User.class))));",
                "User wrong = awaitCollection(() -> users).until(all(matches(User::active)));",
                "awaitMap(() -> map).until(MapConditions.allKeys(matches(User::active)));",
                "awaitMap(() -> map).until(MapConditions.anyKey(equalTo(123)));",
                "awaitMap(() -> map).until(MapConditions.noValue(matches(String::isBlank)));",
                "awaitMap(() -> map).until(MapConditions.allValues(equalTo(123).because(\"wrong\")));",
                "awaitMap(() -> map).until(MapConditions.anyEntry(matches((Map.Entry<Integer,User> e)->true)));",
                "awaitMap(() -> map).until(MapConditions.allEntries(matches((Map.Entry<String,Object> e)->e.setValue(new Object())!=null)));",
                "awaitMap(() -> map).until(MapConditions.noEntry(equalTo(Map.entry(123,new User(true)))));",
                "awaitMap(() -> map).until(MapConditions.allEntries(equalTo(Map.entry(\"user\",123))));",
                "awaitMap(() -> map).until(MapConditions.onlyValueFor(123,User::active));",
                "awaitMap(() -> map).until(MapConditions.onlyValueFor(\"user\",matches(String::isBlank)));",
                "awaitMap(() -> map).until(MapConditions.onlyValueFor(\"user\",equalTo(123)));",
                "String wrong = awaitMap(() -> map).until(MapConditions.onlyValueFor(\"user\",instanceOf(User.class)));")) {
            assertRejected("""
                    import java.util.*;
                    import io.github.gromoff97.awium.MapConditions;
                    import io.github.gromoff97.awium.OptionalConditions;
                    import static io.github.gromoff97.awium.Conditions.await;
                    import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                    import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                    import static io.github.gromoff97.awium.MapConditions.awaitMap;
                    import static io.github.gromoff97.awium.Conditions.*;
                    import static io.github.gromoff97.awium.CollectionConditions.*;
                    final class Contract {
                        record User(boolean active) {}
                        void check(List<User> users, Map<String,User> map) { %s }
                    }
                    """.formatted(body));
        }
    }

    @Test
    void typedCollectionSelectionsKeepSourceAndResultConstraints() throws IOException {
        for (String body : List.of(
                "await(() -> \"wrong\").until(first(instanceOf(User.class)));",
                "awaitCollection(() -> Set.of(new User(1))).until(last(instanceOf(User.class)));",
                "awaitCollection(() -> new ArrayDeque<>(List.of(new User(1)))).until(element(0,instanceOf(User.class)));",
                "awaitCollection(() -> List.of(new User(1))).until(first(equalTo(123)).because(\"wrong comparison\"));",
                "awaitCollection(() -> List.of(new User(1))).until(single(String::isBlank).because(\"wrong input\"));",
                "awaitOptional(() -> Optional.of(new User(1))).until(OptionalConditions.hasValue(String::isBlank));",
                "awaitMap(() -> Map.of(\"user\",new User(1))).until(MapConditions.valueFor(\"user\",String::isBlank));",
                "awaitMap(() -> Map.of(\"user\",new User(1))).until(MapConditions.singleEntry((Integer key,User user)->true));",
                "awaitMap(() -> Map.of(\"user\",new User(1))).until(MapConditions.singleEntry((String key,String user)->true));",
                "awaitMap(() -> Map.of(\"user\",new User(1))).until(MapConditions.singleEntry((String key,User user)->\"wrong boolean\"));",
                "awaitCollection(() -> List.of(new User(1))).until(last(matches(String::isEmpty)).because(\"wrong input\"));",
                "awaitCollection(() -> List.of(new User(1))).until(element(0,equalTo(123)));",
                "String wrong = awaitCollection(() -> List.of(new User(1))).until(first(isNotNull).because(\"wrong result\"));",
                "awaitCollection(() -> Set.of(new User(1))).until(first(isNotNull));",
                "awaitCollection(() -> new ArrayDeque<>(List.of(new User(1)))).until(element(0,isNotNull));",
                "awaitCollection(() -> List.of(\"ready\")).until(last(equalTo(new Object())));",
                "String wrong = awaitCollection(() -> values).until(first(instanceOf(User.class)).because(\"wrong result\"));",
                "String wrong = await(() -> new User(1)).until(matches((User user)->true));",
                "await(() -> new User(1)).until(matches(io.github.gromoff97.awium.CheckedPredicate.not(String::isBlank)).because(\"wrong input\"));",
                "await(() -> new User(1)).until(matches(io.github.gromoff97.awium.CheckedPredicate.not((User user)->\"wrong boolean\")));",
                "awaitMap(() -> Map.of(\"u\",new User(1))).until(MapConditions.singleEntry(io.github.gromoff97.awium.CheckedBiPredicate.not((Integer key,User user)->true)));",
                "awaitCollection(() -> values).until(first(instanceOf(User.class)),first(instanceOf(Integer.class)));",
                "AwaitResult<List<Object>,String> wrong = awaitCollection(() -> values).tryUntil(last(instanceOf(User.class)));",
                "awaitCollection(() -> values).until(first(instanceOf(User.class)),last(instanceOf(String.class)));",
                "awaitCollection(() -> values).<Object>until(first(instanceOf(User.class)),last(instanceOf(String.class)));",
                "awaitCollection(() -> values).tryUntil(element(0,instanceOf(User.class)).because(\"user\"),first(instanceOf(Integer.class)).because(\"number\"));")) {
            assertRejected("""
                    import java.util.*;
                    import io.github.gromoff97.awium.AwaitResult;
                    import io.github.gromoff97.awium.MapConditions;
                    import io.github.gromoff97.awium.OptionalConditions;
                    import static io.github.gromoff97.awium.Conditions.await;
                    import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                    import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                    import static io.github.gromoff97.awium.MapConditions.awaitMap;
                    import static io.github.gromoff97.awium.Conditions.*;
                    import static io.github.gromoff97.awium.CollectionConditions.*;
                    final class Contract {
                        record User(int id) {}
                        void check(List<Object> values) { %s }
                    }
                    """.formatted(body));
        }
    }

    @Test
    void mapsThatAlsoIterateValuesCannotConfuseTheirSelectionResults() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String condition : List.of("single", "single(User::active)", "single(matches(User::active))", "single(equalTo(new User(true)))")) {
                assertRejected("""
                        import java.util.*;
                        import static io.github.gromoff97.awium.Conditions.await;
                        import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                        import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                        import static io.github.gromoff97.awium.MapConditions.awaitMap;
                        import static io.github.gromoff97.awium.Conditions.*;
                        import static io.github.gromoff97.awium.CollectionConditions.*;
                        final class Contract {
                            record User(boolean active) {}
                            static final class Both extends AbstractMap<String,Integer> implements Iterable<User> {
                                public Set<Map.Entry<String,Integer>> entrySet() { return Set.of(Map.entry("key", 10)); }
                                public Iterator<User> iterator() { return List.of(new User(true)).iterator(); }
                            }
                            void check(java.util.concurrent.Callable<Both> source) { awaitMap(source).%s(%s.because("wrong source family")); }
                        }
                        """.formatted(terminal, condition));
            }
        }
    }

    @Test
    void expectedIterablesDoNotWeakenElementKeyOrValueConstraints() throws IOException {
        for (String expression : List.of(
                "await(() -> \"ready\").until(in(wrong).because(\"wrong\"));",
                "await(() -> \"ready\").until(notIn(wrong));",
                "awaitCollection(() -> List.of(\"ready\")).until(containsAll(wrong));",
                "awaitCollection(() -> List.of(\"ready\")).until(containsExactly(wrong).because(\"wrong\"));",
                "awaitCollection(() -> List.of(\"ready\")).until(containsOnly(wrong));",
                "awaitCollection(() -> List.of(\"ready\")).until(startsWith(wrong));",
                "awaitMap(() -> Map.of(\"key\", \"ready\")).until(MapConditions.containsAllKeys(wrong));",
                "awaitMap(() -> Map.of(\"key\", \"ready\")).until(MapConditions.containsOnlyKeys(wrong));",
                "awaitMap(() -> Map.of(\"key\", \"ready\")).until(MapConditions.containsAllValues(wrong).because(\"wrong\"));")) {
            assertRejected("""
                    import java.util.*;
                    import io.github.gromoff97.awium.MapConditions;
                    import static io.github.gromoff97.awium.Conditions.await;
                    import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                    import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                    import static io.github.gromoff97.awium.MapConditions.awaitMap;
                    import static io.github.gromoff97.awium.Conditions.*;
                    import static io.github.gromoff97.awium.CollectionConditions.*;
                    final class Contract {
                        void check(Iterable<Integer> wrong) { %s }
                    }
                    """.formatted(expression));
        }
    }

    @Test
    void iterablesRejectIncompatibleQuantifiersResultsAndCollectionOnlyOperations() throws IOException {
        for (String expression : List.of(
                "await(() -> users).until(all(User::active));",
                "await(() -> users).until(any(matches(User::active)));",
                "await(() -> users).until(none(equalTo(new User(true))));",
                "await(() -> users).until(single(instanceOf(User.class)));",
                "await(() -> users).until(single(User::active));",
                "await(() -> users).until(all(String::isBlank).because(\"wrong\"));",
                "await(() -> users).until(any(String::isBlank));",
                "String wrong = await(() -> users).until(single(instanceOf(User.class)).because(\"wrong\"));",
                "await(() -> users).until(single.because(\"collection required\"));",
                "await(() -> users).until(first.because(\"unordered\"));",
                "await(() -> users).until(size(1));",
                "await(() -> users).until(present);",
                "await(() -> users).until(valueFor(\"key\"));",
                "await(() -> users).until(single(instanceOf(String.class)), single(instanceOf(Integer.class)));",
                "await(() -> broad).until(all(User::active));")) {
            assertRejected("""
                    import java.util.*;
                    import static io.github.gromoff97.awium.Conditions.await;
                    import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                    import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                    import static io.github.gromoff97.awium.MapConditions.awaitMap;
                    import static io.github.gromoff97.awium.Conditions.*;
                    import static io.github.gromoff97.awium.CollectionConditions.*;
                    import static io.github.gromoff97.awium.OptionalConditions.present;
                    import static io.github.gromoff97.awium.MapConditions.valueFor;
                    final class Contract {
                        record User(boolean active) {}
                        void check(Iterable<User> users, Iterable<? super User> broad) { %s }
                    }
                    """.formatted(expression));
        }
    }

    @Test
    void textExpectationsRejectNonTextValues() throws IOException {
        for (String method : List.of("contains", "doesNotContain", "containsIgnoringCase",
                "startsWith", "doesNotStartWith", "endsWith", "doesNotEndWith",
                "matchesRegex", "doesNotMatchRegex", "contentEquals", "equalToIgnoringCase", "notEqualToIgnoringCase")) {
            assertRejected("""
                    import io.github.gromoff97.awium.StringConditions;
                    final class Contract {
                        void check() { StringConditions.%s(42); }
                    }
                    """.formatted(method));
        }
    }

    @Test
    void conditionsAndResultsRejectUnrelatedTypes() throws IOException {
        for (String expression : List.of(
                "await(strings).until(equalTo(42));",
                "await(strings).until(matches(value -> \"wrong boolean\"));",
                "await(strings).until(matches(value -> null));",
                "await(() -> new String[]{\"ready\"}).until(in(java.util.Collections.singletonList(new Integer[]{1})).because(\"incompatible array\"));",
                "String wrong = await(objects).until(instanceOf(Integer.class));",
                "AwaitResult<Optional<Integer>, String> wrong = awaitOptional(optional).tryUntil(present);",
                "await(characters).until(equalTo(42));",
                "await(characters).until(equalTo(42).because(\"incompatible\"));",
                "await(characters).tryUntil(equalTo(42).because(\"%s\", \"incompatible\"));")) {
            assertRejected("""
                    import java.util.concurrent.Callable;
                    import io.github.gromoff97.awium.*;
                    import java.util.Optional;
                    import static io.github.gromoff97.awium.Conditions.await;
                    import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                    import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                    import static io.github.gromoff97.awium.MapConditions.awaitMap;
                    import static io.github.gromoff97.awium.Conditions.*;
                    import static io.github.gromoff97.awium.OptionalConditions.present;
                    final class Contract {
                        void check(Callable<String> strings, Callable<Object> objects,
                                Callable<CharSequence> characters, java.util.concurrent.Callable<java.util.Optional<Integer>> optional) {
                            %s
                        }
                    }
                    """.formatted(expression));
        }
    }

    @Test
    void comparableAndTextChecksEnforceTheirInputContracts() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String condition : List.of("greaterThan(1)", "atLeast(1)", "lessThan(1)",
                    "atMost(1)", "between(1, 2)", "strictlyBetween(1, 2)")) {
                assertRejected("""
                        import static io.github.gromoff97.awium.Conditions.await;
                        import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                        import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                        import static io.github.gromoff97.awium.MapConditions.awaitMap;
                        import static io.github.gromoff97.awium.Conditions.*;
                        final class Contract {
                            void check() {
                                await(() -> "ready").%s(%s.because("incompatible bound"));
                            }
                        }
                        """.formatted(terminal, condition));
            }
            for (String condition : List.of("nonBlank", "length(1)", "contentEquals(\"ready\")")) {
                assertRejected("""
                        import static io.github.gromoff97.awium.Conditions.await;
                        import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                        import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                        import static io.github.gromoff97.awium.MapConditions.awaitMap;
                        import io.github.gromoff97.awium.StringConditions;
                        final class Contract {
                            void check() {
                                await(() -> 1).%s(StringConditions.%s.because("incompatible text"));
                            }
                        }
                        """.formatted(terminal, condition));
            }
            for (String condition : List.of("between", "strictlyBetween")) {
                assertRejected("""
                        import static io.github.gromoff97.awium.Conditions.await;
                        import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                        import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                        import static io.github.gromoff97.awium.MapConditions.awaitMap;
                        import static io.github.gromoff97.awium.Conditions.*;
                        final class Contract {
                            record Limit(int number) {}
                            record Measurement(int number) implements Comparable<Limit> {
                                public int compareTo(Limit limit) { return Integer.compare(number, limit.number()); }
                            }
                            void check() {
                                await(() -> new Measurement(10)).%s(%s(new Limit(5), new Limit(15)).because("unordered limits"));
                            }
                        }
                        """.formatted(terminal, condition));
            }
        }
    }

    @Test
    void collectionExpectationsRejectUnrelatedElementTypes() throws IOException {
        for (String condition : List.of("contains(42)", "containsAll(List.of(42))",
                "doesNotContain(42)", "doesNotContainAny(List.of(42))",
                "doesNotContainAll(List.of(42))", "containsAny(List.of(42))",
                "containsExactly(List.of(42))", "doesNotContainExactly(List.of(42))",
                "containsExactlyInAnyOrder(List.of(42))", "doesNotContainExactlyInAnyOrder(List.of(42))",
                "containsOnly(List.of(42))",
                "startsWith(List.of(42))", "endsWith(List.of(42))",
                "containsSequence(List.of(42))", "doesNotContainSequence(List.of(42))",
                "containsSubsequence(List.of(42))", "doesNotContainSubsequence(List.of(42))",
                "contains(42).because(\"incompatible\")",
                "contains(42).because(\"%s\", \"incompatible\")",
                "containsExactly(List.of(42)).because(\"incompatible\")",
                "containsExactly(List.of(42)).because(\"%s\", \"incompatible\")")) {
            for (String terminal : List.of("until", "tryUntil")) {
                String expression = "awaitCollection(source)." + terminal + "(CollectionConditions." + condition + ");";
                assertRejected("""
                        import java.util.List;
                        import java.util.concurrent.Callable;
                        import io.github.gromoff97.awium.CollectionConditions;
                        import static io.github.gromoff97.awium.Conditions.await;
                        import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                        import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                        import static io.github.gromoff97.awium.MapConditions.awaitMap;
                        final class Contract {
                            void check(Callable<List<String>> source) {
                                %s
                            }
                        }
                        """.formatted(expression));
            }
        }
    }

    @Test
    void negativeFixturesRejectValidCodeAndUnrelatedErrors() {
        assertThrows(AssertionError.class,
                () -> assertRejected("final class Contract {}"));
        assertThrows(AssertionError.class,
                () -> assertRejected("final class Contract { void broken( }"));
        assertThrows(AssertionError.class, () -> assertRejected("""
                import missing.Type;
                final class Contract {}
                """));
        assertThrows(AssertionError.class, () -> assertRejected("""
                import static io.github.gromoff97.awium.Conditions.await;
                import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                import static io.github.gromoff97.awium.MapConditions.awaitMap;
                import java.util.concurrent.Callable;

                final class Contract {
                    void check(Callable<String> source) {
                        await(source).utnil();
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
            assertRejected("""
                    import static io.github.gromoff97.awium.Conditions.await;
                    import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                    import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                    import static io.github.gromoff97.awium.MapConditions.awaitMap;
                    final class Contract {
                        void check() {
                            %s
                            await(source);
                        }
                    }
                    """.formatted(declaration));
        }
    }

    @Test
    void namedFactoriesRejectWrongSourceCategories() throws IOException {
        for (String expression : List.of(
                "await(() -> Optional.of(\"ready\")).until(present);",
                "a(() -> List.of(\"ready\")).until(single);",
                "awaitCollection(() -> Optional.of(\"ready\"));",
                "ac(() -> Map.of(\"key\", \"ready\"));",
                "awaitOptional(() -> \"ready\");",
                "ao(() -> List.of(\"ready\"));",
                "awaitMap(() -> List.of(\"ready\"));",
                "am(() -> Optional.of(\"ready\"));")) {
            assertRejected("""
                    import java.util.*;
                    import static io.github.gromoff97.awium.Conditions.*;
                    import static io.github.gromoff97.awium.CollectionConditions.*;
                    import static io.github.gromoff97.awium.OptionalConditions.*;
                    import static io.github.gromoff97.awium.MapConditions.*;
                    final class Contract {
                        void check() { %s }
                    }
                    """.formatted(expression));
        }
    }

    @Test
    void categorySpecificTerminalsRejectWrongConditions() throws IOException {
        for (String type : List.of(
                "SelectedCondition<java.util.Optional<?>, Object, io.github.gromoff97.awium.Condition.Reference<?>>",
                "SelectedCondition<java.util.Collection<?>, Object, io.github.gromoff97.awium.Condition.Reference<?>>",
                "SelectedCondition<java.util.Map<?, ?>, Object, io.github.gromoff97.awium.Condition.Reference<?>>")) {
            assertRejected("""
                    import static io.github.gromoff97.awium.Conditions.await;
                    import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                    import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                    import static io.github.gromoff97.awium.MapConditions.awaitMap;
                    import java.util.concurrent.Callable;
                    import io.github.gromoff97.awium.*;
                    import io.github.gromoff97.awium.Condition.SelectedCondition;
                    final class Contract {
                        void check(Callable<String> source, %s condition) {
                            await(source).until(condition);
                        }
                    }
                    """.formatted(type));
        }
    }

    @Test
    void jdkCallableSelectionsRejectIncompatibleResultTypes() throws IOException {
        for (String condition : List.of("present", "single", "first", "last", "singleEntry")) {
            String source = condition.equals("present") ? "optional" : condition.equals("singleEntry") ? "map" : "collection";
            String factory = condition.equals("present") ? "awaitOptional" : condition.equals("singleEntry") ? "awaitMap" : "awaitCollection";
            String contract = """
                    import static io.github.gromoff97.awium.Conditions.await;
                    import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                    import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                    import static io.github.gromoff97.awium.MapConditions.awaitMap;
                    import static io.github.gromoff97.awium.CollectionConditions.*;
                    import static io.github.gromoff97.awium.MapConditions.singleEntry;
                    import static io.github.gromoff97.awium.OptionalConditions.present;
                    import java.util.concurrent.Callable;
                    import java.util.*;
                    final class Contract {
                        void check(Callable<Optional<String>> optional,
                                Callable<List<String>> collection,
                                Callable<Map<String, String>> map) {
                            %s result = %s(%s).until(%s);
                        }
                    }
                    """;
            assertRejected(contract.formatted("Integer", factory, source, condition));
            assertRejected(contract.formatted("Integer", factory, source, condition + ".because(\"required\")"));
        }
    }

    @Test
    void collectionAndMapConditionsCannotBeMixed() throws IOException {
        assertRejected("""
                import static io.github.gromoff97.awium.Conditions.await;
                import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                import static io.github.gromoff97.awium.MapConditions.awaitMap;
                import io.github.gromoff97.awium.MapConditions;
                import java.util.List;
                final class Contract {
                    void check(java.util.concurrent.Callable<List<String>> source) {
                        awaitCollection(source).until(MapConditions.nonEmpty);
                    }
                }
                """);
        assertRejected("""
                import static io.github.gromoff97.awium.Conditions.await;
                import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                import static io.github.gromoff97.awium.MapConditions.awaitMap;
                import io.github.gromoff97.awium.CollectionConditions;
                import java.util.Map;
                final class Contract {
                    void check(java.util.concurrent.Callable<Map<String, String>> source) {
                        awaitMap(source).until(CollectionConditions.nonEmpty);
                    }
                }
                """);
        assertRejected("""
                import static io.github.gromoff97.awium.Conditions.await;
                import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                import static io.github.gromoff97.awium.MapConditions.awaitMap;
                import io.github.gromoff97.awium.MapConditions;
                import java.util.List;
                final class Contract {
                    void check(java.util.concurrent.Callable<List<String>> source) {
                        awaitCollection(source).until(MapConditions.singleEntry);
                    }
                }
                """);
        assertRejected("""
                import static io.github.gromoff97.awium.Conditions.await;
                import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                import static io.github.gromoff97.awium.MapConditions.awaitMap;
                import io.github.gromoff97.awium.CollectionConditions;
                import java.util.Map;
                final class Contract {
                    void check(java.util.concurrent.Callable<Map<String, String>> source) {
                        awaitMap(source).until(CollectionConditions.single);
                    }
                }
                """);
    }

    @Test
    void collectionSelectionRejectsAnUnrelatedElementType() throws IOException {
        assertRejected("""
                import static io.github.gromoff97.awium.Conditions.await;
                import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                import static io.github.gromoff97.awium.MapConditions.awaitMap;
                import static io.github.gromoff97.awium.CollectionConditions.single;
                import java.util.List;
                final class Contract {
                    void check() {
                        Integer wrong = awaitCollection((java.util.concurrent.Callable<List<String>>)
                                () -> List.of("value")).until(single);
                    }
                }
                """);
    }

    @Test
    void orderedSelectionRejectsUnrelatedResultsAndUnorderedSources() throws IOException {
        assertRejected("""
                import static io.github.gromoff97.awium.Conditions.await;
                import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                import static io.github.gromoff97.awium.MapConditions.awaitMap;
                import static io.github.gromoff97.awium.CollectionConditions.first;
                import java.util.List;
                final class Contract {
                    void check(java.util.concurrent.Callable<List<String>> source) {
                        Integer wrong = awaitCollection(source).until(first);
                    }
                }
                """);
        assertRejected("""
                import static io.github.gromoff97.awium.Conditions.await;
                import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                import static io.github.gromoff97.awium.MapConditions.awaitMap;
                import static io.github.gromoff97.awium.CollectionConditions.first;
                import java.util.HashSet;
                final class Contract {
                    void check(java.util.concurrent.Callable<HashSet<String>> source) {
                        awaitCollection(source).until(first);
                    }
                }
                """);
    }

    @Test
    void mapMembershipConditionsRejectIncompatibleKeysAndValues() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String explanation : List.of("", ".because(\"incompatible\")")) {
                for (String condition : List.of("containsKey(1)", "containsValue(\"wrong\")",
                        "containsAllKeys(List.of(1))", "containsAllValues(List.of(\"wrong\"))",
                        "containsOnlyKeys(List.of(1))",
                        "doesNotContainKey(1)", "doesNotContainValue(\"wrong\")",
                        "doesNotContainAnyKeys(List.of(1))", "doesNotContainAnyValues(List.of(\"wrong\"))",
                        "containsEntry(1, 1)", "containsEntry(\"x\", \"wrong\")",
                        "containsAllEntries(Map.of(1, 1))", "containsAllEntries(Map.of(\"x\", \"wrong\"))")) {
                    assertRejected("""
                            import static io.github.gromoff97.awium.Conditions.await;
                            import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                            import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                            import static io.github.gromoff97.awium.MapConditions.awaitMap;
                            import static io.github.gromoff97.awium.MapConditions.*;
                            import java.util.Map;
                            import java.util.List;

                            final class Contract {
                                void check(java.util.concurrent.Callable<Map<String, Integer>> source) {
                                    awaitMap(source).%s(%s%s);
                                }
                            }
                            """.formatted(terminal, condition, explanation));
                }
            }
        }
    }

    @Test
    void lazyConditionsRejectAnIncompatibleObservedType() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            assertRejected("""
                    import static io.github.gromoff97.awium.Conditions.await;
                    import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                    import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                    import static io.github.gromoff97.awium.MapConditions.awaitMap;
                    import static io.github.gromoff97.awium.Conditions.*;
                    import io.github.gromoff97.awium.CheckedFunction;
                    import io.github.gromoff97.awium.ConditionResult;
                    import java.util.concurrent.Callable;

                    final class Contract {
                        static CheckedFunction<String, ConditionResult<String>> evaluator() {
                            return ConditionResult::satisfied;
                        }
                        void check(Callable<Integer> source) {
                            await(source).%s(condition("typed", Contract::evaluator));
                        }
                    }
                    """.formatted(terminal));
        }
    }

    @Test
    void orderedExactFactoriesRejectCollectionOnlySources() throws IOException {
        assertRejected("""
                import static io.github.gromoff97.awium.Conditions.await;
                import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                import static io.github.gromoff97.awium.MapConditions.awaitMap;
                import static io.github.gromoff97.awium.CollectionConditions.*;
                import java.util.*;

                final class Contract {
                    void check(java.util.concurrent.Callable<Set<String>> source) {
                        awaitCollection(source).until(containsExactly(List.of("a")));
                    }
                }
                """);
    }

    @Test
    void covariantEntryViewsKeepIndependentValueTypesAcrossStages() throws IOException {
        for (String decoration : List.of("", ".because(\"independent entries\")")) {
            assertRejected("""
                    import java.util.*;
                    import io.github.gromoff97.awium.Await.MapViewSource;
                    import static io.github.gromoff97.awium.Conditions.await;
                    import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
                    import static io.github.gromoff97.awium.OptionalConditions.awaitOptional;
                    import static io.github.gromoff97.awium.MapConditions.awaitMap;
                    import static io.github.gromoff97.awium.MapConditions.singleEntry;
                    final class Contract {
                        static <KEY, VALUE> void overwrite(List<Map.Entry<KEY, VALUE>> entries) {
                            entries.getFirst().setValue(entries.getLast().getValue());
                        }
                        void check(MapViewSource<Number, CharSequence,
                                Map<? extends Number, ? extends CharSequence>> source) {
                            overwrite(awaitMap(source).until(singleEntry%s, singleEntry%s));
                        }
                    }
                    """.formatted(decoration, decoration));
        }
    }

    private void assertRejected(String source) throws IOException {
        CompilationSupport.assertRejected(temporaryDirectory, source);
    }

}
