package io.github.gromoff97.awium.internal;

import java.util.HexFormat;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.lang.Character.isISOControl;
import static java.lang.Long.divideUnsigned;
import static java.lang.Long.remainderUnsigned;
import static java.lang.Thread.currentThread;
import static java.util.Arrays.deepToString;

public final class DiagnosticText {

    private static final List<TimeUnit> DURATION_UNITS = List.of(
            TimeUnit.DAYS, TimeUnit.HOURS, TimeUnit.MINUTES, TimeUnit.SECONDS,
            TimeUnit.MILLISECONDS, TimeUnit.MICROSECONDS, TimeUnit.NANOSECONDS);

    private DiagnosticText() {}

    public static String renderValue(Object value) { return renderValue(value, ignored -> {}); }

    static String renderValue(Object value, Consumer<Throwable> onFailure) {
        return renderSafely(() -> {
            if (value instanceof CharSequence) return quoted(value.toString(), '"');
            if (value instanceof Character) return quoted(value.toString(), '\'');
            String rendered = deepToString(new Object[] {value});
            return rendered.substring(1, rendered.length() - 1);
        }, onFailure);
    }

    public static String renderCause(Throwable cause) { return renderCause(cause, ignored -> {}); }

    static String renderCause(Throwable cause, Consumer<Throwable> onFailure) {
        if (cause == null) return "null";
        String message = renderSafely(cause::getMessage, onFailure);
        return cause.getClass().getName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    static String unsignedDuration(long remainingNanos) {
        StringJoiner result = new StringJoiner(" ").setEmptyValue("0 ns");
        for (TimeUnit unit : DURATION_UNITS) {
            long unitNanos = unit.toNanos(1);
            long count = divideUnsigned(remainingNanos, unitNanos);
            remainingNanos = remainderUnsigned(remainingNanos, unitNanos);
            if (count != 0) {
                String suffix = switch (unit) {
                    case DAYS -> "d";
                    case HOURS -> "h";
                    case MINUTES -> "min";
                    case SECONDS -> "s";
                    case MILLISECONDS -> "ms";
                    case MICROSECONDS -> "µs";
                    case NANOSECONDS -> "ns";
                };
                result.add(count + " " + suffix);
            }
        }
        return result.toString();
    }

    static String renderSafely(Supplier<String> rendering, Consumer<Throwable> onFailure) {
        boolean interrupted = currentThread().isInterrupted();
        try {
            return rendering.get();
        } catch (Throwable failure) {
            onFailure.accept(failure);
            return "<unprintable>";
        } finally {
            if (interrupted) currentThread().interrupt();
        }
    }

    private static String quoted(String value, char quote) {
        var out = new StringBuilder().append(quote);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == quote || character == '\\') out.append('\\');
            switch (character) {
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (isISOControl(character)) out.append("\\u").append(HexFormat.of().toHexDigits(character));
                    else out.append(character);
                }
            }
        }
        return out.append(quote).toString();
    }

}
