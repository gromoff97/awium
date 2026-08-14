package io.github.gromoff97.awium.conditioning.providers;

import io.github.gromoff97.awium.conditioning.CheckedConsumer;
import io.github.gromoff97.awium.conditioning.CheckedFunction;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;

import java.util.function.Supplier;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.conditioning.Evaluation.assertionUnsatisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static java.util.Objects.requireNonNull;

public final class ConditionProvider {

    private ConditionProvider() {
        throw new AssertionError("Utility class");
    }

    public static <S, R> Condition<S, R> condition(
            String description,
            CheckedFunction<? super S, Evaluation<R>> evaluation) {
        if (requireNonNull(description, "description must not be null").isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        return condition(() -> description, evaluation);
    }

    static <S, R> Condition<S, R> condition(
            Supplier<String> description,
            CheckedFunction<? super S, Evaluation<R>> evaluation) {
        requireNonNull(description, "description must not be null");
        requireNonNull(evaluation, "evaluation must not be null");
        return new Condition<>() {
            @Override
            public Evaluation<R> evaluate(S actual) throws Exception {
                return evaluation.apply(actual);
            }

            @Override
            public String description() {
                return description.get();
            }
        };
    }

    static <S> PreservingCondition<S> preservingCondition(
            Supplier<String> description,
            CheckedFunction<? super S, Evaluation<S>> evaluation) {
        requireNonNull(evaluation, "evaluation must not be null");
        return PreservingCondition.of(new RuntimeCondition<>(evaluation::apply, description));
    }

    static <S> PreservingCondition<S> matchingCondition(String subject, String description,
            String mismatch, boolean positive, Predicate<? super S> matches) {
        return preservingCondition(() -> description, actual -> {
            if (actual == null) {
                return unsatisfied(subject + " was null");
            }
            return matches.test(actual) == positive ? satisfied(actual) : unsatisfied(mismatch);
        });
    }

    static <T> boolean anyMatch(Iterable<T> values, Predicate<? super T> matches) {
        for (T value : values) {
            if (matches.test(value)) {
                return true;
            }
        }
        return false;
    }

    static <A, E> boolean allFound(Iterable<A> actual, Collection<E> remaining,
            BiPredicate<? super A, ? super E> matches) {
        for (A value : actual) {
            remaining.removeIf(expected -> matches.test(value, expected));
            if (remaining.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static <A, E> int matchCount(Iterator<A> actual, Collection<E> remaining,
            BiPredicate<? super A, ? super E> matches) {
        int matched = 0;
        while (actual.hasNext()) {
            A value = actual.next();
            boolean found = false;
            Iterator<E> candidates = remaining.iterator();
            while (candidates.hasNext()) {
                if (matches.test(value, candidates.next())) {
                    candidates.remove();
                    matched++;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return -1;
            }
        }
        return matched;
    }

    public static <S> PreservingCondition<S> asserted(
            CheckedConsumer<? super S> assertion) {
        requireNonNull(assertion, "assertion must not be null");
        return PreservingCondition.of(RuntimeCondition.open(
                ConditionProvider.<S, S>passed(actual -> {
                    assertion.accept(actual);
                    return actual;
                })));
    }

    public static <S, R> Condition<S, R> passed(
            CheckedFunction<? super S, ? extends R> assertion) {
        requireNonNull(assertion, "assertion must not be null");
        return condition("assertion passes", actual -> {
            try {
                return satisfied(assertion.apply(actual));
            } catch (AssertionError error) {
                return assertionUnsatisfied("assertion did not pass", error);
            }
        });
    }
}
