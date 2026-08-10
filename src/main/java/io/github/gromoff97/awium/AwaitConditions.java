package io.github.gromoff97.awium;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedCollection;

public final class AwaitConditions {

    public static final Present present = OptionalConditions.present();
    public static final StructuralCondition empty = StructuralConditions.empty();
    public static final StructuralCondition nonEmpty =
            StructuralConditions.nonEmpty();
    public static final Condition<Optional<?>, Void> absent =
            OptionalConditions.absent();
    public static final Condition<Object, Void> isNull = ObjectConditions.isNull();
    public static final PreservingCondition<Object> isNotNull =
            ObjectConditions.isNotNull();

    private AwaitConditions() {
    }

    public static PreservingCondition<Object> equalTo(Object expected) {
        return ObjectConditions.equalTo(expected);
    }

    public static PreservingCondition<Object> notEqualTo(Object unexpected) {
        return ObjectConditions.notEqualTo(unexpected);
    }

    public static StructuralCondition sizeExactly(int expected) {
        return StructuralConditions.sizeExactly(expected);
    }

    public static StructuralCondition sizeNotExactly(int unexpected) {
        return StructuralConditions.sizeNotExactly(unexpected);
    }

    public static StructuralCondition sizeGreaterThan(int lowerBound) {
        return StructuralConditions.sizeGreaterThan(lowerBound);
    }

    public static StructuralCondition sizeAtLeast(int lowerBound) {
        return StructuralConditions.sizeAtLeast(lowerBound);
    }

    public static StructuralCondition sizeLessThan(int upperBound) {
        return StructuralConditions.sizeLessThan(upperBound);
    }

    public static StructuralCondition sizeAtMost(int upperBound) {
        return StructuralConditions.sizeAtMost(upperBound);
    }

    public static <T> Condition<Optional<T>, T> hasValueEqualTo(T expected) {
        return OptionalConditions.hasValueEqualTo(expected);
    }

    public static <T> Condition<Optional<T>, T> hasValueNotEqualTo(T unexpected) {
        return OptionalConditions.hasValueNotEqualTo(unexpected);
    }

    public static <E> PreservingCondition<Collection<? super E>> contains(
            E expected) {
        return CollectionConditions.contains(expected);
    }

