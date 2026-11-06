# Diagnostics

[README](../README.md) · [Usage](usage.md)

## Results

`tryAwait(source).until(condition)` returns `AwaitResult<OBSERVED, RESULT>` (source,
then result type). Here `receipt` is a `Condition<Payment, String>`.

Both entry points run and record attempts identically. `await` returns the selected
value or throws; `tryAwait` returns the outcome for inspection.

```java
var result = tryAwait(paymentRepository::load).until(receipt);
// AwaitResult<Payment, String>
out.println(switch (result) {
    case Succeeded(var conditions, var value) -> value;
    case Failed(var conditions, var failure) -> failure.message();
});
```

Use `Succeeded.result()` or `Failed.failure()`. `AwaitFailure` exposes `reason()`,
`message()`, original `cause()`, and immutable `suppressed()` causes. `orThrow()` returns
the selected value, including null, or creates a failure wrapper. Reading results
never reruns checks.

On failure, ordinary `await` and `orThrow()` throw a wrapper exposing `failure()`:

| Wrapper | Reasons |
| --- | --- |
| `AwaitAssertionError extends AssertionError` | `TIMEOUT`, `PERSISTENCE_FAILED` |
| `AwaitExecutionException extends RuntimeException` | `SOURCE_FAILED`, `CONDITION_FAILED`, `INTERRUPTED`, `WAIT_FAILED` |

Wrappers use `AwaitFailure`'s message and cause; `getSuppressed()` mirrors `suppressed()`.
Additional failures are deduplicated by identity without changing original throwables.

Failure messages include the last attempt's details and unsatisfied reasons in
attempt order, grouped by condition. Earlier reasons may already be resolved.

## Condition diagnostics

Implementing `Condition` is enough to return named fields from `probe`:

```java
final class ThreeItems implements Condition<List<String>, List<String>> {
    @Override
    public String description() { return "collection has three items"; }

    @Override
    public AttemptResult<List<String>> probe(List<String> actual) {
        int size = actual.size();
        var details = new Diagnostic(null,
                new Reference<>("Expected size", 3), new Reference<>("Actual size", size));
        return size == 3 ? satisfied(actual, details)
                : unsatisfied(details.withMismatch("collection size did not match"));
    }
}
```

`Diagnostic` pairs an optional `mismatch` with an immutable list of named `fields`.
Here the size is measured once, so later list changes cannot alter that field.
Field values are retained by reference; use measured scalars or immutable values to
preserve their contents. A condition-valued field prints its description, operands,
and reason. Core supplies the layout, stop reason, attempt number, and timing.

## Execution failures

Null sources or conditions and invalid eager arguments fail before polling.
Any throwable escaping a source, run factory, evaluator factory, probe, or history
callback stops the wait. Existing execution failures remain primary.

An existing interrupt flag survives run preparation, history callbacks, and formatting;
it prevents further polls and nested checks.
Existing condition causes take precedence over a generated interruption cause.
A thrown `InterruptedException` with a cleared flag is an ordinary failure; its type
does not restore the flag.

## Attempts

Attempts store complete source observations. To record derived values, project in the source.

The sealed `Attempt<OBSERVED>` has four immutable records:

| Variant | Fields | Meaning |
| --- | --- | --- |
| `SatisfiedObservation` | `observed`, `diagnostic` | This check passed |
| `UnsatisfiedObservation` | `observed`, `diagnostic`, `cause` | Mismatch; retry allowed even with a cause |
| `FailedAfterObservation` | `observed`, `operation`, `cause`, `location`, `diagnostic` | Observation obtained, then execution failed |
| `FailedBeforeObservation` | `operation`, `cause` | Observation unavailable |

The three variants with an observation share `Attempt.Observation<OBSERVED>` and its
`observed()` accessor, including null. Values preceding a check failure or interruption are retained.
Every variant has `cause()` (null if absent), `diagnostic()` (empty if absent), and
`operation()` (null unless execution failed).
`UnsatisfiedObservation.mismatch()` reads the diagnostic message. Failure variants share
`Attempt.Failure<OBSERVED>`: non-null cause, operation `SOURCE`/`CONDITION`/`WAITING`,
and optional nested `location()`. Record patterns preserve types without casts.

## Histories

`result.conditions()` is immutable. Ordinary conditions have one history; custom
lifecycles define scopes and order. Unreached scopes have empty maps.
If user history declarations fail, collected observations remain in one history under
the original condition. `Last attempt #N` explains a terminal failure outside the retained groups.
Each `ConditionHistory<OBSERVED>` exposes:

| Access | Value |
| --- | --- |
| `condition()` | Original check |
| `attempts()` | Immutable `NavigableMap<Long, Attempt<OBSERVED>>` |

Consecutive satisfied or unsatisfied attempts share an entry when observations and
diagnostic field values match under `Objects.deepEquals`. Status, mismatch text,
field labels/order, and exception instance must also match.
Equal exception text is insufficient. Execution failures remain separate.
Entries retain the latest observation. Returning to an earlier value starts a new
entry; phase changes alone do not.

The key is the **inclusive last attempt number**, numbered across the whole wait:

```text
key → observed
  4 → 0          attempts 1–4
  6 → 10         attempts 5–6
 12 → 20         attempts 7–12
 24 → 30         attempts 13–24
 30 → 40         attempts 25–30
```

For a single history, ranges start at 1 and then after the preceding key.
`attempts().size()` counts retained states; `lastEntry().getValue()` gives the last state of a nonempty map.

Grouping neither trims history nor skips checks. It calls no `hashCode` or `toString`
and reads no exception messages. Equality should be side-effect free; if it throws,
observations stay separate. Memory is unbounded in both entry points.

Mutable observations can lose earlier contents; later mutations affect old entries.
Measured values explicitly returned in a diagnostic remain available through
`attempt.diagnostic().fields()`. Statuses and mismatch strings stay fixed.
History has no timing or progress objects.

## Reading diagnostics

```java
out.println(result);                       // Outcome and histories
result.conditions().forEach(out::println); // Histories only
```

Histories print the definition, operands, and `Because` reason once, followed by
`Through #N: satisfied`, `unsatisfied`, or `failed (...)`. Unreached conditions show
`Result: not attempted`; attempts print observation/status details. Definitions expose
`description()` and immutable `references()` with a label and value per reference.

`Observed: unavailable` differs from `Observed now: null`. Text is quoted and control
characters escaped; other objects supply their representation. Cause type/message
share a field; times use exact units such as `1 s 20 ms`. Rendering failures produce
`<unprintable>`; secondary errors appear as `Suppressed` without replacing the primary cause.

A failed result and its exception share text fixed at completion. Printing either
does not render values again; printing histories separately shows their current values.
