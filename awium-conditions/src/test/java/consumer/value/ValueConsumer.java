package consumer.value;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.math.BigDecimal;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.Conditions.asserted;
import static io.github.gromoff97.awium.conditions.Conditions.captured;
import static io.github.gromoff97.awium.conditions.Conditions.exactlyInstanceOf;
import static io.github.gromoff97.awium.conditions.Conditions.in;
import static io.github.gromoff97.awium.conditions.Conditions.instanceOf;
import static io.github.gromoff97.awium.conditions.Conditions.isNotNull;
import static io.github.gromoff97.awium.conditions.Conditions.isNull;
import static io.github.gromoff97.awium.conditions.Conditions.matches;
import static io.github.gromoff97.awium.conditions.Conditions.notEqualTo;
import static io.github.gromoff97.awium.conditions.Conditions.notIn;
import static io.github.gromoff97.awium.conditions.Conditions.notSameAs;
import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.OptionalConditions.absent;
import static io.github.gromoff97.awium.conditions.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.conditions.OptionalConditions.present;
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
}
