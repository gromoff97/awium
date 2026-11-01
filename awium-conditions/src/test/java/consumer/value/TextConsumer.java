package consumer.value;

import io.github.gromoff97.awium.result.AwaitResult;
import java.nio.CharBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.conditions.collection.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.captured;
import static io.github.gromoff97.awium.conditions.map.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.optional.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.conditions.string.StringConditions.contentEquals;
import static io.github.gromoff97.awium.conditions.string.StringConditions.hasLength;
import static io.github.gromoff97.awium.conditions.string.StringConditions.matchesRegex;
import static io.github.gromoff97.awium.conditions.string.StringConditions.startsWith;
import static java.time.Duration.ofNanos;

public final class TextConsumer {
    public static String stringPrefix(String actual, PreservingCondition<CharSequence> condition) {
        return await(() -> actual).until(condition).substring(0, 5);
    }

    public static int builderCapacity(StringBuilder actual, PreservingCondition<CharSequence> condition) {
        return await(() -> actual).until(condition).capacity();
    }

    public static int diagnosticBuilderCapacity(StringBuilder actual, PreservingCondition<CharSequence> condition) {
        return switch (tryAwait(() -> actual).until(condition)) {
            case AwaitResult.Succeeded(var history, var value) -> value.capacity();
            case AwaitResult.Failed(var history, var failure) -> throw new AssertionError(failure);
        };
    }

    public static int capturedBuilderCapacity(StringBuilder actual, PreservingCondition<CharSequence> condition) {
        return await(() -> actual).every(ofNanos(1)).until(captured(condition, condition)).getLast().capacity();
    }

    public static int optionalBuilderCapacity(StringBuilder actual) {
        return await(() -> Optional.of(actual)).until(hasValue(startsWith(CharBuffer.wrap("rea")))).capacity();
    }

    public static int mappedBuilderCapacity(StringBuilder actual) {
        return await(() -> Map.of("text", actual)).until(valueFor("text", hasLength(5))).capacity();
    }

    public static int selectedBufferPosition(CharBuffer actual) {
        return await(() -> List.of(actual)).until(singleElement(matchesRegex("ready"))).position();
    }

    public static int contentEqualsBufferPosition(CharBuffer actual, CharSequence expected) {
        return await(() -> actual).until(contentEquals(expected)).position();
    }

    public static int nestedContentEqualsCapacity(StringBuilder actual, CharSequence expected) {
        return await(() -> List.of(actual)).until(singleElement(contentEquals(expected))).capacity();
    }
}
