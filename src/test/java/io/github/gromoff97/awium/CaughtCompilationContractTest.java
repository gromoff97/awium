package io.github.gromoff97.awium;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

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
