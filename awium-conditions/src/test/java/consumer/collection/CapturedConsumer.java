package consumer.collection;

import io.github.gromoff97.awium.dsl.AwaitCondition;
import io.github.gromoff97.awium.dsl.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.dsl.AwaitCondition.CollectingCondition;
import io.github.gromoff97.awium.result.AwaitResult;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import static io.github.gromoff97.awium.dsl.Await.await;
import static io.github.gromoff97.awium.dsl.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.hasSize;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.captured;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.condition;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.equalTo;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.exactlyInstanceOf;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.instanceOf;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.isNotNull;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.matches;
import static io.github.gromoff97.awium.conditions.map.MapConditions.entryFor;
import static io.github.gromoff97.awium.conditions.map.MapConditions.singleEntry;
import static io.github.gromoff97.awium.conditions.map.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.present;
import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.fill;

public final class CapturedConsumer {
    private CapturedConsumer() {}

    public static List<AwaitCondition> explainedStages(AwaitCondition first, AwaitCondition last) {
        return captured(first, last).because("ordered observations").stages();
    }

    public record Payment(int stage, String detail) {
        public boolean pending() { return stage == 2; }
        public boolean ready() { return stage == 3; }
    }

    public static List<Optional<Payment>> optionalStages(List<Optional<Payment>> observations) {
        var source = observations.iterator();
        var values = await(source::next).every(ofNanos(1)).until(captured(
                hasValue(equalTo(observations.getFirst().orElseThrow())), hasValue(Payment::ready)));
        values.getLast().orElseThrow().stage();
        return values;
    }

    public static AwaitResult<List<Payment>, List<List<Payment>>> collectionStages(List<List<Payment>> observations) {
        var source = observations.iterator();
        var result = tryAwait(source::next).every(ofNanos(1)).until(captured(
                singleElement(equalTo(observations.getFirst().getFirst())), singleElement(Payment::ready)));
        result.orThrow().getLast().getFirst().stage();
        return result;
    }

    public static AwaitResult<Map<String, Payment>, List<Map<String, Payment>>> mapStages(List<Map<String, Payment>> observations) {
        var source = observations.iterator();
        var result = tryAwait(source::next).every(ofNanos(1)).until(captured(
                valueFor("user", equalTo(observations.getFirst().get("user"))).because("created"),
                valueFor("user", Payment::ready).because("ready")));
        result.orThrow().getLast().get("user").stage();
        return result;
    }

    public static List<Payment> alternatingStages(List<Payment> observations, boolean recorded) {
        var source = observations.iterator();
        var sequence = captured(equalTo(observations.getFirst()), matches(Payment::pending),
                equalTo(observations.getLast()), matches(Payment::ready));
        if (recorded) {
            var result = tryAwait(source::next).every(ofNanos(1)).until(sequence);
            return switch (result) {
                case AwaitResult.Succeeded(var history, var values) -> {
                    values.getLast().stage();
                    yield values;
                }
                case AwaitResult.Failed(var history, var failure) -> throw new IllegalStateException(failure.message());
            };
        }
        var values = await(source::next).every(ofNanos(1)).until(sequence);
        values.getLast().stage();
        return values;
    }

    public static List<List<Payment>> mixedChecks(List<List<Payment>> observations) {
        var source = observations.iterator();
        return await(source::next).every(ofNanos(1)).until(captured(hasSize(1), singleElement, singleElement(instanceOf(Payment.class))));
    }

    public static AwaitResult<List<Payment>, List<List<Payment>>> recordedMixedChecks(List<List<Payment>> observations) {
        var source = observations.iterator();
        return tryAwait(source::next).every(ofNanos(1)).until(captured(hasSize(1), singleElement, singleElement(instanceOf(Payment.class))));
    }

    public static List<Object> heterogeneousResults(List<Object> observations) {
        var source = observations.iterator();
        return await(source::next).every(ofNanos(1)).until(captured(instanceOf(String.class), instanceOf(Integer.class)));
    }

    public static AwaitResult<Object, List<Object>> recordedHeterogeneousResults(List<Object> observations) {
        var source = observations.iterator();
        return tryAwait(source::next).every(ofNanos(1)).until(captured(instanceOf(String.class), instanceOf(Integer.class)));
    }

