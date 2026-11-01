package io.github.gromoff97.awium.conditions.api

import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class CompilationContractTest {

    @TestFactory
    fun `nested quantifiers reject incompatible inputs and results`() = listOf(
            "await(() -> users).until(all(matches(String::isBlank)));",
            "await(() -> users).until(any(equalTo(123)));",
            "await(() -> users).until(none(OptionalConditions.hasValue(instanceOf(User.class))));",
            "await(() -> users).until(all(OptionalConditions.present));",
            "await(() -> map).until(MapConditions.allKeys(OptionalConditions.present));",
            "await(() -> map).until(MapConditions.allValues(singleElement));",
            "User wrong = await(() -> users).until(all(matches(User::active)));",
            "await(() -> map).until(MapConditions.allKeys(matches(User::active)));",
            "await(() -> map).until(MapConditions.anyKey(equalTo(123)));",
            "await(() -> map).until(MapConditions.noValue(matches(String::isBlank)));",
            "await(() -> map).until(MapConditions.allValues(equalTo(123)));",
            "await(() -> map).until(MapConditions.hasEntry(matches((Map.Entry<Integer,User> e)->true)));",
            """await(() -> map).until(MapConditions.hasEntry(matches((Map.Entry<String,String> e)->true)).because("reason"));""",
            "await(() -> map).until(MapConditions.hasEntry(matches((Map.Entry<String,Object> e)->e.setValue(new Object())!=null)));",
            """await(() -> map).until(MapConditions.hasEntry(equalTo(Map.entry(123,new User(true)))).because("reason"));""",
            """await(() -> map).until(MapConditions.hasEntry(equalTo(Map.entry("user",123))));""",
            """Map.Entry<String,User> wrong = await(() -> map).until(MapConditions.hasEntry("user",new User(true)));""",
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
            """await(() -> map).until(MapConditions.allEntries(equalTo(Map.entry("user",123))));""").map { body ->
        dynamicTest(body) { assertRejected("""
                import io.github.gromoff97.awium.conditions.map.MapConditions;
                import io.github.gromoff97.awium.conditions.optional.OptionalConditions;
                import static io.github.gromoff97.awium.conditions.common.CommonConditions.*;
                import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.*;
                final class Contract {
                    record User(boolean active) {}
                    void check(List<User> users, Map<String,User> map) { %s }
                }
                """.trimIndent().format(body)) }
    }

    @TestFactory
    fun `typed collection selections keep source and result constraints`() = listOf(
            """await(() -> "wrong").until(firstElement(instanceOf(User.class)));""",
            "await(() -> Set.of(new User(1))).until(lastElement(instanceOf(User.class)));",
            "await(() -> Set.of(new User(1))).until(elementAt(0,instanceOf(User.class)));",
            "await(() -> List.of(new User(1))).until(firstElement(equalTo(123)));",
            "await(() -> List.of(new User(1))).until(singleElement(String::isBlank));",
            "tryAwait(() -> List.of(new User(1))).until(singleElement(String::isBlank));",
            "tryAwait(() -> Optional.of(new User(1))).until(OptionalConditions.hasValue(String::isBlank));",
            """tryAwait(() -> Map.of("user",new User(1))).until(MapConditions.valueFor("user",String::isBlank));""",
            "await(() -> Optional.of(new User(1))).until(OptionalConditions.hasValue(String::isBlank));",
            """await(() -> Map.of("user",new User(1))).until(MapConditions.valueFor("user",String::isBlank));""",
            """await(() -> Map.of("user",new User(1))).until(MapConditions.singleEntry((Integer key,User user)->true));""",
            """await(() -> Map.of("user",new User(1))).until(MapConditions.singleEntry((String key,String user)->true));""",
            """await(() -> Map.of("user",new User(1))).until(MapConditions.singleEntry((String key,User user)->"wrong boolean"));""",
            "await(() -> List.of(new User(1))).until(lastElement(matches(String::isEmpty)));",
            "await(() -> List.of(new User(1))).until(elementAt(0,equalTo(123)));",
            "String wrong = await(() -> List.of(new User(1))).until(firstElement(isNotNull));",
            "await(() -> Set.of(new User(1))).until(firstElement(isNotNull));",
            "await(() -> Set.of(new User(1))).until(elementAt(0,isNotNull));",
            """await(() -> List.of("ready")).until(lastElement(equalTo(new Object())));""",
            "String wrong = await(() -> values).until(firstElement(instanceOf(User.class)));",
            "String wrong = await(() -> new User(1)).until(matches((User user)->true));",
            "await(() -> new User(1)).until(matches(io.github.gromoff97.awium.function.checked.CheckedPredicate.not(String::isBlank)));",
            """await(() -> new User(1)).until(matches(io.github.gromoff97.awium.function.checked.CheckedPredicate.not((User user)->"wrong boolean")));""",
            """await(() -> Map.of("u",new User(1))).until(MapConditions.singleEntry(io.github.gromoff97.awium.function.checked.CheckedBiPredicate.not((Integer key,User user)->true)));""",
            "AwaitResult<List<Object>, String> wrong = tryAwait(() -> values).until(lastElement(instanceOf(User.class)));",
            "AwaitResult<List<Object>, List<Integer>> wrong = tryAwait(() -> values).until(captured(elementAt(0,instanceOf(User.class)),firstElement(instanceOf(Integer.class))));").map { body ->
        dynamicTest(body) { assertRejected("""
                import io.github.gromoff97.awium.result.AwaitResult;
                import io.github.gromoff97.awium.conditions.map.MapConditions;
                import io.github.gromoff97.awium.conditions.optional.OptionalConditions;
                import static io.github.gromoff97.awium.conditions.common.CommonConditions.*;
                import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.*;
                final class Contract {
                    record User(int id) {}
                    void check(List<Object> values) { %s }
                }
                """.trimIndent().format(body)) }
    }

    @TestFactory
    fun `a map implementing Iterable still rejects element selectors`() = listOf("await", "tryAwait").flatMap { factory ->
        listOf("singleElement", "singleElement(User::active)", "singleElement(matches(User::active))", "singleElement(equalTo(new User(true)))").map { condition ->
            dynamicTest("$factory / $condition") { assertRejected("""
                    import static io.github.gromoff97.awium.conditions.common.CommonConditions.*;
                    import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.*;
                    final class Contract {
                        record User(boolean active) {}
                        static final class Both extends AbstractMap<String,Integer> implements Iterable<User> {
                            public Set<Map.Entry<String,Integer>> entrySet() { return Set.of(Map.entry("key", 10)); }
                            public Iterator<User> iterator() { return List.of(new User(true)).iterator(); }
                        }
                        void check(Callable<Both> source) { %s(source::call).until(%s); }
                    }
                    """.trimIndent().format(factory, condition)) }
        }
    }

    @TestFactory
    fun `expected iterables do not weaken element key or value constraints`() = listOf(
            """await(() -> "ready").until(in(wrong));""",
            """await(() -> "ready").until(notIn(wrong));""",
            """await(() -> List.of("ready")).until(containsAll(wrong));""",
            """await(() -> List.of("ready")).until(containsExactly(wrong));""",
            """await(() -> List.of("ready")).until(containsOnly(wrong));""",
            """await(() -> List.of("ready")).until(startsWith(wrong));""",
            """await(() -> Map.of("key", "ready")).until(MapConditions.containsAllKeys(wrong));""",
            """await(() -> Map.of("key", "ready")).until(MapConditions.containsOnlyKeys(wrong));""",
            """await(() -> Map.of("key", "ready")).until(MapConditions.containsAllValues(wrong));""").map { expression ->
        dynamicTest(expression) { assertRejected("""
                import io.github.gromoff97.awium.conditions.map.MapConditions;
                import static io.github.gromoff97.awium.conditions.common.CommonConditions.*;
                import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.*;
                final class Contract {
                    void check(Iterable<Integer> wrong) { %s }
                }
                """.trimIndent().format(expression)) }
    }

    @TestFactory
    fun `iterables reject container conditions`() = listOf(
            "await(() -> users).until(all(User::active));",
            "await(() -> users).until(any(matches(User::active)));",
            "await(() -> users).until(none(equalTo(new User(true))));",
            "await(() -> users).until(singleElement(instanceOf(User.class)));",
            "await(() -> users).until(singleElement(User::active));",
            "await(() -> users).until(singleElement);",
            "await(() -> users).until(firstElement);",
            "await(() -> users).until(hasSize(1));",
            "await(() -> users).until(present);",
            """await(() -> users).until(valueFor("key"));""",
            "await(() -> broad).until(all(User::active));").map { expression ->
        dynamicTest(expression) { assertRejected("""
                import static io.github.gromoff97.awium.conditions.common.CommonConditions.*;
                import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.*;
                import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.present;
                import static io.github.gromoff97.awium.conditions.map.MapConditions.valueFor;
                final class Contract {
                    record User(boolean active) {}
                    void check(Iterable<User> users, Iterable<? super User> broad) { %s }
                }
                """.trimIndent().format(expression)) }
    }

    @TestFactory
    fun `text expectations reject numeric values`() = listOf("contains", "doesNotContain", "containsIgnoringCase",
            "startsWith", "doesNotStartWith", "endsWith", "doesNotEndWith",
            "matchesRegex", "doesNotMatchRegex", "contentEquals", "equalToIgnoringCase", "notEqualToIgnoringCase").map { method ->
        dynamicTest(method) { assertRejected("""
                import io.github.gromoff97.awium.conditions.string.StringConditions;
                final class Contract {
                    void check() { StringConditions.%s(42); }
                }
                """.trimIndent().format(method)) }
    }

    @TestFactory
    fun `conditions and results reject unrelated types`() = listOf("await(strings).until(equalTo(42));",
            """await(strings).until(matches(value -> "wrong boolean"));""",
            "await(strings).until(matches(value -> null));",
            """await(() -> new String[]{"ready"}).until(in(Collections.singletonList(new Integer[]{1})));""",
            "String wrong = await(objects).until(instanceOf(Integer.class));",
            "AwaitResult<Optional<Integer>, String> wrong = tryAwait(optional::call).until(present);",
            "await(characters).until(equalTo(42));",
            "tryAwait(characters).until(equalTo(42));").map { expression ->
        dynamicTest(expression) { assertRejected("""
                import io.github.gromoff97.awium.*;
                import io.github.gromoff97.awium.conditions.common.*;
                import io.github.gromoff97.awium.result.*;
                import io.github.gromoff97.awium.function.*;
                import static io.github.gromoff97.awium.conditions.common.CommonConditions.*;
                import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.present;
                final class Contract {
                    void check(Callable<String> strings, Callable<Object> objects,
                            Callable<CharSequence> characters, Callable<Optional<Integer>> optional) {
                        %s
                    }
                }
                """.trimIndent().format(expression)) }
    }

    @TestFactory
    fun `comparable and text checks enforce their input contracts`() = buildList {
        for (factory in listOf("await", "tryAwait")) {
            for (condition in listOf("greaterThan(1)", "atLeast(1)", "lessThan(1)",
                    "atMost(1)", "between(1, 2)", "strictlyBetween(1, 2)")) {
                add(dynamicTest("$factory / $condition") { assertRejected("""
                        import static io.github.gromoff97.awium.conditions.common.CommonConditions.*;
                        final class Contract {
                            void check() {
                                %s(() -> "ready").until(%s);
                            }
                        }
                        """.trimIndent().format(factory, condition)) })
            }
            for (condition in listOf("nonBlank", "hasLength(1)", """contentEquals("ready")""")) {
                add(dynamicTest("$factory / $condition") { assertRejected("""
                        import io.github.gromoff97.awium.conditions.string.StringConditions;
                        final class Contract {
                            void check() {
                                %s(() -> 1).until(StringConditions.%s);
                            }
                        }
                        """.trimIndent().format(factory, condition)) })
            }
            for (condition in listOf("between", "strictlyBetween")) {
                add(dynamicTest("$factory / $condition") { assertRejected("""
                        import static io.github.gromoff97.awium.conditions.common.CommonConditions.*;
                        final class Contract {
                            record Limit(int number) {}
                            record Measurement(int number) implements Comparable<Limit> {
                                public int compareTo(Limit limit) { return Integer.compare(number, limit.number()); }
                            }
                            void check() {
                                %s(() -> new Measurement(10)).until(%s(new Limit(5), new Limit(15)));
                            }
                        }
                        """.trimIndent().format(factory, condition)) })
            }
        }
    }

    @TestFactory
    fun `collection expectations reject unrelated element types`() = listOf("contains(42)",
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
            "doesNotContainSubsequence(List.of(42))").flatMap { condition ->
        listOf("await", "tryAwait").map { factory ->
            val expression = factory + "(source::call).until(CollectionConditions." + condition + ");"
            dynamicTest(expression) { assertRejected("""
                    import io.github.gromoff97.awium.conditions.collection.CollectionConditions;
                    final class Contract {
                        void check(Callable<List<String>> source) {
                            %s
                        }
                    }
                    """.trimIndent().format(expression)) }
        }
    }

    @TestFactory
    fun `plain callable container sources require adaptation for selections`() = listOf(
            "await(optional).until(present);", "await(collection).until(singleElement);", "await(map).until(singleEntry);").map { expression ->
        dynamicTest(expression) { assertRejected("""
                import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.present;
                import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement;
                import static io.github.gromoff97.awium.conditions.map.MapConditions.singleEntry;
                final class Contract {
                    void check(Callable<Optional<String>> optional, Callable<List<String>> collection,
                            Callable<Map<String, String>> map) { %s }
                }
                """.trimIndent().format(expression)) }
    }

    @TestFactory
    fun `adapted callable selections reject incompatible result types`() = listOf(
            "present", "singleElement", "firstElement", "lastElement", "singleEntry").map { condition ->
        val source = if (condition == "present") "optional" else if (condition == "singleEntry") "map" else "collection"
        val contract = """
                import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.*;
                import static io.github.gromoff97.awium.conditions.map.MapConditions.singleEntry;
                import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.present;
                final class Contract {
                    void check(Callable<Optional<String>> optional,
                            Callable<List<String>> collection,
                            Callable<Map<String, String>> map) {
                        Integer result = await(%s::call).until(%s);
                    }
                }
                """.trimIndent()
        dynamicTest(condition) { assertRejected(contract.format(source, condition)) }
    }

    @TestFactory
    fun `collection and map conditions cannot be mixed`() = buildList {
        add(dynamicTest("map size on a collection") { assertRejected("""
                import io.github.gromoff97.awium.conditions.map.MapConditions;
                final class Contract {
                    void check(Callable<List<String>> source) {
                        await(source::call).until(MapConditions.nonEmpty);
                    }
                }
                """.trimIndent()) })
        add(dynamicTest("collection size on a map") { assertRejected("""
                import io.github.gromoff97.awium.conditions.collection.CollectionConditions;
                final class Contract {
                    void check(Callable<Map<String, String>> source) {
                        await(source::call).until(CollectionConditions.nonEmpty);
                    }
                }
                """.trimIndent()) })
        add(dynamicTest("entry selection on a collection") { assertRejected("""
                import io.github.gromoff97.awium.conditions.map.MapConditions;
                final class Contract {
                    void check(Callable<List<String>> source) {
                        await(source::call).until(MapConditions.singleEntry);
                    }
                }
                """.trimIndent()) })
        add(dynamicTest("element selection on a map") { assertRejected("""
                import io.github.gromoff97.awium.conditions.collection.CollectionConditions;
                final class Contract {
                    void check(Callable<Map<String, String>> source) {
                        await(source::call).until(CollectionConditions.singleElement);
                    }
                }
                """.trimIndent()) })
    }

    @Test
    fun `collection selection rejects an unrelated element type`() {
        assertRejected("""
                import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement;
                final class Contract {
                    void check() {
                        Integer wrong = await(() -> List.of("value")).until(singleElement);
                    }
                }
                """.trimIndent())
    }

    @Test
    fun `ordered selection rejects unordered sources`() {
        assertRejected("""
                import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.firstElement;
                final class Contract {
                    void check(Callable<HashSet<String>> source) {
                        await(source::call).until(firstElement);
                    }
                }
                """.trimIndent())
    }

    @TestFactory
    fun `map membership conditions reject incompatible keys and values`() = listOf("await", "tryAwait").flatMap { factory ->
        listOf("containsKey(1)", """containsValue("wrong")""",
                "containsAllKeys(List.of(1))", """containsAllValues(List.of("wrong"))""",
                "containsOnlyKeys(List.of(1))",
                "doesNotContainKey(1)", """doesNotContainValue("wrong")""",
                "doesNotContainAnyKeys(List.of(1))", """doesNotContainAnyValues(List.of("wrong"))""",
                "hasEntry(1, 1)", """hasEntry("x", "wrong")""",
                "containsAllEntries(Map.of(1, 1))", """containsAllEntries(Map.of("x", "wrong"))""").map { condition ->
            dynamicTest("$factory / $condition") { assertRejected("""
                    import static io.github.gromoff97.awium.conditions.map.MapConditions.*;

                    final class Contract {
                        void check(Callable<Map<String, Integer>> source) {
                            %s(source::call).until(%s);
                        }
                    }
                    """.trimIndent().format(factory, condition)) }
        }
    }

    @TestFactory
    fun `custom conditions reject an incompatible observed type`() = listOf("await", "tryAwait").flatMap { factory ->
        listOf("""condition("typed", Contract::status)""",
                """conditionPerWait("typed", Contract::evaluator)""").map { condition ->
            dynamicTest("$factory / $condition") { assertRejected("""
                    import static io.github.gromoff97.awium.conditions.common.CommonConditions.*;
                    import io.github.gromoff97.awium.function.AwaitAttempt;
                    import io.github.gromoff97.awium.result.ConditionResult;

                    final class Contract {
                        static ConditionResult<String> status(String value) {
                            return ConditionResult.satisfied(value);
                        }
                        static AwaitAttempt<String, String> evaluator() {
                            return Contract::status;
                        }
                        void check(Callable<Integer> source) {
                            %s(source).until(%s.because("reason"));
                        }
                    }
                    """.trimIndent().format(factory, condition)) }
        }
    }

    @Test
    fun `ordered equality rejects unordered collections`() {
        assertRejected("""
                import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.*;

                final class Contract {
                    void check(Callable<Set<String>> source) {
                        await(source::call).until(containsExactly(List.of("a")));
                    }
                }
                """.trimIndent())
    }

    @Test
    fun `covariant map views keep independent value types across stages`() {
        assertRejected("""
                import io.github.gromoff97.awium.Await.MapViewSource;
                import static io.github.gromoff97.awium.conditions.common.CommonConditions.captured;
                import static io.github.gromoff97.awium.conditions.map.MapConditions.singleEntry;
                final class Contract {
                    static <KEY, VALUE> void overwrite(List<Map<KEY, VALUE>> maps) {
                        KEY key = maps.getFirst().keySet().iterator().next();
                        VALUE value = maps.getLast().values().iterator().next();
                        maps.getFirst().put(key, value);
                    }
                    void check(MapViewSource<Number, CharSequence,
                            Map<? extends Number, ? extends CharSequence>> source) {
                        overwrite(await(source).until(captured(singleEntry, singleEntry)));
                    }
                }
                """.trimIndent())
    }

    private fun assertRejected(source: String) {
        CompilationSupport.assertRejected("""
                import java.util.*;
                import java.util.concurrent.Callable;
                import static io.github.gromoff97.awium.Await.await;
                import static io.github.gromoff97.awium.Await.tryAwait;
                """.trimIndent() + source)
    }
}
