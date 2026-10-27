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
    void nestedQuantifiersRejectIncompatibleInputsAndResults() throws IOException {
        for (String body : List.of(
                "await(() -> users).until(all(matches(String::isBlank)));",
                "await(() -> users).until(any(equalTo(123)));",
                "await(() -> users).until(none(OptionalConditions.hasValue(instanceOf(User.class))));",
                "User wrong = await(() -> users).until(all(matches(User::active)));",
                "await(() -> map).until(MapConditions.allKeys(matches(User::active)));",
                "await(() -> map).until(MapConditions.anyKey(equalTo(123)));",
                "await(() -> map).until(MapConditions.noValue(matches(String::isBlank)));",
                "await(() -> map).until(MapConditions.allValues(equalTo(123)));",
                "await(() -> map).until(MapConditions.hasEntry(matches((Map.Entry<Integer,User> e)->true)));",
                "await(() -> map).until(MapConditions.hasEntry(matches((Map.Entry<String,String> e)->true)).because(\"reason\"));",
                "await(() -> map).until(MapConditions.hasEntry(matches((Map.Entry<String,Object> e)->e.setValue(new Object())!=null)));",
                "await(() -> map).until(MapConditions.hasEntry(equalTo(Map.entry(123,new User(true)))).because(\"reason\"));",
                "await(() -> map).until(MapConditions.hasEntry(equalTo(Map.entry(\"user\",123))));",
                "Map.Entry<String,User> wrong = await(() -> map).until(MapConditions.hasEntry(\"user\",new User(true)));",
                "User wrong = await(() -> map).until(MapConditions.hasEntry(matches((Map.Entry<String,User> e)->e.getValue().active())));",
                "await(() -> map).until(MapConditions.allEntries(matches((Map.Entry<String,Object> e)->e.setValue(new Object())!=null)));",
                "await(() -> map).until(MapConditions.allEntries((Map.Entry<Integer,User> e)->true));",
                "await(() -> map).until(MapConditions.allEntries((Map.Entry<String,String> e)->true));",
                "await(() -> map).until(MapConditions.allEntries((Map.Entry<String,Object> e)->e.setValue(new Object())!=null));",
                "await(() -> map).until(MapConditions.singleEntry((Map.Entry<Integer,User> e)->true));",
                "await(() -> map).until(MapConditions.singleEntry((Map.Entry<String,String> e)->true));",
                "await(() -> map).until(MapConditions.singleEntry((Map.Entry<String,Object> e)->e.setValue(new Object())!=null));",
                "Map<String,? extends User> view=map; await(() -> view).until(MapConditions.allEntries((Map.Entry<String,User> e)->e.setValue(new User(true))!=null));",
                "Map<String,? extends User> view=map; await(() -> view).until(MapConditions.singleEntry((Map.Entry<String,User> e)->e.setValue(new User(true))!=null));",
                "Map.Entry<String,User> wrong = await(() -> map).until(MapConditions.allEntries(e->e.getValue().active()));",
                "Map<String,User> wrong = await(() -> map).until(MapConditions.singleEntry(e->e.getValue().active()));",
                "await(() -> map).until(MapConditions.hasNoEntry(equalTo(Map.entry(123,new User(true)))));",
                "await(() -> map).until(MapConditions.allEntries(equalTo(Map.entry(\"user\",123))));")) {
            assertRejected("""
                    import java.util.*;
                    import io.github.gromoff97.awium.MapConditions;
                    import io.github.gromoff97.awium.OptionalConditions;
                    import static io.github.gromoff97.awium.Await.await;
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
                "await(() -> \"wrong\").until(firstElement(instanceOf(User.class)));",
                "await(() -> Set.of(new User(1))).until(lastElement(instanceOf(User.class)));",
                "await(() -> new ArrayDeque<>(List.of(new User(1)))).until(elementAt(0,instanceOf(User.class)));",
                "await(() -> List.of(new User(1))).until(firstElement(equalTo(123)));",
                "await(() -> List.of(new User(1))).until(singleElement(String::isBlank));",
                "await(() -> Optional.of(new User(1))).until(OptionalConditions.hasValue(String::isBlank));",
                "await(() -> Map.of(\"user\",new User(1))).until(MapConditions.valueFor(\"user\",String::isBlank));",
                "await(() -> Map.of(\"user\",new User(1))).until(MapConditions.singleEntry((Integer key,User user)->true));",
                "await(() -> Map.of(\"user\",new User(1))).until(MapConditions.singleEntry((String key,String user)->true));",
                "await(() -> Map.of(\"user\",new User(1))).until(MapConditions.singleEntry((String key,User user)->\"wrong boolean\"));",
                "await(() -> List.of(new User(1))).until(lastElement(matches(String::isEmpty)));",
                "await(() -> List.of(new User(1))).until(elementAt(0,equalTo(123)));",
                "String wrong = await(() -> List.of(new User(1))).until(firstElement(isNotNull));",
                "await(() -> Set.of(new User(1))).until(firstElement(isNotNull));",
                "await(() -> new ArrayDeque<>(List.of(new User(1)))).until(elementAt(0,isNotNull));",
                "await(() -> List.of(\"ready\")).until(lastElement(equalTo(new Object())));",
                "String wrong = await(() -> values).until(firstElement(instanceOf(User.class)));",
                "String wrong = await(() -> new User(1)).until(matches((User user)->true));",
                "await(() -> new User(1)).until(matches(io.github.gromoff97.awium.CheckedPredicate.not(String::isBlank)));",
                "await(() -> new User(1)).until(matches(io.github.gromoff97.awium.CheckedPredicate.not((User user)->\"wrong boolean\")));",
                "await(() -> Map.of(\"u\",new User(1))).until(MapConditions.singleEntry(io.github.gromoff97.awium.CheckedBiPredicate.not((Integer key,User user)->true)));",
                "await(() -> values).until(firstElement(instanceOf(User.class)),firstElement(instanceOf(Integer.class)));",
                "AwaitResult<List<Object>,String> wrong = await(() -> values).tryUntil(lastElement(instanceOf(User.class)));",
                "await(() -> values).until(firstElement(instanceOf(User.class)),lastElement(instanceOf(String.class)));",
                "await(() -> values).<Object>until(firstElement(instanceOf(User.class)),lastElement(instanceOf(String.class)));",
                "await(() -> values).tryUntil(elementAt(0,instanceOf(User.class)),firstElement(instanceOf(Integer.class)));")) {
            assertRejected("""
                    import java.util.*;
                    import io.github.gromoff97.awium.AwaitResult;
                    import io.github.gromoff97.awium.MapConditions;
                    import io.github.gromoff97.awium.OptionalConditions;
                    import static io.github.gromoff97.awium.Await.await;
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
            for (String condition : List.of("singleElement", "singleElement(User::active)", "singleElement(matches(User::active))", "singleElement(equalTo(new User(true)))")) {
                assertRejected("""
                        import java.util.*;
                        import static io.github.gromoff97.awium.Await.await;
                        import static io.github.gromoff97.awium.Conditions.*;
                        import static io.github.gromoff97.awium.CollectionConditions.*;
                        final class Contract {
                            record User(boolean active) {}
                            static final class Both extends AbstractMap<String,Integer> implements Iterable<User> {
                                public Set<Map.Entry<String,Integer>> entrySet() { return Set.of(Map.entry("key", 10)); }
                                public Iterator<User> iterator() { return List.of(new User(true)).iterator(); }
                            }
                            void check(java.util.concurrent.Callable<Both> source) { await(source::call).%s(%s); }
                        }
                        """.formatted(terminal, condition));
            }
        }
    }

    @Test
    void expectedIterablesDoNotWeakenElementKeyOrValueConstraints() throws IOException {
        for (String expression : List.of(
                "await(() -> \"ready\").until(in(wrong));",
                "await(() -> \"ready\").until(notIn(wrong));",
                "await(() -> List.of(\"ready\")).until(containsAll(wrong));",
                "await(() -> List.of(\"ready\")).until(containsExactly(wrong));",
                "await(() -> List.of(\"ready\")).until(containsOnly(wrong));",
                "await(() -> List.of(\"ready\")).until(startsWith(wrong));",
                "await(() -> Map.of(\"key\", \"ready\")).until(MapConditions.containsAllKeys(wrong));",
                "await(() -> Map.of(\"key\", \"ready\")).until(MapConditions.containsOnlyKeys(wrong));",
                "await(() -> Map.of(\"key\", \"ready\")).until(MapConditions.containsAllValues(wrong));")) {
            assertRejected("""
                    import java.util.*;
                    import io.github.gromoff97.awium.MapConditions;
                    import static io.github.gromoff97.awium.Await.await;
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
                "await(() -> users).until(singleElement(instanceOf(User.class)));",
                "await(() -> users).until(singleElement(User::active));",
                "await(() -> users).until(all(String::isBlank));",
                "await(() -> users).until(any(String::isBlank));",
                "String wrong = await(() -> users).until(singleElement(instanceOf(User.class)));",
                "await(() -> users).until(singleElement);",
                "await(() -> users).until(firstElement);",
                "await(() -> users).until(hasSize(1));",
                "await(() -> users).until(present);",
                "await(() -> users).until(valueFor(\"key\"));",
                "await(() -> users).until(singleElement(instanceOf(String.class)), singleElement(instanceOf(Integer.class)));",
                "await(() -> broad).until(all(User::active));")) {
            assertRejected("""
                    import java.util.*;
                    import static io.github.gromoff97.awium.Await.await;
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
        for (String expression : List.of("await(strings).until(equalTo(42));",
                "await(strings).until(matches(value -> \"wrong boolean\"));",
                "await(strings).until(matches(value -> null));",
                "await(() -> new String[]{\"ready\"}).until(in(java.util.Collections.singletonList(new Integer[]{1})));",
                "String wrong = await(objects).until(instanceOf(Integer.class));",
                "AwaitResult<Optional<Integer>, String> wrong = await(optional::call).tryUntil(present);",
                "await(characters).until(equalTo(42));",
                "await(characters).tryUntil(equalTo(42));")) {
            assertRejected("""
                    import java.util.concurrent.Callable;
                    import io.github.gromoff97.awium.*;
                    import java.util.Optional;
                    import static io.github.gromoff97.awium.Await.await;
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
                        import static io.github.gromoff97.awium.Await.await;
                        import static io.github.gromoff97.awium.Conditions.*;
                        final class Contract {
                            void check() {
                                await(() -> "ready").%s(%s);
                            }
                        }
                        """.formatted(terminal, condition));
            }
            for (String condition : List.of("nonBlank", "hasLength(1)", "contentEquals(\"ready\")")) {
                assertRejected("""
                        import static io.github.gromoff97.awium.Await.await;
                        import io.github.gromoff97.awium.StringConditions;
                        final class Contract {
                            void check() {
                                await(() -> 1).%s(StringConditions.%s);
                            }
                        }
                        """.formatted(terminal, condition));
            }
            for (String condition : List.of("between", "strictlyBetween")) {
                assertRejected("""
                        import static io.github.gromoff97.awium.Await.await;
                        import static io.github.gromoff97.awium.Conditions.*;
                        final class Contract {
                            record Limit(int number) {}
                            record Measurement(int number) implements Comparable<Limit> {
                                public int compareTo(Limit limit) { return Integer.compare(number, limit.number()); }
                            }
                            void check() {
                                await(() -> new Measurement(10)).%s(%s(new Limit(5), new Limit(15)));
                            }
                        }
                        """.formatted(terminal, condition));
            }
        }
    }

    @Test
    void collectionExpectationsRejectUnrelatedElementTypes() throws IOException {
        for (String condition : List.of("contains(42)",
                "containsAll(List.of(42))",
                "doesNotContain(42)",
                "doesNotContainAny(List.of(42))",
                "doesNotContainAll(List.of(42))",
                "containsAny(List.of(42))",
                "containsExactly(List.of(42))",
                "doesNotContainExactly(List.of(42))",
                "containsExactlyInAnyOrder(List.of(42))",
                "doesNotContainExactlyInAnyOrder(List.of(42))",
                "containsOnly(List.of(42))",
                "startsWith(List.of(42))",
                "endsWith(List.of(42))",
                "containsSequence(List.of(42))",
                "doesNotContainSequence(List.of(42))",
                "containsSubsequence(List.of(42))",
                "doesNotContainSubsequence(List.of(42))")) {
            for (String terminal : List.of("until", "tryUntil")) {
                String expression = "await(source::call)." + terminal + "(CollectionConditions." + condition + ");";
                assertRejected("""
                        import java.util.List;
                        import java.util.concurrent.Callable;
                        import io.github.gromoff97.awium.CollectionConditions;
                        import static io.github.gromoff97.awium.Await.await;
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
                import static io.github.gromoff97.awium.Await.await;
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
                    import static io.github.gromoff97.awium.Await.await;
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
    void ambiguousSourcesNeedALambdaOrAnExplicitSourceCategory() throws IOException {
        for (String expression : List.of("await(StringBuilder::new);", "await(java.util.Optional::empty);", "await(() -> null);")) {
            assertRejected("""
                    import static io.github.gromoff97.awium.Await.await;
                    final class Contract { void check() { %s } }
                    """.formatted(expression));
        }
    }

    @Test
    void plainCallableContainerSourcesRequireAdaptationForSelections() throws IOException {
        for (String expression : List.of("await(optional).until(present);", "await(collection).until(singleElement);", "await(map).until(singleEntry);")) {
            assertRejected("""
                    import java.util.*;
                    import java.util.concurrent.Callable;
                    import static io.github.gromoff97.awium.Await.await;
                    import static io.github.gromoff97.awium.OptionalConditions.present;
                    import static io.github.gromoff97.awium.CollectionConditions.singleElement;
                    import static io.github.gromoff97.awium.MapConditions.singleEntry;
                    final class Contract {
                        void check(Callable<Optional<String>> optional, Callable<List<String>> collection,
                                Callable<Map<String, String>> map) { %s }
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
                    import static io.github.gromoff97.awium.Await.await;
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
        for (String condition : List.of("present", "singleElement", "firstElement", "lastElement", "singleEntry")) {
            String source = condition.equals("present") ? "optional" : condition.equals("singleEntry") ? "map" : "collection";
            String factory = condition.equals("present") ? "await" : condition.equals("singleEntry") ? "await" : "await";
            String contract = """
                    import static io.github.gromoff97.awium.Await.await;
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
            assertRejected(contract.formatted("Integer", factory, source, condition + ""));
        }
    }

    @Test
    void collectionAndMapConditionsCannotBeMixed() throws IOException {
        assertRejected("""
                import static io.github.gromoff97.awium.Await.await;
                import io.github.gromoff97.awium.MapConditions;
                import java.util.List;
                final class Contract {
                    void check(java.util.concurrent.Callable<List<String>> source) {
                        await(source::call).until(MapConditions.nonEmpty);
                    }
                }
                """);
        assertRejected("""
                import static io.github.gromoff97.awium.Await.await;
                import io.github.gromoff97.awium.CollectionConditions;
                import java.util.Map;
                final class Contract {
                    void check(java.util.concurrent.Callable<Map<String, String>> source) {
                        await(source::call).until(CollectionConditions.nonEmpty);
                    }
                }
                """);
        assertRejected("""
                import static io.github.gromoff97.awium.Await.await;
                import io.github.gromoff97.awium.MapConditions;
                import java.util.List;
                final class Contract {
                    void check(java.util.concurrent.Callable<List<String>> source) {
                        await(source::call).until(MapConditions.singleEntry);
                    }
                }
                """);
        assertRejected("""
                import static io.github.gromoff97.awium.Await.await;
                import io.github.gromoff97.awium.CollectionConditions;
                import java.util.Map;
                final class Contract {
                    void check(java.util.concurrent.Callable<Map<String, String>> source) {
                        await(source::call).until(CollectionConditions.singleElement);
                    }
                }
                """);
    }

    @Test
    void collectionSelectionRejectsAnUnrelatedElementType() throws IOException {
        assertRejected("""
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.singleElement;
                import java.util.List;
                final class Contract {
                    void check() {
                        Integer wrong = await((java.util.concurrent.Callable<List<String>>)
                                () -> List.of("value")).until(singleElement);
                    }
                }
                """);
    }

    @Test
    void orderedSelectionRejectsUnrelatedResultsAndUnorderedSources() throws IOException {
        assertRejected("""
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.firstElement;
                import java.util.List;
                final class Contract {
                    void check(java.util.concurrent.Callable<List<String>> source) {
                        Integer wrong = await(source::call).until(firstElement);
                    }
                }
                """);
        assertRejected("""
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.firstElement;
                import java.util.HashSet;
                final class Contract {
                    void check(java.util.concurrent.Callable<HashSet<String>> source) {
                        await(source::call).until(firstElement);
                    }
                }
                """);
    }

    @Test
    void mapMembershipConditionsRejectIncompatibleKeysAndValues() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            for (String condition : List.of("containsKey(1)", "containsValue(\"wrong\")",
                    "containsAllKeys(List.of(1))", "containsAllValues(List.of(\"wrong\"))",
                    "containsOnlyKeys(List.of(1))",
                    "doesNotContainKey(1)", "doesNotContainValue(\"wrong\")",
                    "doesNotContainAnyKeys(List.of(1))", "doesNotContainAnyValues(List.of(\"wrong\"))",
                    "hasEntry(1, 1)", "hasEntry(\"x\", \"wrong\")",
                    "containsAllEntries(Map.of(1, 1))", "containsAllEntries(Map.of(\"x\", \"wrong\"))")) {
                assertRejected("""
                        import static io.github.gromoff97.awium.Await.await;
                        import static io.github.gromoff97.awium.MapConditions.*;
                        import java.util.Map;
                        import java.util.List;

                        final class Contract {
                            void check(java.util.concurrent.Callable<Map<String, Integer>> source) {
                                await(source::call).%s(%s);
                            }
                        }
                        """.formatted(terminal, condition));
            }
        }
    }

    @Test
    void lazyConditionsRejectAnIncompatibleObservedType() throws IOException {
        for (String terminal : List.of("until", "tryUntil")) {
            assertRejected("""
                    import static io.github.gromoff97.awium.Await.await;
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
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.CollectionConditions.*;
                import java.util.*;

                final class Contract {
                    void check(java.util.concurrent.Callable<Set<String>> source) {
                        await(source::call).until(containsExactly(List.of("a")));
                    }
                }
                """);
    }

    @Test
    void covariantEntryViewsKeepIndependentValueTypesAcrossStages() throws IOException {
        assertRejected("""
                import java.util.*;
                import io.github.gromoff97.awium.Await.MapViewSource;
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.MapConditions.singleEntry;
                final class Contract {
                    static <KEY, VALUE> void overwrite(List<Map.Entry<KEY, VALUE>> entries) {
                        entries.getFirst().setValue(entries.getLast().getValue());
                    }
                    void check(MapViewSource<Number, CharSequence,
                            Map<? extends Number, ? extends CharSequence>> source) {
                        overwrite(await(source).until(singleEntry, singleEntry));
                    }
                }
                """);
    }

    private void assertRejected(String source) throws IOException {
        CompilationSupport.assertRejected(temporaryDirectory, source);
    }

}
