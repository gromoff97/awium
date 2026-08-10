package io.github.gromoff97.awium.conditioning.providers;

import io.github.gromoff97.awium.conditioning.CheckedConsumer;
import io.github.gromoff97.awium.conditioning.CheckedFunction;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.PresentCondition;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;
import io.github.gromoff97.awium.conditioning.conditions.StructuralCondition;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedCollection;

import static io.github.gromoff97.awium.conditioning.Evaluation.assertionUnsatisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static java.util.Objects.requireNonNull;

public final class ConditionProvider {

    public static final PresentCondition present = OptionalConditionProvider.present();
    public static final StructuralCondition empty = StructuralCondition.empty();
    public static final StructuralCondition nonEmpty =
            StructuralCondition.nonEmpty();
    public static final Condition<Optional<?>, Void> absent =
            OptionalConditionProvider.absent();
    public static final Condition<Object, Void> isNull = ObjectConditionProvider.isNull();
    public static final PreservingCondition<Object> isNotNull =
            ObjectConditionProvider.isNotNull();

    private ConditionProvider() {
        throw new AssertionError("Utility class");
    }

    public static PreservingCondition<Object> equalTo(Object expected) {
        return ObjectConditionProvider.equalTo(expected);
    }

    public static PreservingCondition<Object> notEqualTo(Object unexpected) {
        return ObjectConditionProvider.notEqualTo(unexpected);
    }

    public static StructuralCondition sizeExactly(int expected) {
        return StructuralCondition.sizeExactly(expected);
    }

    public static StructuralCondition sizeNotExactly(int unexpected) {
        return StructuralCondition.sizeNotExactly(unexpected);
    }

    public static StructuralCondition sizeGreaterThan(int lowerBound) {
        return StructuralCondition.sizeGreaterThan(lowerBound);
    }

    public static StructuralCondition sizeAtLeast(int lowerBound) {
        return StructuralCondition.sizeAtLeast(lowerBound);
    }

    public static StructuralCondition sizeLessThan(int upperBound) {
        return StructuralCondition.sizeLessThan(upperBound);
    }

    public static StructuralCondition sizeAtMost(int upperBound) {
        return StructuralCondition.sizeAtMost(upperBound);
    }

    public static <T> Condition<Optional<T>, T> hasValueEqualTo(T expected) {
        return OptionalConditionProvider.hasValueEqualTo(expected);
    }

    public static <T> Condition<Optional<T>, T> hasValueNotEqualTo(T unexpected) {
        return OptionalConditionProvider.hasValueNotEqualTo(unexpected);
    }

    public static <E> PreservingCondition<Collection<? super E>> contains(
            E expected) {
        return CollectionConditionProvider.contains(expected);
    }

