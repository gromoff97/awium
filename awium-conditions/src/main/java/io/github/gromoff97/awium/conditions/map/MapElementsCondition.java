package io.github.gromoff97.awium.conditions.map;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static io.github.gromoff97.awium.function.AwaitAttempt.bind;
import static io.github.gromoff97.awium.result.AttemptResult.Unsatisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

record MapElementsCondition<INPUT extends Map<?, ?>>(Part part,
        PreservingCondition<? extends Collection<?>> nested) implements PreservingCondition<INPUT> {
    enum Part {
        KEYS, VALUES, ENTRIES, ENTRY_CONTENTS;

        String label() {
            return switch (this) {
                case KEYS -> "keys";
                case VALUES -> "values";
                case ENTRIES, ENTRY_CONTENTS -> "entries";
            };
        }

        Iterable<?> elements(Map<?, ?> actual) {
            return switch (this) {
                case KEYS -> actual.keySet();
                case VALUES -> actual.values();
                case ENTRIES -> actual.entrySet();
                case ENTRY_CONTENTS -> MapEntries.view(actual);
            };
        }
    }

    @Override public String description() { return "map " + part.label() + ": " + nested.description(); }
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
            var result = evaluator.probeAt(view(actual), "map " + part.label());
            if (result instanceof Unsatisfied<?> failure) {
                return unsatisfied(failure.diagnostic().withMismatch(
                        "map " + part.label() + ": " + failure.mismatch()), failure.cause());
            }
            return result.mapValue(ignored -> actual);
        };
    }
    private Collection<Object> view(INPUT actual) {
        return new AbstractCollection<>() {
            private Iterable<?> values;
            @Override public int size() { return actual.size(); }
            @Override public Iterator<Object> iterator() {
                if (values == null) values = part.elements(actual);
                var iterator = values.iterator();
                return new Iterator<>() {
                    @Override public boolean hasNext() { return iterator.hasNext(); }
                    @Override public Object next() { return iterator.next(); }
                };
            }
        };
    }
}
