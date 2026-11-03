package io.github.gromoff97.awium.conditions.map;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static io.github.gromoff97.awium.function.AwaitAttempt.bind;
import static io.github.gromoff97.awium.result.AttemptResult.Unsatisfied;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

record MapElementsCondition<INPUT extends Map<?, ?>>(Part part,
        PreservingCondition<? extends Collection<?>> nested) implements PreservingCondition<INPUT> {
    enum Part {
        KEYS("keys"), VALUES("values"), ENTRIES("entries"), ENTRY_CONTENTS("entries");

        private final String label;

        Part(String label) { this.label = label; }

        Iterable<?> elements(Map<?, ?> actual) {
            return switch (this) {
                case KEYS -> actual.keySet();
                case VALUES -> actual.values();
                case ENTRIES -> actual.entrySet();
                case ENTRY_CONTENTS -> MapEntries.view(actual);
            };
        }
    }

    @Override public String description() { return "map " + part.label + ": " + nested.description(); }
    @Override public String explanation() { return nested.explanation(); }
    @Override public List<Reference<?>> references() {
        if (part != Part.ENTRY_CONTENTS) return nested.references();
        return nested.references().stream().<Reference<?>>map(ref -> new Reference<>(ref.label(), DeepEntry.reference(ref.value()))).toList();
    }
    @Override public AttemptResult<?> probe(INPUT actual) throws Throwable { return newAttempt().probe(actual); }

    @Override
    public AwaitAttempt<INPUT, INPUT> newAttempt() {
        AwaitAttempt<Collection<?>, ?> evaluator = bind(nested);
        return actual -> {
            if (actual == null) return unsatisfied("map was null");
            var result = evaluator.probeAt(view(actual, () -> part.elements(actual)), "map " + part.label);
            if (result instanceof Unsatisfied<?> failure) {
                return unsatisfied(failure.diagnostic().withMismatch(
                        "map " + part.label + ": " + failure.mismatch()), failure.cause());
            }
            return satisfied(actual, result.diagnostic());
        };
    }
    private static <ELEMENT> Collection<ELEMENT> view(Map<?, ?> actual, Supplier<Iterable<ELEMENT>> elements) {
        return new AbstractCollection<>() {
            private Iterable<ELEMENT> values;
            @Override public int size() { return actual.size(); }
            @Override public Iterator<ELEMENT> iterator() {
                if (values == null) values = elements.get();
                return values.iterator();
            }
        };
    }
}
