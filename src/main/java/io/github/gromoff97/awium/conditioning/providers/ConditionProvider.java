package io.github.gromoff97.awium.conditioning.providers;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static io.github.gromoff97.awium.conditioning.Evaluation.assertionUnsatisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static java.util.Objects.deepEquals;
import static java.util.Objects.requireNonNull;

public final class ConditionProvider {

    private ConditionProvider() {
        throw new AssertionError("Utility class");
    }

    public static <S, R> Condition<S, R> condition(String description, CheckedFunction<? super S, Evaluation<R>> evaluation) {
        if (requireNonNull(description, "description must not be null").isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        return condition(() -> description, evaluation);
    }

    static <S, R> Condition<S, R> condition(Supplier<String> description, CheckedFunction<? super S, Evaluation<R>> evaluation) {
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

    static <S> PreservingCondition<S> preservingCondition(Supplier<String> description, CheckedFunction<? super S, Evaluation<S>> evaluation) {
        return new PreservingCondition<>(condition(description, evaluation));
    }

    static <S> PreservingCondition<S> matchingCondition(String subject, String description, String mismatch, boolean positive, Predicate<? super S> matches) {
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

    static <A, E> boolean containsAllMatches(Iterable<A> actual, Collection<E> remainingExpected,
            BiPredicate<? super A, ? super E> matches) {
        for (A value : actual) {
            remainingExpected.removeIf(expected -> matches.test(value, expected));
            if (remainingExpected.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static <A, E> boolean matchesExactly(Iterator<A> actual, Collection<E> remainingExpected,
            BiPredicate<? super A, ? super E> matches) {
        while (actual.hasNext()) {
            A value = actual.next();
            boolean found = false;
            Iterator<E> candidates = remainingExpected.iterator();
            while (candidates.hasNext()) {
                if (matches.test(value, candidates.next())) {
                    candidates.remove();
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    static boolean equal(Object actual, Object expected) {
        record ValuePair(Object actual, Object expected) {}

        var pending = new ArrayDeque<ValuePair>();
        var visited = new HashSet<ValuePair>();
        pending.addLast(new ValuePair(actual, expected));
        while (!pending.isEmpty()) {
            ValuePair pair = pending.removeLast();
            Object left = pair.actual();
            Object right = pair.expected();
            if (left == right) {
                continue;
            }
            if (left instanceof Object[] leftObjects && right instanceof Object[] rightObjects) {
                if (!visited.add(pair)) {
                    continue;
                }
                if (leftObjects.length != rightObjects.length) {
                    return false;
                }
                for (int index = leftObjects.length - 1; index >= 0; index--) {
                    pending.addLast(new ValuePair(leftObjects[index], rightObjects[index]));
                }
            } else if (!deepEquals(left, right)) {
                return false;
            }
        }
        return true;
    }

    public static <S> PreservingCondition<S> asserted(CheckedConsumer<? super S> assertion) {
        requireNonNull(assertion, "assertion must not be null");
        return new PreservingCondition<>(passed(actual -> {
            assertion.accept(actual);
            return actual;
        }));
    }

    public static <S, R> Condition<S, R> passed(CheckedFunction<? super S, ? extends R> assertion) {
        requireNonNull(assertion, "assertion must not be null");
        return condition("assertion passes", actual -> {
            try {
                return satisfied(assertion.apply(actual));
            } catch (AssertionError error) {
                return assertionUnsatisfied("assertion did not pass", error);
            }
        });
    }

    @FunctionalInterface
    public interface CheckedConsumer<T> {

        void accept(T value) throws Exception;
    }

    @FunctionalInterface
    public interface CheckedFunction<T, R> {

        R apply(T value) throws Exception;
    }
}
