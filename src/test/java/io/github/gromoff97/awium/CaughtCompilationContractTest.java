package io.github.gromoff97.awium;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaughtCompilationContractTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void predicateAndConditionSequencesInferTypedLists() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditioning.Evaluation.*;
                import static io.github.gromoff97.awium.conditioning.conditions.Condition.*;
                import static io.github.gromoff97.awium.conditioning.conditions.ObjectCondition.matches;
                import io.github.gromoff97.awium.sources.Source;
                import java.util.List;

                final class Contract {
                    void check(Source<String> source) {
                        List<String> predicates = await(source).until(caught(
                                value -> value.startsWith("a"),
                                value -> value.endsWith("z")));
                        List<String> preserving = await(source).until(caught(
                                matches(value -> value.startsWith("a")),
                                matches((String value) -> value.endsWith("z")).because("final state")));
                        var ready = matches((String value) -> value.startsWith("a"));
                        List<String> readyMade = await(source).until(caught(
                                ready, ready.because("typed final state")));
                        List<Integer> transformed = await(source).until(caught(
                                condition("length 1", value -> value.length() == 1
                                        ? satisfied(value.length()) : unsatisfied("length was not 1")),
                                condition("length 2", value -> value.length() == 2
                                        ? satisfied(value.length()) : unsatisfied("length was not 2"))));
                    }
                }
                """));
    }

    @Test
    void selectedSequencesInferFacadeElementLists() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.await.TryAwait.tryAwait;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.*;
                import static io.github.gromoff97.awium.conditioning.conditions.Condition.caught;
                import static io.github.gromoff97.awium.conditioning.conditions.MapCondition.singleEntry;
                import static io.github.gromoff97.awium.conditioning.conditions.OptionalCondition.present;
                import io.github.gromoff97.awium.await.AwaitResult;
                import io.github.gromoff97.awium.sources.Source.*;
                import java.util.*;

                final class Contract {
                    void check(OptionalSource<String> optionalSource,
                            CollectionSource<List<String>> collectionSource,
                            MapSource<Map<String, Integer>> mapSource) {
                        List<String> optional = await(optionalSource).until(caught(present, present));
                        List<String> collection = await(collectionSource).until(caught(first, last));
                        List<Map.Entry<String, Integer>> map =
                                await(mapSource).until(caught(singleEntry, singleEntry));
                        AwaitResult<Optional<String>, List<String>> attempted =
                                tryAwait(optionalSource).until(caught(present, present));
                    }
                }
                """));
    }

    @Test
    void selectedSequencesCannotCrossSourceKinds() throws IOException {
        for (String invocation : List.of(
                "await(optional).until(caught(first, last))",
                "await(collection).until(caught(present, present))",
                "await(map).until(caught(first, last))",
                "await(collection).until(caught(singleEntry, singleEntry))")) {
            assertFalse(compiles("""
                    import static io.github.gromoff97.awium.await.Await.await;
                    import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.*;
                    import static io.github.gromoff97.awium.conditioning.conditions.Condition.caught;
                    import static io.github.gromoff97.awium.conditioning.conditions.MapCondition.singleEntry;
                    import static io.github.gromoff97.awium.conditioning.conditions.OptionalCondition.present;
                    import io.github.gromoff97.awium.sources.Source.*;
                    import java.util.*;
                    final class Contract {
                        void check(OptionalSource<String> optional,
                                CollectionSource<List<String>> collection,
                                MapSource<Map<String, Integer>> map) {
                            %s;
                        }
                    }
                    """.formatted(invocation)), invocation);
        }
    }

    @Test
    void plainSourcesRejectSelectedSequences() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.await.Await.await;
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.*;
                import static io.github.gromoff97.awium.conditioning.conditions.Condition.caught;
                import io.github.gromoff97.awium.sources.Source;
                import java.util.List;
                final class Contract {
                    void check(Source<List<String>> source) {
                        await(source).until(caught(first, last));
                    }
                }
                """));
    }

    @Test
    void selectedStagesCannotMixWithOtherFamilies() throws IOException {
        for (String stage : List.of(
                "matches((Optional<String> value) -> true)",
                "condition(\"text\", (Optional<String> value) -> satisfied(value.toString()))")) {
            assertFalse(compiles("""
                    import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
                    import static io.github.gromoff97.awium.conditioning.conditions.Condition.*;
                    import static io.github.gromoff97.awium.conditioning.conditions.ObjectCondition.matches;
                    import static io.github.gromoff97.awium.conditioning.conditions.OptionalCondition.present;
                    import java.util.Optional;
                    final class Contract {
                        void check() {
                            caught(present, %s);
                        }
                    }
                    """.formatted(stage)), stage);
        }
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.first;
                import static io.github.gromoff97.awium.conditioning.conditions.Condition.caught;
                import static io.github.gromoff97.awium.conditioning.conditions.MapCondition.singleEntry;
                final class Contract {
                    void check() {
                        caught(first, singleEntry);
                    }
                }
                """));
    }

    @Test
    void explainedSelectedSequenceCannotBeExplainedAgain() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.*;
                import static io.github.gromoff97.awium.conditioning.conditions.Condition.caught;
                final class Contract {
                    void check() {
                        caught(first, last).because("first").because("second");
                    }
                }
                """));
    }

    @Test
    void sequenceRequiresAtLeastTwoStages() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.conditioning.conditions.Condition.caught;
                final class Contract {
                    void check() {
                        caught();
                    }
                }
                """));
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.conditioning.conditions.Condition.caught;
                final class Contract {
                    void check() {
                        caught((String value) -> true);
                    }
                }
                """));
    }

    @Test
    void predicateAndConditionStagesCannotMix() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.conditioning.conditions.Condition.caught;
                import static io.github.gromoff97.awium.conditioning.conditions.ObjectCondition.matches;
                final class Contract {
                    void check() {
                        caught((String value) -> true, matches((String value) -> true));
                    }
                }
                """));
    }

    @Test
    void preservingAndTransformingStagesCannotMix() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
                import static io.github.gromoff97.awium.conditioning.conditions.Condition.*;
                import static io.github.gromoff97.awium.conditioning.conditions.ObjectCondition.matches;
                import io.github.gromoff97.awium.conditioning.conditions.Condition;
                import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingStage;
                final class Contract {
                    void check() {
                        PreservingStage<String> preserving = matches(value -> true);
                        Condition<String, Integer> transforming = condition("length",
                                value -> satisfied(value.length()));
                        caught(preserving, transforming);
                    }
                }
                """));
    }

    @Test
    void transformedStagesRequireOneInvariantResultType() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
                import static io.github.gromoff97.awium.conditioning.conditions.Condition.*;
                import io.github.gromoff97.awium.conditioning.conditions.Condition;
                final class Contract {
                    void check() {
                        Condition<String, Integer> integer = condition("integer",
                                value -> satisfied(value.length()));
                        Condition<String, Long> longer = condition("long",
                                value -> satisfied((long) value.length()));
                        caught(integer, longer);
                    }
                }
                """));
    }

    private boolean compiles(String source) throws IOException {
        return CompilationSupport.compiles(temporaryDirectory, source);
    }
}
