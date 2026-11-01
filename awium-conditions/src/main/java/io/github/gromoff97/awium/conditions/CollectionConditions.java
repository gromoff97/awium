package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.conditions.internal.CollectionContentCondition.Content;
import io.github.gromoff97.awium.conditions.internal.CollectionContentCondition;
import io.github.gromoff97.awium.conditions.internal.CollectionSequenceCondition.Sequence;
import io.github.gromoff97.awium.conditions.internal.CollectionSequenceCondition;
import io.github.gromoff97.awium.conditions.internal.ContainsCondition;
import io.github.gromoff97.awium.conditions.internal.ContainsElementsCondition.Membership;
import io.github.gromoff97.awium.conditions.internal.ContainsElementsCondition;
import io.github.gromoff97.awium.conditions.internal.ElementAtCondition;
import io.github.gromoff97.awium.conditions.internal.EmptyCondition;
import io.github.gromoff97.awium.conditions.internal.HasNoDuplicatesCondition;
import io.github.gromoff97.awium.conditions.internal.HasSizeCondition;
import io.github.gromoff97.awium.conditions.internal.NullElementsCondition;
import io.github.gromoff97.awium.conditions.internal.QuantifiedCondition.Quantifier;
import io.github.gromoff97.awium.conditions.internal.QuantifiedCondition;
import io.github.gromoff97.awium.conditions.internal.SingleElementCondition;
import io.github.gromoff97.awium.conditions.internal.SortedCondition;
import io.github.gromoff97.awium.conditions.function.CheckedPredicate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.SequencedCollection;

import static io.github.gromoff97.awium.AwaitCondition.ComparisonCondition;
import static io.github.gromoff97.awium.AwaitCondition.InputCondition;
import static io.github.gromoff97.awium.conditions.Conditions.matches;
import static io.github.gromoff97.awium.conditions.internal.NestedCondition.compose;
import static io.github.gromoff97.awium.conditions.internal.SizeRelation.AT_LEAST;
import static io.github.gromoff97.awium.conditions.internal.SizeRelation.AT_MOST;
import static io.github.gromoff97.awium.conditions.internal.SizeRelation.BETWEEN;
import static io.github.gromoff97.awium.conditions.internal.SizeRelation.EQUAL;
import static io.github.gromoff97.awium.conditions.internal.SizeRelation.GREATER;
import static io.github.gromoff97.awium.conditions.internal.SizeRelation.LESS;
import static io.github.gromoff97.awium.conditions.internal.SizeRelation.NOT_EQUAL;
import static java.util.function.Function.identity;

public final class CollectionConditions {

    public static final SelectedCondition<Collection<?>> singleElement = new SingleElementCondition();
    public static final PreservingCondition<Collection<?>> empty = new EmptyCondition<>("collection", Collection::isEmpty, true);
    public static final PreservingCondition<Collection<?>> nonEmpty = new EmptyCondition<>("collection", Collection::isEmpty, false);
    public static final PreservingCondition<Collection<?>> containsNull = new NullElementsCondition(false, false);
    public static final PreservingCondition<Collection<?>> doesNotContainNull = new NullElementsCondition(true, false);
    public static final PreservingCondition<Collection<?>> containsOnlyNulls = new NullElementsCondition(false, true);
    public static final PreservingCondition<Collection<?>> hasNoDuplicates = new HasNoDuplicatesCondition();

    private CollectionConditions() {}

    public static PreservingCondition<Collection<?>> hasSize(int expected) {
        return new HasSizeCondition<>("collection", "size", Collection::size, EQUAL, expected);
    }

    public static PreservingCondition<Collection<?>> hasSizeNotEqualTo(int unexpected) {
        return new HasSizeCondition<>("collection", "size", Collection::size, NOT_EQUAL, unexpected);
    }

    public static PreservingCondition<Collection<?>> hasSizeGreaterThan(int lowerBound) {
        return new HasSizeCondition<>("collection", "size", Collection::size, GREATER, lowerBound);
    }

    public static PreservingCondition<Collection<?>> hasSizeAtLeast(int lowerBound) {
        return new HasSizeCondition<>("collection", "size", Collection::size, AT_LEAST, lowerBound);
    }

    public static PreservingCondition<Collection<?>> hasSizeLessThan(int upperBound) {
        return new HasSizeCondition<>("collection", "size", Collection::size, LESS, upperBound);
    }

    public static PreservingCondition<Collection<?>> hasSizeAtMost(int upperBound) {
        return new HasSizeCondition<>("collection", "size", Collection::size, AT_MOST, upperBound);
    }

    public static PreservingCondition<Collection<?>> hasSizeBetween(int lowerBound, int upperBound) {
        return new HasSizeCondition<>("collection", "size", Collection::size, BETWEEN, lowerBound, upperBound);
    }