    public static <E> PreservingCondition<Collection<? super E>> doesNotContain(
            E expected) {
        return CollectionConditions.doesNotContain(expected);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>> containsAll(
            E... expected) {
        return CollectionConditions.containsAll(expected);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>>
            doesNotContainAll(E... expected) {
        return CollectionConditions.doesNotContainAll(expected);
    }

    public static <E> PreservingCondition<Collection<? super E>>
            containsAllElementsOf(Collection<? extends E> expected) {
        return CollectionConditions.containsAllElementsOf(expected);
    }

    public static <E> PreservingCondition<Collection<? super E>>
            doesNotContainAllElementsOf(Collection<? extends E> expected) {
        return CollectionConditions.doesNotContainAllElementsOf(expected);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>> containsAnyOf(
            E... expected) {
        return CollectionConditions.containsAnyOf(expected);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>> containsNoneOf(
            E... expected) {
        return CollectionConditions.containsNoneOf(expected);
    }

    public static <E> PreservingCondition<Collection<? super E>>
            containsAnyElementsOf(Collection<? extends E> expected) {
        return CollectionConditions.containsAnyElementsOf(expected);
    }

    public static <E> PreservingCondition<Collection<? super E>>
            containsNoElementsOf(Collection<? extends E> expected) {
        return CollectionConditions.containsNoElementsOf(expected);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<SequencedCollection<? super E>>
            containsExactly(E... expected) {
        return CollectionConditions.containsExactly(expected);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<SequencedCollection<? super E>>
            doesNotContainExactly(E... expected) {
        return CollectionConditions.doesNotContainExactly(expected);
    }

    public static <E> PreservingCondition<SequencedCollection<? super E>>
            containsExactlyElementsOf(Collection<? extends E> expected) {
        return CollectionConditions.containsExactlyElementsOf(expected);
    }

    public static <E> PreservingCondition<SequencedCollection<? super E>>
            doesNotContainExactlyElementsOf(
                    Collection<? extends E> expected) {
        return CollectionConditions.doesNotContainExactlyElementsOf(expected);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>>
            containsExactlyInAnyOrder(E... expected) {
        return CollectionConditions.containsExactlyInAnyOrder(expected);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> PreservingCondition<Collection<? super E>>
            doesNotContainExactlyInAnyOrder(E... expected) {
        return CollectionConditions.doesNotContainExactlyInAnyOrder(expected);
    }

    public static <E> PreservingCondition<Collection<? super E>>
            containsExactlyInAnyOrderElementsOf(
                    Collection<? extends E> expected) {
        return CollectionConditions.containsExactlyInAnyOrderElementsOf(
                expected);
    }

    public static <E> PreservingCondition<Collection<? super E>>
            doesNotContainExactlyInAnyOrderElementsOf(
                    Collection<? extends E> expected) {
        return CollectionConditions.doesNotContainExactlyInAnyOrderElementsOf(
                expected);
    }

    public static <K> PreservingCondition<Map<? super K, ?>> containsKey(
            K expected) {
        return MapConditions.containsKey(expected);
    }

    public static <K> PreservingCondition<Map<? super K, ?>> doesNotContainKey(
            K expected) {
        return MapConditions.doesNotContainKey(expected);
    }

    public static <V> PreservingCondition<Map<?, ? super V>> containsValue(
            V expected) {
        return MapConditions.containsValue(expected);
    }

    public static <V> PreservingCondition<Map<?, ? super V>>
            doesNotContainValue(V expected) {
        return MapConditions.doesNotContainValue(expected);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            containsEntry(K key, V value) {
        return MapConditions.containsEntry(key, value);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            doesNotContainEntry(K key, V value) {
        return MapConditions.doesNotContainEntry(key, value);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            containsAllEntriesOf(Map<? extends K, ? extends V> expected) {
        return MapConditions.containsAllEntriesOf(expected);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            doesNotContainAllEntriesOf(
                    Map<? extends K, ? extends V> expected) {
        return MapConditions.doesNotContainAllEntriesOf(expected);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            containsAnyEntriesOf(Map<? extends K, ? extends V> expected) {
        return MapConditions.containsAnyEntriesOf(expected);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            containsNoEntriesOf(Map<? extends K, ? extends V> expected) {
        return MapConditions.containsNoEntriesOf(expected);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            containsExactlyEntriesOf(
                    Map<? extends K, ? extends V> expected) {
        return MapConditions.containsExactlyEntriesOf(expected);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            doesNotContainExactlyEntriesOf(
                    Map<? extends K, ? extends V> expected) {
        return MapConditions.doesNotContainExactlyEntriesOf(expected);
    }

    public static <S, R> Condition<S, R> condition(
            String description,
            ThrowingFunction<? super S, Evaluation<R>> evaluation) {
        String checked = Validation.nonBlank(description, "description");
        Objects.requireNonNull(evaluation, "evaluation must not be null");
        return new Condition<>() {
            @Override
            public Evaluation<R> evaluate(S actual) throws Exception {
                return evaluation.apply(actual);
            }

            @Override
            public String description() {
                return checked;
            }
        };
    }

    public static <S> PreservingCondition<S> asserted(
            ThrowingConsumer<? super S> assertion) {
        Objects.requireNonNull(assertion, "assertion must not be null");
        return new PreservingCondition<>(new ConditionRuntime<>(actual -> {
            try {
                assertion.accept(actual);
                return Evaluation.satisfied(actual);
            } catch (AssertionError error) {
                return Evaluation.assertionUnsatisfied(
                        "assertion did not pass", error);
            }
        }, () -> "assertion to pass", null));
    }

    public static <S, R> Condition<S, R> passed(
            ThrowingFunction<? super S, ? extends R> assertion) {
        Objects.requireNonNull(assertion, "assertion must not be null");
        return condition("assertion to pass", actual -> {
            try {
                R result = assertion.apply(actual);
                return Evaluation.satisfied(result);
            } catch (AssertionError error) {
                return Evaluation.assertionUnsatisfied(
                        "assertion did not pass", error);
            }
        });
    }
}