    public static List<Integer> reusableCaptures(Payment payment) {
        var payments = new ArrayList<>(List.of(payment));
        var checks = captured(isNotNull, isNotNull).because("observations");
        var selections = captured(singleElement, singleElement).because("elements");
        ArrayList<Payment> original = await(() -> payments).every(ofNanos(1)).until(checks).getFirst();
        var selected = await(() -> payments).every(ofNanos(1)).until(selections);
        var strings = await(() -> List.of("ready")).every(ofNanos(1)).until(selections);
        var optionals = await(() -> Optional.of(payment)).every(ofNanos(1)).until(captured(present, present).because("present values"));
        var mapped = await(() -> Map.of("id", payment)).every(ofNanos(1)).until(captured(valueFor("id"), valueFor("id")).because("map values"));
        var entries = await(() -> Map.of("id", payment)).every(ofNanos(1)).until(captured(entryFor("id"), singleEntry).because("entries"));
        return List.of(original.getFirst().detail().length(), selected.getFirst().getFirst().detail().length(),
                strings.getFirst().getFirst().length(), optionals.getFirst().orElseThrow().detail().length(),
                mapped.getFirst().get("id").detail().length(), entries.getFirst().get("id").detail().length());
    }

    public static List<Object> explainedNarrowing(Payment payment) {
        return await(() -> (Object) payment).every(ofNanos(1)).until(
                captured(instanceOf(Payment.class), instanceOf(Payment.class)).because("payments"));
    }

    public static List<Optional<Payment>> oneOptional(Payment payment) {
        return await(() -> Optional.of(payment)).until(captured(present));
    }

    public static List<Object> narrowedStrings(List<Object> observations, boolean recorded) {
        var source = observations.iterator();
        var sequence = captured(instanceOf(String.class), exactlyInstanceOf(String.class));
        return recorded ? tryAwait(source::next).every(ofNanos(1)).until(sequence).orThrow()
                : await(source::next).every(ofNanos(1)).until(sequence);
    }

    public static List<List<Number>> nestedNarrowing(List<List<Number>> observations, boolean recorded) {
        var source = observations.iterator();
        var sequence = captured(singleElement(instanceOf(Integer.class)), singleElement(exactlyInstanceOf(Integer.class)));
        return recorded ? tryAwait(source::next).every(ofNanos(1)).until(sequence).orThrow()
                : await(source::next).every(ofNanos(1)).until(sequence);
    }

    public static List<Number> comparedNumbers(List<Number> observations, boolean recorded) {
        var source = observations.iterator();
        var sequence = captured(equalTo(new BigDecimal("1.00")), equalTo(new BigInteger("2")), equalTo(1024L)).because("numeric states");
        return recorded ? tryAwait(source::next).every(ofNanos(1)).until(sequence).orThrow()
                : await(source::next).every(ofNanos(1)).until(sequence);
    }

    public static List<Integer> projectedObservations() {
        var source = List.of("a", "bb").iterator();
        return await(() -> source.next().length()).every(ofNanos(1)).until(captured(equalTo(1), equalTo(2)));
    }

    public static List<Payment> orderedStates(List<Payment> observations) {
        var source = observations.iterator();
        return await(source::next).every(ofNanos(1)).until(captured(matches(payment -> payment.stage() == 1),
                matches(payment -> payment.stage() == 2), matches(payment -> payment.stage() == 3)));
    }

    public static List<String> nullableObservations() {
        return await((Callable<String>) () -> null).every(ofNanos(1)).until(captured(
                condition("first null", value -> satisfied(value)), condition("second null", value -> satisfied(value))));
    }

    @SuppressWarnings("unchecked")
    public static List<Integer> copiedArray(boolean recorded) {
        PreservingCondition<Integer>[] stages = (PreservingCondition<Integer>[]) new PreservingCondition<?>[]{
                matches((Integer value) -> value == 1), matches((Integer value) -> value == 2)};
        var sequence = captured(stages);
        fill(stages, matches((Integer value) -> false));
        int[] calls = {0};
        Callable<Integer> source = () -> {
            fill(stages, matches((Integer value) -> false));
            return ++calls[0];
        };
        return recorded ? tryAwait(source).every(ofNanos(1)).until(sequence).orThrow()
                : await(source).every(ofNanos(1)).until(sequence);
    }

    public static CollectingCondition<?> nullStages() {
        return captured((PreservingCondition<String>[]) null);
    }
}
