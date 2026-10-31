# Diagnostics

[README](../README.md) · [Usage](usage.md)

## Results

`tryAwait(source).until(condition)` returns `AwaitResult<OBSERVED, RESULT>` (source,
then result type). Here `receipt` is a `Condition<Payment, String>`.

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

Wrappers use `AwaitFailure`'s message and cause. Distinct rendering failures are
retained by identity in `suppressed()` and wrapper `getSuppressed()`; original
throwables remain unchanged.

## Execution failures

Null sources or conditions and invalid eager arguments fail before polling.
Any throwable escaping a source, check, or evaluator factory stops the wait.

An interrupt flag stops polling and further nested checks, and survives formatting.
Existing condition causes take precedence over a generated interruption cause.
A thrown `InterruptedException` with a cleared flag is an ordinary failure; its type
does not restore the flag. Blocked callbacks cannot be forcibly stopped.

## Attempts

Attempts store complete source observations. To record derived values, project in the source.

The sealed `Attempt<OBSERVED>` has four immutable records:

| Variant | Fields | Meaning |
| --- | --- | --- |
| `SatisfiedObservation` | `observed` | This check passed |
| `UnsatisfiedObservation` | `observed`, `mismatch`, `cause` | Mismatch; retry allowed even with a cause |
| `FailedAfterObservation` | `observed`, `operation`, `cause`, `location` | Observation obtained, then execution failed |
| `FailedBeforeObservation` | `operation`, `cause` | Observation unavailable |

Observed nulls and observations preceding a check failure or interruption are retained.
Every variant has `cause()` (null if absent). Failure variants share
`Attempt.Failure<OBSERVED>`: non-null cause, operation `SOURCE`/`CONDITION`/`WAITING`,
and optional nested `location()`. Record patterns preserve types without casts.

## Histories

`result.conditions()` is immutable. Ordinary conditions have one history; custom
lifecycles define scopes and order. Unreached scopes have empty maps.
Each `ConditionHistory<OBSERVED>` exposes:

| Access | Value |
| --- | --- |
| `condition()` | Original check |
| `attempts()` | Immutable `NavigableMap<Long, Attempt<OBSERVED>>` |

Consecutive outcomes share an entry when observations match under `Objects.deepEquals`
and status, mismatch, and exception instance stay unchanged. Equal exception text is insufficient.
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

Grouping preserves chronological changes without trimming or skipping checks.
Equality should be side-effect free. Grouping never calls `hashCode`, `toString`, or
exception messages; failed equality keeps observations separate without changing the outcome.
Memory is unbounded. Ordinary `await` neither retains nor compares attempts.

Mutable observations can lose earlier contents; later mutations affect old entries.
Fields read inside a check are not recorded separately. Statuses and mismatch strings
stay fixed. History has no timing or progress objects.

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
`<unprintable>` and `Rendering failure` while preserving other fields.

Terminal failure text saves elapsed time, phase, and custom lifecycle context at
completion. Histories and operands render their current contents on demand.
