package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.conditioning.providers.ConditionProvider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompilationContractTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void allFacadesTimingSubsetsAndTerminalResultsCompile() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.Awium.await;
                import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;
                import static java.time.Duration.ofMillis;
                import io.github.gromoff97.awium.await.Await;
                import io.github.gromoff97.awium.sources.Source;
                import io.github.gromoff97.awium.conditioning.Evaluation;
                import io.github.gromoff97.awium.conditioning.conditions.Condition;
                import io.github.gromoff97.awium.conditioning.conditions.StructuralCondition;
                import java.util.*;

                final class Contract {
                    static String object() { return "value"; }
                    static Optional<String> optional() { return Optional.of("value"); }
                    static Collection<String> collection() { return List.of("value"); }
                    static List<String> list() {
                        return new ArrayList<>(List.of("value"));
                    }
                    static HashMap<String, Integer> map() {
                        return new HashMap<>(Map.of("value", 1));
                    }

                    void check(StructuralCondition structural,
                            StructuralCondition.ExplainedCondition explainedStructural) {
                        Source<String> source = Contract::object;
                        String immediate = await(source).until(isNotNull);
                        String every = await(source).every(ofMillis(1)).until(isNotNull);
                        String upTo = await(source).upTo(ofMillis(2)).until(isNotNull);
                        String stable = await(source).stableFor(ofMillis(1)).until(isNotNull);
                        String everyUpTo = await(source).every(ofMillis(1))
                                .upTo(ofMillis(2)).until(isNotNull);
                        String everyStable = await(source).every(ofMillis(1))
                                .stableFor(ofMillis(1)).until(isNotNull);
                        String upToStable = await(source).upTo(ofMillis(2))
                                .stableFor(ofMillis(1)).until(isNotNull);
                        String all = await(source).every(ofMillis(1)).upTo(ofMillis(2))
                                .stableFor(ofMillis(1)).until(isNotNull);

                        String explained = await(Contract::object)
                                .until(isNotNull.because("value is required"));
                        Integer selected = await(Contract::object).until(condition(
                                "length", (String value) -> Evaluation.satisfied(value.length())));
                        Integer selectedExplained = await(Contract::object).until(
                                condition("length",
                                        (String value) -> Evaluation.satisfied(value.length()))
                                        .because("needed"));
                        Void nil = await((Source<String>) () -> null)
                                .until(isNull);
                        String presentValue = await(Contract::optional).until(present);
                        String explainedPresent = await(Contract::optional)
                                .until(present.because("required"));
                        Void absentValue = await(Contract::optional).until(absent);

                        Collection<String> collectionValue = await(Contract::collection)
                                .until(structural);
                        List<String> ordered = await(Contract::list)
                                .until(explainedStructural);
                        HashMap<String, Integer> mapValue = await(Contract::map)
                                .until(structural);
                    }
                }
                """));
    }

    @Test
    void excludedDirectAndJdkSourceTypesDoNotCompile() throws IOException {
        for (String declaration : new String[] {
                "String source = \"value\";",
                "java.util.function.Supplier<String> source = () -> \"value\";",
                "java.util.concurrent.Callable<String> source = () -> \"value\";",
                "java.util.concurrent.Future<String> source = null;",
                "java.lang.Iterable<String> source = java.util.List.of();"
        }) {
            assertFalse(compiles("""
                    import static io.github.gromoff97.awium.Awium.await;
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
                import static io.github.gromoff97.awium.Awium.await;
                final class Contract { void check() { await(() -> null); } }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Awium.await;
                final class Contract { void check() { await(null); } }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Awium.await;
                import io.github.gromoff97.awium.sources.Source;
                final class Contract {
                    void check(Source<String> source) {
                        await(source).until(null);
                    }
                }
                """));
    }

    @Test
    void duplicateAndBackwardConfigurationDoNotCompile() throws IOException {
        for (String chain : new String[] {
                "every(d).every(d)",
                "upTo(d).every(d)",
                "upTo(d).upTo(d)",
                "stableFor(d).upTo(d)",
                "stableFor(d).stableFor(d)"
        }) {
            assertFalse(compiles("""
                    import static io.github.gromoff97.awium.Awium.await;
                    import io.github.gromoff97.awium.sources.Source;
                    import java.time.Duration;
                    final class Contract {
                        void check(Source<String> source) {
                            Duration d = Duration.ofSeconds(1);
                            await(source).%s;
                        }
                    }
                    """.formatted(chain)), chain);
        }
    }

    @Test
    void categorySpecificTerminalsRejectWrongConditions() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Awium.await;
                import io.github.gromoff97.awium.sources.Source;
                import io.github.gromoff97.awium.conditioning.conditions.*;
                final class Contract {
                    void check(Source<String> source, PresentCondition condition) {
                        await(source).until(condition);
                    }
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Awium.await;
                import io.github.gromoff97.awium.sources.Source;
                import io.github.gromoff97.awium.conditioning.conditions.*;
                final class Contract {
                    void check(Source<String> source,
                            StructuralCondition condition) {
                        await(source).until(condition);
                    }
                }
                """));
    }

    @Test
    void collectionExactFactoriesRespectOrderedSourceTyping()
            throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.Awium.await;
                import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;
                import io.github.gromoff97.awium.sources.*;
                import io.github.gromoff97.awium.conditioning.conditions.*;
                import java.util.*;

                final class Contract {
                    static List<String> list() { return List.of("a", "b"); }

                    void check(CollectionSource<Collection<String>> collection,
                            Collection<String> expected) {
                        List<String> ordered = await(Contract::list)
                                .until(containsExactly("a", "b"));
                        await(Contract::list).until(doesNotContainExactly("b", "a")
                                .because("ordered"));
                        await(Contract::list).until(containsExactlyElementsOf(expected));
                        await(Contract::list).until(doesNotContainExactlyElementsOf(
                                expected).because("ordered"));

                        Collection<String> anyOrder = await(collection)
                                .until(containsExactlyInAnyOrder("a", "b"));
                        await(collection).until(doesNotContainExactlyInAnyOrder(
                                "a", "b").because("any order"));
                        await(collection).until(
                                containsExactlyInAnyOrderElementsOf(expected));
                        await(collection).until(
                                doesNotContainExactlyInAnyOrderElementsOf(expected)
                                        .because("any order"));

                        await(Contract::list).until(containsExactlyInAnyOrder("a"));
                        await(Contract::list).until(
                                containsExactlyInAnyOrderElementsOf(expected));
                    }
                }
                """));
    }

    @Test
    void orderedExactFactoriesRejectCollectionOnlySources() throws IOException {
        for (String condition : new String[] {
                "containsExactly(\"a\")",
                "doesNotContainExactly(\"a\")",
                "containsExactlyElementsOf(expected)",
                "doesNotContainExactlyElementsOf(expected)"
        }) {
            assertFalse(compiles("""
                    import static io.github.gromoff97.awium.Awium.await;
                    import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;
                    import io.github.gromoff97.awium.sources.CollectionSource;
                    import io.github.gromoff97.awium.conditioning.conditions.*;
                    import java.util.*;

                    final class Contract {
                        void check(CollectionSource<Set<String>> source,
                                Collection<String> expected) {
                            await(source).until(%s);
                        }
                    }
                    """.formatted(condition)), condition);
        }
    }

    @Test
    void allFluentInterfacesRejectExternalImplementations() throws IOException {
        for (String type : new String[] {
                "Await.Until<String>", "Await<String>",
                "Await.AfterEvery<String>", "Await.AfterUpTo<String>",
                "OptionalAwait.Until<String>", "OptionalAwait<String>",
                "OptionalAwait.AfterEvery<String>", "OptionalAwait.AfterUpTo<String>",
                "StructuralAwait.Until<java.util.Collection<String>>",
                "StructuralAwait<java.util.Collection<String>>",
                "StructuralAwait.AfterEvery<java.util.Collection<String>>",
                "StructuralAwait.AfterUpTo<java.util.Collection<String>>"
        }) {
            assertFalse(compiles("""
                    import io.github.gromoff97.awium.await.*;
                    import io.github.gromoff97.awium.conditioning.conditions.*;
                    final class Contract {
                        abstract class Broken implements %s {}
                    }
                    """.formatted(type)), type);
        }
    }

    @Test
    void disjointGrammarStagesCannotBeCastToRecoverMethods() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.Awium.await;
                import io.github.gromoff97.awium.await.Await;
                import io.github.gromoff97.awium.sources.Source;
                import io.github.gromoff97.awium.conditioning.conditions.*;
                final class Contract {
                    void check(Source<String> source) {
                        Await<String> initial = await(source);
                        Await.AfterEvery<String> impossible =
                                (Await.AfterEvery<String>) initial;
                    }
                }
                """));
    }

    @Test
    void assertionAdaptersMayBeDecoratedOnce() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;

                final class Contract {
                    record Payment(long id) {}

                    void check() {
                        asserted((Payment value) -> {}).because("first");
                        passed((Payment value) -> value).because("first");
                    }
                }
                """));
    }

    @Test
    void explainedAssertionAdapterCannotBeDecoratedAgain() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.asserted;

                final class Contract {
                    record Payment(long id) {}

                    void check() {
                        asserted((Payment value) -> {}).because("first")
                                .because("second");
                    }
                }
                """));
    }

    @Test
    void conditionIsNotDirectlyLambdaAssignable() throws IOException {
        assertFalse(compiles("""
                import io.github.gromoff97.awium.conditioning.conditions.Condition;
                import io.github.gromoff97.awium.conditioning.Evaluation;

                final class Contract {
                    record Payment(long id) {}

                    Condition<Payment, Payment> condition =
                            value -> Evaluation.satisfied(value);
                }
                """));
    }

    @Test
    void literalBecauseCannotBeOverridden() throws IOException {
        assertFalse(compiles("""
                import io.github.gromoff97.awium.conditioning.conditions.Condition;
                import io.github.gromoff97.awium.conditioning.Evaluation;
                import io.github.gromoff97.awium.conditioning.conditions.Condition.ExplainedCondition;

                final class Contract extends Condition<Contract.Payment, Contract.Payment> {
                    record Payment(long id) {}

                    @Override
                    public Evaluation<Payment> evaluate(Payment actual) {
                        return Evaluation.satisfied(actual);
                    }

                    @Override
                    public Condition.ExplainedCondition<Payment, Payment> because(String value) {
                        return null;
                    }
                }
                """));
    }

    @Test
    void formattedBecauseCannotBeOverridden() throws IOException {
        assertFalse(compiles("""
                import io.github.gromoff97.awium.conditioning.conditions.Condition;
                import io.github.gromoff97.awium.conditioning.Evaluation;
                import io.github.gromoff97.awium.conditioning.conditions.Condition.ExplainedCondition;

                final class Contract extends Condition<Contract.Payment, Contract.Payment> {
                    record Payment(long id) {}

                    @Override
                    public Evaluation<Payment> evaluate(Payment actual) {
                        return Evaluation.satisfied(actual);
                    }

                    @Override
                    public Condition.ExplainedCondition<Payment, Payment> because(
                            String format, Object... arguments) {
                        return null;
                    }
                }
                """));
    }

    private boolean compiles(String source) throws IOException {
        return CompilationSupport.compiles(temporaryDirectory, source);
    }
}