    public static <E> PreservingCondition<Collection<? super E>> doesNotContain(
            E expected) {
        return CollectionConditionProvider.doesNotContain(expected);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>> containsAll(
            E... expected) {
        return CollectionConditionProvider.containsAll(expected);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>>
            doesNotContainAll(E... expected) {
        return CollectionConditionProvider.doesNotContainAll(expected);
    }

    public static <E> PreservingCondition<Collection<? super E>>
            containsAllElementsOf(Collection<? extends E> expected) {
        return CollectionConditionProvider.containsAllElementsOf(expected);
    }

    public static <E> PreservingCondition<Collection<? super E>>
            doesNotContainAllElementsOf(Collection<? extends E> expected) {
        return CollectionConditionProvider.doesNotContainAllElementsOf(expected);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>> containsAnyOf(
            E... expected) {
        return CollectionConditionProvider.containsAnyOf(expected);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>> containsNoneOf(
            E... expected) {
        return CollectionConditionProvider.containsNoneOf(expected);
    }

    public static <E> PreservingCondition<Collection<? super E>>
            containsAnyElementsOf(Collection<? extends E> expected) {
        return CollectionConditionProvider.containsAnyElementsOf(expected);
    }

    public static <E> PreservingCondition<Collection<? super E>>
            containsNoElementsOf(Collection<? extends E> expected) {
        return CollectionConditionProvider.containsNoElementsOf(expected);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<SequencedCollection<? super E>>
            containsExactly(E... expected) {
        return CollectionConditionProvider.containsExactly(expected);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<SequencedCollection<? super E>>
            doesNotContainExactly(E... expected) {
        return CollectionConditionProvider.doesNotContainExactly(expected);
    }

    public static <E> PreservingCondition<SequencedCollection<? super E>>
            containsExactlyElementsOf(Collection<? extends E> expected) {
        return CollectionConditionProvider.containsExactlyElementsOf(expected);
    }

    public static <E> PreservingCondition<SequencedCollection<? super E>>
            doesNotContainExactlyElementsOf(
                    Collection<? extends E> expected) {
        return CollectionConditionProvider.doesNotContainExactlyElementsOf(expected);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>>
            containsExactlyInAnyOrder(E... expected) {
        return CollectionConditionProvider.containsExactlyInAnyOrder(expected);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>>
            doesNotContainExactlyInAnyOrder(E... expected) {
        return CollectionConditionProvider.doesNotContainExactlyInAnyOrder(expected);
    }

    public static <E> PreservingCondition<Collection<? super E>>
            containsExactlyInAnyOrderElementsOf(
                    Collection<? extends E> expected) {
        return CollectionConditionProvider.containsExactlyInAnyOrderElementsOf(
                expected);
    }

    public static <E> PreservingCondition<Collection<? super E>>
            doesNotContainExactlyInAnyOrderElementsOf(
                    Collection<? extends E> expected) {
        return CollectionConditionProvider.doesNotContainExactlyInAnyOrderElementsOf(
                expected);
    }

    public static <K> PreservingCondition<Map<? super K, ?>> containsKey(
            K expected) {
        return MapConditionProvider.containsKey(expected);
    }

    public static <K> PreservingCondition<Map<? super K, ?>> doesNotContainKey(
            K expected) {
        return MapConditionProvider.doesNotContainKey(expected);
    }

    public static <V> PreservingCondition<Map<?, ? super V>> containsValue(
            V expected) {
        return MapConditionProvider.containsValue(expected);
    }

    public static <V> PreservingCondition<Map<?, ? super V>>
            doesNotContainValue(V expected) {
        return MapConditionProvider.doesNotContainValue(expected);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            containsEntry(K key, V value) {
        return MapConditionProvider.containsEntry(key, value);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            doesNotContainEntry(K key, V value) {
        return MapConditionProvider.doesNotContainEntry(key, value);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            containsAllEntriesOf(Map<? extends K, ? extends V> expected) {
        return MapConditionProvider.containsAllEntriesOf(expected);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            doesNotContainAllEntriesOf(
                    Map<? extends K, ? extends V> expected) {
        return MapConditionProvider.doesNotContainAllEntriesOf(expected);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            containsAnyEntriesOf(Map<? extends K, ? extends V> expected) {
        return MapConditionProvider.containsAnyEntriesOf(expected);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            containsNoEntriesOf(Map<? extends K, ? extends V> expected) {
        return MapConditionProvider.containsNoEntriesOf(expected);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            containsExactlyEntriesOf(
                    Map<? extends K, ? extends V> expected) {
        return MapConditionProvider.containsExactlyEntriesOf(expected);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            doesNotContainExactlyEntriesOf(
                    Map<? extends K, ? extends V> expected) {
        return MapConditionProvider.doesNotContainExactlyEntriesOf(expected);
    }

    public static <S, R> Condition<S, R> condition(
            String description,
            CheckedFunction<? super S, Evaluation<R>> evaluation) {
        requireNonNull(description, "description must not be null");
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        requireNonNull(evaluation, "evaluation must not be null");
        return new Condition<>() {
            @Override
            public Evaluation<R> evaluate(S actual) throws Exception {
                return evaluation.apply(actual);
            }

            @Override
            public String description() {
                return description;
            }
        };
    }

    public static <S> PreservingCondition<S> asserted(
            CheckedConsumer<? super S> assertion) {
        requireNonNull(assertion, "assertion must not be null");
        return PreservingCondition.of(new RuntimeCondition<>(actual -> {
            try {
                assertion.accept(actual);
                return satisfied(actual);
            } catch (AssertionError error) {
                return assertionUnsatisfied(
                        "assertion did not pass", error);
            }
        }, () -> "assertion to pass", null));
    }

    public static <S, R> Condition<S, R> passed(
            CheckedFunction<? super S, ? extends R> assertion) {
        requireNonNull(assertion, "assertion must not be null");
        return condition("assertion to pass", actual -> {
            try {
                R result = assertion.apply(actual);
                return satisfied(result);
            } catch (AssertionError error) {
                return assertionUnsatisfied(
                        "assertion did not pass", error);
            }
        });
    }
}
