package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.conditions.ComparableCondition;
import io.github.gromoff97.awium.conditioning.conditions.ObjectCondition;
import io.github.gromoff97.awium.conditioning.conditions.OptionalCondition;
import io.github.gromoff97.awium.conditioning.conditions.StringCondition;
import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.sources.Source.OptionalSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.SATISFIED;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.UNSATISFIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ObjectOptionalAndScalarConditionsTest {

    @Test
    void objectConditionsPreserveNarrowAndExtractTypes() throws Exception {
        var actual = new Child("ready");
        Source<Parent> source = () -> actual;

        assertSame(actual, await(source).until(ObjectCondition.sameAs(actual)));
        assertSame(actual, await(source).until(ObjectCondition.notSameAs(new Child("ready"))));
        assertSame(actual, await(source).until(ObjectCondition.in(actual, new Parent())));
        assertSame(actual, await(source).until(ObjectCondition.notIn(new Parent())));
        assertSame(actual, await(source).until(ObjectCondition.matches(value -> value instanceof Child)));

        Child child = await(source).until(ObjectCondition.instanceOf(Child.class));
        Child exact = await(source).until(ObjectCondition.exactInstanceOf(Child.class));
        String status = await(source).until(ObjectCondition.extracting(
                value -> ((Child) value).status(), ObjectCondition.equalTo("ready")));

        assertSame(actual, child);
        assertSame(actual, exact);
        assertEquals("ready", status);
        assertEquals(UNSATISFIED, ObjectCondition.exactInstanceOf(Parent.class)
                .evaluate(actual).status());
    }

    @Test
    void optionalOverloadsReturnValuesAndNarrowedTypes() {
        OptionalSource<Object> source = () -> Optional.of("ready");

        Object expected = await(source).until(OptionalCondition.hasValue((Object) "ready"));
        Object selected = await(source).until(OptionalCondition.hasValue(value -> value.toString().startsWith("r")));
        String narrowed = await(source).until(OptionalCondition.hasValue(String.class));
        String nested = await((OptionalSource<String>) () -> Optional.of("ready")).until(OptionalCondition.hasValue(StringCondition.startsWith("rea")));

        assertEquals("ready", expected);
        assertEquals("ready", selected);
        assertEquals("ready", narrowed);
        assertEquals("ready", nested);
    }

    @Test
    void comparableConditionsCoverInclusiveAndExclusiveRelations() {
        assertEquals(5, await((Source<Integer>) () -> 5).until(ComparableCondition.greaterThan(4)));
        assertEquals(5, await((Source<Integer>) () -> 5).until(ComparableCondition.atLeast(5)));
        assertEquals(5, await((Source<Integer>) () -> 5).until(ComparableCondition.lessThan(6)));
        assertEquals(5, await((Source<Integer>) () -> 5).until(ComparableCondition.atMost(5)));
        assertEquals(5, await((Source<Integer>) () -> 5).until(ComparableCondition.between(5, 6)));
        assertEquals(5, await((Source<Integer>) () -> 5).until(ComparableCondition.strictlyBetween(4, 6)));
    }

    @Test
    void stringConditionsCoverTextPatternsAndSizes() throws Exception {
        String actual = "Ready 42";

        assertSame(actual, await((Source<String>) () -> actual).until(StringCondition.nonBlank));
        assertSame(actual, await((Source<String>) () -> actual).until(StringCondition.containsText("Ready", "42")));
        assertSame(actual, await((Source<String>) () -> actual).until(StringCondition.doesNotContainText("failed")));
        assertSame(actual, await((Source<String>) () -> actual).until(StringCondition.containsIgnoringCase("READY")));
        assertSame(actual, await((Source<String>) () -> actual).until(StringCondition.startsWith("Ready")));
        assertSame(actual, await((Source<String>) () -> actual).until(StringCondition.endsWith("42")));
        assertSame(actual, await((Source<String>) () -> actual).until(StringCondition.matchesRegex("Ready \\d+")));
        assertSame(actual, await((Source<String>) () -> actual).until(StringCondition.equalIgnoringCase("ready 42")));
        assertSame(actual, await((Source<String>) () -> actual).until(StringCondition.lengthBetween(1, 20)));
        assertEquals(SATISFIED, StringCondition.blank.delegate().evaluate(" \n").status());
        assertEquals(UNSATISFIED, StringCondition.nonEmpty.delegate().evaluate("").status());
    }

    private static class Parent {
    }

    private static final class Child extends Parent {
        private final String status;

        private Child(String status) {
            this.status = status;
        }

        private String status() {
            return status;
        }
    }
}