    public static <ELEMENT> SelectedCondition<Collection<? extends ELEMENT>> singleElement(CheckedPredicate<? super ELEMENT> predicate) {
        return singleElement(matches(predicate));
    }

    public static <CHECKED> SelectedCondition<Collection<? extends CHECKED>> singleElement(
            PreservingCondition<? super CHECKED> nested) {
        return new SelectedCondition<>(compose("single element", nested, singleElement)) {};
    }

    public static <EXPECTED> SelectedCondition<Collection<? super EXPECTED>> singleElement(
            ComparisonCondition<EXPECTED> nested) {
        return new SelectedCondition<>(compose("single element", nested, singleElement)) {};
    }

    public static <ELEMENT, RESULT> Condition<Collection<? extends ELEMENT>, RESULT> singleElement(Condition<? super ELEMENT, RESULT> nested) {
        return compose("single element", nested, singleElement);
    }

    public static <ELEMENT> PreservingCondition<Collection<? extends ELEMENT>> all(CheckedPredicate<? super ELEMENT> predicate) {
        return all(matches(predicate));
    }

    public static <ELEMENT> PreservingCondition<Collection<? extends ELEMENT>> all(InputCondition<? super ELEMENT> nested) {
        return new QuantifiedCondition<>("collection", "elements", Quantifier.ALL, nested, identity());
    }

    public static <EXPECTED> PreservingCondition<Collection<? super EXPECTED>> all(ComparisonCondition<EXPECTED> nested) {
        return new QuantifiedCondition<>("collection", "elements", Quantifier.ALL, nested, identity());
    }

    public static <ELEMENT> PreservingCondition<Collection<? extends ELEMENT>> any(CheckedPredicate<? super ELEMENT> predicate) {
        return any(matches(predicate));
    }

    public static <ELEMENT> PreservingCondition<Collection<? extends ELEMENT>> any(InputCondition<? super ELEMENT> nested) {
        return new QuantifiedCondition<>("collection", "elements", Quantifier.ANY, nested, identity());
    }

    public static <EXPECTED> PreservingCondition<Collection<? super EXPECTED>> any(ComparisonCondition<EXPECTED> nested) {
        return new QuantifiedCondition<>("collection", "elements", Quantifier.ANY, nested, identity());
    }

    public static <ELEMENT> PreservingCondition<Collection<? extends ELEMENT>> none(CheckedPredicate<? super ELEMENT> predicate) {
        return none(matches(predicate));
    }

    public static <ELEMENT> PreservingCondition<Collection<? extends ELEMENT>> none(InputCondition<? super ELEMENT> nested) {
        return new QuantifiedCondition<>("collection", "elements", Quantifier.NONE, nested, identity());
    }

