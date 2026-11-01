package consumer.value;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AwaitResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.asserted;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.captured;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.equalTo;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.exactlyInstanceOf;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.in;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.instanceOf;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.isNotNull;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.isNull;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.matches;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.notEqualTo;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.notIn;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.notSameAs;
import static io.github.gromoff97.awium.conditions.map.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.absent;
import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.present;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public final class ValueConsumer {
    private ValueConsumer() {}

    public static List<Integer> candidateLengths() {
        var list = List.of("ready");
        var map = Map.of("ready", "ready");
        var optional = Optional.of("ready");
        return List.of(
                await(() -> "ready").until(in(List.of("pending", "ready"))).length(),
                await(() -> list).until(in(List.of(list))).getFirst().length(),
                await(() -> map).until(in(List.of(map))).get("ready").length(),
                await(() -> optional).until(in(List.of(optional))).orElseThrow().length(),
                tryAwait(() -> map).until(notIn(List.of(Map.of("failed", "failed")))).orThrow().get("ready").length());
    }

    public static List<String[]> arrayCandidates(String[] actual) {
        var waiting = await(() -> actual).every(ofNanos(1));
        String[] direct = waiting.until(in(singletonList(actual.clone())));
        AwaitResult<String[], String[]> result = tryAwait(() -> actual).until(in(singletonList(actual.clone())));
        var captured = waiting.until(captured(in(singletonList(actual.clone())), notIn(singletonList(new String[]{"failed"}))));
        return List.of(direct, result.orThrow(), captured.getFirst(), captured.getLast());
    }

    public static int primitiveArrayCandidate(int[] actual) {
        return await(() -> actual).until(in(singletonList(actual.clone())))[0];
    }

    public static List<String> negativeComparisons(String actual) {
        var waiting = await(() -> actual);
        return List.of(waiting.until(notEqualTo("different")), waiting.until(notSameAs("different")),
                waiting.until(notIn(List.of("one", "two"))));
    }

    public static List<String> broadPreservingCondition(String actual) {
        PreservingCondition<Object> check = asserted(ignored -> {});
        String value = await(() -> actual).until(check);
        AwaitResult<String, String> result = tryAwait(() -> actual).until(check);
        return List.of(value, result.orThrow());
    }

    public static List<String> captureNullThenPresent() {
        var observations = asList(null, "ready").iterator();
        return await(observations::next).every(ofNanos(1)).until(captured(isNull, isNotNull));
    }

    public static AwaitResult<String, List<String>> captureNullThenPresentResult() {
        var observations = asList(null, "ready").iterator();
        return tryAwait(observations::next).every(ofNanos(1)).until(captured(isNull, isNotNull));
    }

    public static String missingElement() {
        return await(() -> singletonList((String) null)).until(singleElement(isNull));
    }

    public static Optional<String> nestedAbsence(Optional<String> empty) {
        return await(() -> Optional.of(empty)).until(hasValue(absent));
    }

    public static Optional<String> mappedAbsence(Optional<String> empty) {
        return await(() -> Map.of("key", empty)).until(valueFor("key", absent));
    }

    public static List<String> narrowedText() {
        Callable<Optional<Object>> source = () -> Optional.of("ready");
        return List.of(await(() -> (Object) "ready").until(instanceOf(String.class)),
                await(() -> (Object) "ready").until(exactlyInstanceOf(String.class)),
                await(source::call).until(hasValue(instanceOf(String.class))));
    }

    public static Number broadenedNumber() {
        return await(() -> 1).until(instanceOf(Number.class));
    }

    public static int projectedOptionalLength() {
        return await(() -> Optional.of("ready").map(String::length)).until(present).intValue();
    }

    public static AwaitResult<Number, BigDecimal> narrowedResult(BigDecimal actual) {
        Callable<Number> source = () -> actual;
        return tryAwait(source).until(instanceOf(BigDecimal.class));
    }

    public static List<AwaitResult<String, String>> nullResults() {
        Callable<String> source = () -> null;
        return List.of(tryAwait(source).until(isNull), tryAwait(source).until(matches(actual -> actual == null)));
    }

    public static AwaitResult<Optional<String>, String> optionalResult() {
        return tryAwait(() -> Optional.of("payment")).until(present);
    }
    public static List<Integer> inferredCaptureLengths() {
        var value = await(() -> "ready").until(equalTo("ready"));
        var comparisons = await(() -> "ready").every(ofNanos(1)).until(captured(equalTo("ready"), notIn(List.of("failed"))));
        var checks = await(() -> "ready").every(ofNanos(1)).until(captured(isNotNull, matches(ignored -> true)));
        return List.of(value.length(), comparisons.getFirst().length(), checks.getLast().length());
    }

    public static String nullableEquality() {
        String missing = null;
        var value = await(() -> missing).until(equalTo(missing));
        return value;
    }

    public static Class<?> optionalClass(Class<?> expected) {
        Optional<Class<?>> actual = Optional.of(expected);
        return await(() -> actual).until(hasValue(equalTo(expected)));
    }

    public static PreservingCondition<String> optionalCondition(PreservingCondition<String> expected) {
        return await(() -> Optional.of(expected)).until(hasValue(equalTo(expected)));
    }

    public static Predicate<String> optionalPredicate(Predicate<String> expected) {
        return await(() -> Optional.of(expected)).until(hasValue(equalTo(expected)));
    }

}
