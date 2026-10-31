# Diagnostics

[README](../README.md) · [Usage](usage.md)

## Results

`tryAwait(source).until(condition)` returns `AwaitResult<OBSERVED, RESULT>`:
source type first, condition result type second. Here `receipt` is a
`Condition<Payment, String>`.

```java
var result = tryAwait(paymentRepository::load).until(receipt);
// AwaitResult<Payment, String>
out.println(switch (result) {
    case Succeeded(var conditions, var value) -> value;
    case Failed(var conditions, var failure) -> failure.message();
});
```

Records expose `Succeeded.result()` or `Failed.failure()`. `AwaitFailure` has
`reason()`, `message()`, original `cause()`, and immutable `suppressed()` causes.
On success, `result.orThrow()` returns the selected value, including null.
Reading a result never reruns checks. A failed result creates its wrapper exception
only when `orThrow()` is called.

On failure, ordinary `await` and `orThrow()` throw a wrapper exposing `failure()`:

| Wrapper | Reasons |
| --- | --- |
| `AwaitAssertionError extends AssertionError` | `TIMEOUT`, `PERSISTENCE_FAILED` |
| `AwaitExecutionException extends RuntimeException` | `SOURCE_FAILED`, `CONDITION_FAILED`, `INTERRUPTED`, `WAIT_FAILED` |

Wrapper message/cause come from `AwaitFailure`. Distinct rendering failures are
retained by identity in `suppressed()` and wrapper `getSuppressed()`; the original
throwable is never modified.

## Execution failures

Null sources or conditions and invalid eager arguments fail before polling.
Any throwable escaping a source, check, or factory stops the wait.

An interrupt flag stops polling, including before the next nested check, and
survives diagnostic formatting. Existing condition causes are kept;
a service interruption cause is created only when absent. A thrown
`InterruptedException` with an already-cleared flag is an ordinary failure:
its type does not restore the flag. A blocked callback cannot be forcibly stopped.

## Attempts

Attempts store complete source observations, not selected results. Project in the
source if history should observe a derived value instead.

The sealed `Attempt<OBSERVED>` has four immutable records:

| Variant | Fields | Meaning |
| --- | --- | --- |
| `SatisfiedObservation` | `observed` | This check passed |
| `UnsatisfiedObservation` | `observed`, `mismatch`, `cause` | Mismatch; retry allowed even with a cause |
| `FailedAfterObservation` | `observed`, `operation`, `cause`, `location` | Observation obtained, then execution failed |
| `FailedBeforeObservation` | `operation`, `cause` | Observation unavailable |

An observed null is still a value. A later check failure or interruption retains
that observation. Every variant exposes `cause()` (null if absent).
Failure variants share `Attempt.Failure<OBSERVED>`: non-null cause, operation
`SOURCE`/`CONDITION`/`WAITING`, optional nested `location()`. Record patterns retain
types without casts.

## Histories

`result.conditions()` is an immutable list. An ordinary condition has one history;
custom lifecycles define their history scopes and order. Unreached scopes have
empty maps. Each `ConditionHistory<OBSERVED>` exposes:

| Access | Value |
| --- | --- |
| `condition()` | Original check |
| `attempts()` | Immutable `NavigableMap<Long, Attempt<OBSERVED>>` |

Consecutive check outcomes share one entry when observations are equal under
`Objects.deepEquals` and status, mismatch, and exception instance stay unchanged
(identical exception text is insufficient). Each entry retains the latest observation.
Returning to an earlier value starts another entry; phase changes alone do not.

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
Equality should be side-effect free; grouping never calls `hashCode`, `toString`,
or exception messages. Failed equality keeps observations separate without changing
the outcome. Memory has no fixed bound. Ordinary `await` neither retains nor compares attempts.

Reusing one mutable object loses earlier contents; later mutations affect old
entries. Fields read only inside a check are not independently recorded. Recorded statuses
and mismatch strings stay fixed.
History contains no timing or progress objects.

## Reading diagnostics

```java
out.println(result);                       // Outcome and histories
result.conditions().forEach(out::println); // Histories only
```

Each history prints its definition, operands, and `Because` reason once, then
`Through #N: satisfied`, `unsatisfied`, or `failed (...)`. Unreached conditions show
`Result: not attempted`; individual attempts print only observation/status details.
Definitions expose `description()` and immutable `references()`; each reference
supplies its own label and value.

`Observed: unavailable` differs from `Observed now: null`. Text is quoted with
escaped control characters; other objects use their own representation. Cause
type/message share one field; time uses exact units such as `1 s 20 ms`.
If rendering fails, the output uses `<unprintable>` and `Rendering failure`
without hiding other fields.

Terminal failure text is saved at completion with elapsed time and phase. A custom
lifecycle can add its own context. Histories and operands are rendered on demand
from current contents.