    public static <EXPECTED> PreservingCondition<Collection<? super EXPECTED>> none(ComparisonCondition<EXPECTED> nested) {
        return new QuantifiedCondition<>("collection", "elements", Quantifier.NONE, nested, identity());
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> contains(ELEMENT expected) {
        return new ContainsCondition<>("collection", "element", expected, false, identity(), Objects::deepEquals);
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> containsAll(Iterable<? extends ELEMENT> expected) {
        return new ContainsElementsCondition<>("collection", "elements", expected, Membership.ALL, identity(), Objects::deepEquals);
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContain(ELEMENT unexpected) {
        return new ContainsCondition<>("collection", "element", unexpected, true, identity(), Objects::deepEquals);
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContainAny(Iterable<? extends ELEMENT> unexpected) {
        return new ContainsElementsCondition<>("collection", "elements", unexpected, Membership.NONE, identity(), Objects::deepEquals);
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContainAll(Iterable<? extends ELEMENT> unexpected) {
        return new ContainsElementsCondition<>("collection", "elements", unexpected, Membership.NOT_ALL, identity(), Objects::deepEquals);
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> containsAny(Iterable<? extends ELEMENT> expected) {
        return new ContainsElementsCondition<>("collection", "elements", expected, Membership.ANY, identity(), Objects::deepEquals);
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> containsExactly(Iterable<? extends ELEMENT> expected) {
        return new CollectionContentCondition<>(expected, Content.EXACT, false);
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> doesNotContainExactly(Iterable<? extends ELEMENT> unexpected) {
        return new CollectionContentCondition<>(unexpected, Content.EXACT, true);
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> containsExactlyInAnyOrder(Iterable<? extends ELEMENT> expected) {
        return new CollectionContentCondition<>(expected, Content.ANY_ORDER, false);
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> doesNotContainExactlyInAnyOrder(Iterable<? extends ELEMENT> unexpected) {
        return new CollectionContentCondition<>(unexpected, Content.ANY_ORDER, true);
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> containsOnly(Iterable<? extends ELEMENT> expected) {
        return new CollectionContentCondition<>(expected, Content.ONLY, false);
    }

    public static <ELEMENT> PreservingCondition<Collection<? super ELEMENT>> subsetOf(Iterable<? extends ELEMENT> expected) {
        return new CollectionContentCondition<>(expected, Content.SUBSET, false);
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> startsWith(Iterable<? extends ELEMENT> expected) {
        return new CollectionSequenceCondition<>(expected, Sequence.PREFIX, false);
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> endsWith(Iterable<? extends ELEMENT> expected) {
        return new CollectionSequenceCondition<>(expected, Sequence.SUFFIX, false);
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> containsSequence(Iterable<? extends ELEMENT> expected) {
        return new CollectionSequenceCondition<>(expected, Sequence.SEQUENCE, false);
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> doesNotContainSequence(Iterable<? extends ELEMENT> unexpected) {
        return new CollectionSequenceCondition<>(unexpected, Sequence.SEQUENCE, true);
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> containsSubsequence(Iterable<? extends ELEMENT> expected) {
        return new CollectionSequenceCondition<>(expected, Sequence.SUBSEQUENCE, false);
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? super ELEMENT>> doesNotContainSubsequence(Iterable<? extends ELEMENT> unexpected) {
        return new CollectionSequenceCondition<>(unexpected, Sequence.SUBSEQUENCE, true);
    }

    public static final SelectedCondition<SequencedCollection<?>> firstElement = new ElementAtCondition(false);

    public static <ELEMENT> SelectedCondition<SequencedCollection<? extends ELEMENT>> firstElement(
            CheckedPredicate<? super ELEMENT> predicate) {
        return firstElement(matches(predicate));
    }

    public static <CHECKED> SelectedCondition<SequencedCollection<? extends CHECKED>> firstElement(
            PreservingCondition<? super CHECKED> nested) {
        return new SelectedCondition<>(compose("first element", nested, firstElement)) {};
    }

    public static <EXPECTED> SelectedCondition<SequencedCollection<? super EXPECTED>> firstElement(
            ComparisonCondition<EXPECTED> nested) {
        return new SelectedCondition<>(compose("first element", nested, firstElement)) {};
    }

    public static <ELEMENT, RESULT> Condition<SequencedCollection<? extends ELEMENT>, RESULT> firstElement(
            Condition<? super ELEMENT, RESULT> nested) {
        return compose("first element", nested, firstElement);
    }

    public static final SelectedCondition<SequencedCollection<?>> lastElement = new ElementAtCondition(true);

    public static <ELEMENT> SelectedCondition<SequencedCollection<? extends ELEMENT>> lastElement(
            CheckedPredicate<? super ELEMENT> predicate) {
        return lastElement(matches(predicate));
    }

    public static <CHECKED> SelectedCondition<SequencedCollection<? extends CHECKED>> lastElement(
            PreservingCondition<? super CHECKED> nested) {
        return new SelectedCondition<>(compose("last element", nested, lastElement)) {};
    }

    public static <EXPECTED> SelectedCondition<SequencedCollection<? super EXPECTED>> lastElement(
            ComparisonCondition<EXPECTED> nested) {
        return new SelectedCondition<>(compose("last element", nested, lastElement)) {};
    }

    public static <ELEMENT, RESULT> Condition<SequencedCollection<? extends ELEMENT>, RESULT> lastElement(
            Condition<? super ELEMENT, RESULT> nested) {
        return compose("last element", nested, lastElement);
    }

    public static SelectedCondition<SequencedCollection<?>> elementAt(int index) {
        return new ElementAtCondition(index);
    }

    public static <ELEMENT> SelectedCondition<SequencedCollection<? extends ELEMENT>> elementAt(
            int index, CheckedPredicate<? super ELEMENT> predicate) {
        return elementAt(index, matches(predicate));
    }

    public static <CHECKED> SelectedCondition<SequencedCollection<? extends CHECKED>> elementAt(
            int index, PreservingCondition<? super CHECKED> nested) {
        return new SelectedCondition<>(compose("element at index " + index, nested, elementAt(index))) {};
    }

    public static <EXPECTED> SelectedCondition<SequencedCollection<? super EXPECTED>> elementAt(
            int index, ComparisonCondition<EXPECTED> nested) {
        return new SelectedCondition<>(compose("element at index " + index, nested, elementAt(index))) {};
    }

    public static <ELEMENT, RESULT> Condition<SequencedCollection<? extends ELEMENT>, RESULT> elementAt(int index,
            Condition<? super ELEMENT, RESULT> nested) {
        return compose("element at index " + index, nested, elementAt(index));
    }

    public static <ELEMENT> PreservingCondition<SequencedCollection<? extends ELEMENT>> sorted(Comparator<? super ELEMENT> comparator) {
        return new SortedCondition<>(comparator);
    }

}
