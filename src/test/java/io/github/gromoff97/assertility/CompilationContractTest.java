package io.github.gromoff97.assertility;

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
                import static io.github.gromoff97.assertility.Assertility.await;
                import static io.github.gromoff97.assertility.AwaitConditions.*;
                import static io.github.gromoff97.assertility.Evaluation.satisfied;
                import static java.time.Duration.ofMillis;
                import io.github.gromoff97.assertility.*;
                import java.util.*;

                final class Contract {
                    static String object() { return "value"; }
                    static Optional<String> optional() { return Optional.of("value"); }
                    static Collection<String> collection() { return List.of("value"); }
                    static ArrayList<String> sequenced() {
                        return new ArrayList<>(List.of("value"));
                    }
                    static HashMap<String, Integer> map() {
                        return new HashMap<>(Map.of("value", 1));
                    }

                    void check(StructuralCondition structural,
                            ExplainedStructuralCondition explainedStructural) {
                        AwaitSources.Source<String> source = Contract::object;
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
                                .until(isNotNull.because("required"));
                        Integer selected = await(Contract::object).until(condition(
                                "length", (String value) -> satisfied(value.length())));
                        Integer selectedExplained = await(Contract::object).until(
                                condition("length",
                                        (String value) -> satisfied(value.length()))
                                        .because("needed"));
                        Void nil = await((AwaitSources.Source<String>) () -> null)
                                .until(isNull);
                        String presentValue = await(Contract::optional).until(present);
                        String explainedPresent = await(Contract::optional)
                                .until(present.because("required"));
                        Void absentValue = await(Contract::optional).until(absent);

                        Collection<String> collectionValue = await(Contract::collection)
                                .until(structural);
                        ArrayList<String> sequencedValue = await(Contract::sequenced)
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
                    import static io.github.gromoff97.assertility.Assertility.await;
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
                import static io.github.gromoff97.assertility.Assertility.await;
                final class Contract { void check() { await(() -> null); } }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.assertility.Assertility.await;
                final class Contract { void check() { await(null); } }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.assertility.Assertility.await;
                import io.github.gromoff97.assertility.AwaitSources;
                final class Contract {
                    void check(AwaitSources.Source<String> source) {
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
                    import static io.github.gromoff97.assertility.Assertility.await;
                    import io.github.gromoff97.assertility.AwaitSources;
                    import java.time.Duration;
                    final class Contract {
                        void check(AwaitSources.Source<String> source) {
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
                import static io.github.gromoff97.assertility.Assertility.await;
                import io.github.gromoff97.assertility.*;
                final class Contract {
                    void check(AwaitSources.Source<String> source, Present condition) {
                        await(source).until(condition);
                    }
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.assertility.Assertility.await;
                import io.github.gromoff97.assertility.*;
                final class Contract {
                    void check(AwaitSources.Source<String> source,
                            StructuralCondition condition) {
                        await(source).until(condition);
                    }
                }
                """));
    }

    @Test
    void allFluentInterfacesRejectExternalImplementations() throws IOException {
        for (String type : new String[] {
                "ObjectUntil<String>", "ObjectAwait<String>",
                "ObjectAwait.AfterEvery<String>", "ObjectAwait.AfterUpTo<String>",
                "OptionalUntil<String>", "OptionalAwait<String>",
                "OptionalAwait.AfterEvery<String>", "OptionalAwait.AfterUpTo<String>",
                "CollectionUntil<String, java.util.Collection<String>>",
                "CollectionAwait<String, java.util.Collection<String>>",
                "CollectionAwait.AfterEvery<String, java.util.Collection<String>>",
                "CollectionAwait.AfterUpTo<String, java.util.Collection<String>>",
                "SequencedCollectionUntil<String, java.util.SequencedCollection<String>>",
                "SequencedCollectionAwait<String, java.util.SequencedCollection<String>>",
                "SequencedCollectionAwait.AfterEvery<String, java.util.SequencedCollection<String>>",
                "SequencedCollectionAwait.AfterUpTo<String, java.util.SequencedCollection<String>>",
                "MapUntil<String, Integer, java.util.Map<String, Integer>>",
                "MapAwait<String, Integer, java.util.Map<String, Integer>>",
                "MapAwait.AfterEvery<String, Integer, java.util.Map<String, Integer>>",
                "MapAwait.AfterUpTo<String, Integer, java.util.Map<String, Integer>>"
        }) {
            assertFalse(compiles("""
                    import io.github.gromoff97.assertility.*;
                    final class Contract {
                        abstract class Broken implements %s {}
                    }
                    """.formatted(type)), type);
        }
    }

    @Test
    void disjointGrammarStagesCannotBeCastToRecoverMethods() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.assertility.Assertility.await;
                import io.github.gromoff97.assertility.*;
                final class Contract {
                    void check(AwaitSources.Source<String> source) {
                        ObjectAwait<String> initial = await(source);
                        ObjectAwait.AfterEvery<String> impossible =
                                (ObjectAwait.AfterEvery<String>) initial;
                    }
                }
                """));
    }

    @Test
    void assertionAdaptersMayBeDecoratedOnce() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.assertility.AwaitConditions.*;

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
                import static io.github.gromoff97.assertility.AwaitConditions.asserted;

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
                import io.github.gromoff97.assertility.Condition;
                import io.github.gromoff97.assertility.Evaluation;

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
                import io.github.gromoff97.assertility.Condition;
                import io.github.gromoff97.assertility.Evaluation;
                import io.github.gromoff97.assertility.ExplainedCondition;

                final class Contract extends Condition<Contract.Payment, Contract.Payment> {
                    record Payment(long id) {}

                    @Override
                    public Evaluation<Payment> evaluate(Payment actual) {
                        return Evaluation.satisfied(actual);
                    }

                    @Override
                    public ExplainedCondition<Payment, Payment> because(String value) {
                        return null;
                    }
                }
                """));
    }

    @Test
    void formattedBecauseCannotBeOverridden() throws IOException {
        assertFalse(compiles("""
                import io.github.gromoff97.assertility.Condition;
                import io.github.gromoff97.assertility.Evaluation;
                import io.github.gromoff97.assertility.ExplainedCondition;

                final class Contract extends Condition<Contract.Payment, Contract.Payment> {
                    record Payment(long id) {}

                    @Override
                    public Evaluation<Payment> evaluate(Payment actual) {
                        return Evaluation.satisfied(actual);
                    }

                    @Override
                    public ExplainedCondition<Payment, Payment> because(
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
