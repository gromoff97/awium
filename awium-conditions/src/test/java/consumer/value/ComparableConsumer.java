package consumer.value;

import io.github.gromoff97.awium.result.AwaitResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.conditions.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.Conditions.atLeast;
import static io.github.gromoff97.awium.conditions.Conditions.atMost;
import static io.github.gromoff97.awium.conditions.Conditions.between;
import static io.github.gromoff97.awium.conditions.Conditions.captured;
import static io.github.gromoff97.awium.conditions.Conditions.greaterThan;
import static io.github.gromoff97.awium.conditions.Conditions.lessThan;
import static io.github.gromoff97.awium.conditions.Conditions.strictlyBetween;
import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.OptionalConditions.hasValue;
import static java.lang.Integer.compare;
import static java.time.Duration.ofNanos;

public final class ComparableConsumer {
    public static void missingBound() { greaterThan(null); }
    public static void missingLowerBound() { between(null, 1); }
    public static void missingUpperBound() { between(1, null); }

    public static List<Integer> nonComparableBounds() {
        record Limit(int number) {}
        record Measurement(int number) implements Comparable<Limit> {
            @Override public int compareTo(Limit limit) { return compare(number, limit.number()); }
        }
        var actual = new Measurement(10);
        var waiting = await(() -> actual).every(ofNanos(1));
        var values = new ArrayList<Integer>();
        for (var check : List.of(greaterThan(new Limit(5)), atLeast(new Limit(10)),
                lessThan(new Limit(15)), atMost(new Limit(10)))) {
            values.add(waiting.until(check).number());
            AwaitResult<Measurement, Measurement> result = tryAwait(() -> actual).until(check);
            values.add(result.orThrow().number());
            values.add(waiting.until(captured(check, check)).getLast().number());
        }
        values.add(await(() -> Optional.of(actual)).until(hasValue(atLeast(new Limit(5)))).number());
        values.add(await(() -> Map.of("measurement", actual)).until(valueFor("measurement", lessThan(new Limit(15)))).number());
        return values;
    }

    public static List<Integer> subclassBounds() {
        class Score implements Comparable<Score> {
            private final int number;
            Score(int number) { this.number = number; }
            int number() { return number; }
            @Override public int compareTo(Score other) { return compare(number, other.number); }
        }
        class Bound extends Score { Bound(int number) { super(number); } }
        var actual = new Score(10);
        var waiting = await(() -> actual).every(ofNanos(1));
        var values = new ArrayList<Integer>();
        for (var check : List.of(greaterThan(new Bound(5)), atLeast(new Bound(10)),
                lessThan(new Bound(15)), atMost(new Bound(10)),
                between(new Bound(5), new Bound(15)), strictlyBetween(new Bound(5), new Bound(15)))) {
            values.add(waiting.until(check).number());
            AwaitResult<Score, Score> result = tryAwait(() -> actual).until(check);
            values.add(result.orThrow().number());
            values.add(waiting.until(captured(check, check)).getLast().number());
        }
        return values;
    }

    public static List<Integer> unrelatedComparableBounds() {
        record Threshold(int number) implements Comparable<Threshold> {
            @Override public int compareTo(Threshold other) { return compare(number, other.number); }
        }
        record Reading(int number) implements Comparable<Threshold> {
            @Override public int compareTo(Threshold threshold) { return compare(number, threshold.number()); }
        }
        var actual = new Reading(10);
        var values = new ArrayList<Integer>();
        for (var check : List.of(greaterThan(new Threshold(5)), atLeast(new Threshold(10)),
                lessThan(new Threshold(15)), atMost(new Threshold(10)),
                between(new Threshold(5), new Threshold(15)), strictlyBetween(new Threshold(5), new Threshold(15)))) {
            values.add(await(() -> actual).until(check).number());
        }
        values.add(await(() -> List.of(actual)).until(singleElement(greaterThan(new Threshold(5)))).number());
        PreservingCondition<Integer> positive = greaterThan(0);
        values.add(await(() -> 1).until(positive));
        return values;
    }
}
