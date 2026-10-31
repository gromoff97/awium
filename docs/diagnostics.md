# Diagnostics

[README](../README.md) · [Usage](usage.md) · [Conditions](conditions.md)

## Results

`tryAwait(source).until(condition)` returns `AwaitResult<OBSERVED, RESULT>`:
source type first, condition result type second.

```java
var result = tryAwait(paymentRepository::find).until(present);
// AwaitResult<Optional<Payment>, Payment>
out.println(switch (result) {
    case Satisfied(var conditions, var payment) -> payment.receipt();
    case Failed(var conditions, var failure) -> failure.message();
});
```

Records expose `Satisfied.result()` or `Failed.failure()`. `AwaitFailure` has
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

An interrupt flag stops polling, including before the next nested candidate, and
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
| `Satisfied` | `observed` | This check passed |
| `Unsatisfied` | `observed`, `mismatch`, `cause` | Mismatch; retry allowed even with a cause |
| `FailedAfterObservation` | `observed`, `operation`, `cause`, `location` | Observation obtained, then execution failed |
| `FailedBeforeObservation` | `operation`, `cause` | Observation unavailable |

An observed null is still a value. A later check failure or interruption retains
that observation. Every variant exposes `cause()` (null if absent).
Failure variants share `Attempt.Failure<OBSERVED>`: non-null cause, operation
`SOURCE`/`CONDITION`/`WAITING`, optional nested `location()`. Record patterns retain
types without casts. A successful stage may precede later sequence failure.

## Histories

`result.conditions()` is an immutable list in declaration order: one history per
check position, even when the same condition is reused. Unreached positions have
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

Each range starts after the preceding key, or after the previous condition's
last attempt (1 for the first condition). `attempts().size()` counts retained
states; `lastEntry().getValue()` gives the last state of a nonempty map.

For `captured(equalTo("created"), equalTo("paid"))`:

```text
conditions[0]: 1 → Satisfied("created")
conditions[1]: 3 → Unsatisfied("pending", ...)
               4 → Satisfied("paid")
```

Grouping preserves chronological changes without trimming or skipping checks.
Equality should be side-effect free; grouping never calls `hashCode`, `toString`,
or exception messages. Failed equality keeps observations separate without changing
the outcome. Memory has no fixed bound. Ordinary `await` neither retains nor compares attempts.

Reusing one mutable object loses earlier contents; later mutations affect old
entries. `matches` cannot record fields read by its predicate. Recorded statuses
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
Definitions expose `description()` and immutable `references()`; compositions keep
lookup keys, expected operands, and nested names. Operands are labelled `Expected`
or `Unexpected`.

Quantifier candidates are not separate attempts. `(checked N)` counts evaluated
candidates in iteration order, not polls or necessarily container size. Nested
mismatches retain completed selections; nested errors show paths such as
`Within: optional value → collection elements[1]`. Candidate indices start at 0.
Built-ins retain measured size, length, class, duplicate or out-of-order indices,
and missing/forbidden operand indices despite later mutations.

`Observed: unavailable` differs from `Observed now: null`. Text is quoted with
escaped control characters; other objects use their own representation. Cause
type/message share one field; time uses exact units such as `1 s 20 ms`.
If rendering fails, the output uses `<unprintable>` and `Rendering failure`
without hiding other fields.

Terminal failure text is saved at completion, with elapsed time, phase, stage
number, and sequence reason. Timeout after an intermediate success identifies
the next stage. Histories and operands are rendered on demand from current contents.
