# Diagnostics

[README](../README.md) · [Usage](usage.md) · [Conditions](conditions.md)

## Results

`tryAwait(source).until(condition)` returns `AwaitResult<OBSERVED, RESULT>`.
`OBSERVED` is the complete source value; `RESULT` is the condition's result:

```java
var result = tryAwait(paymentRepository::find).until(present);
// AwaitResult<Optional<Payment>, Payment>

switch (result) {
    case AwaitResult.Satisfied(var conditions, var payment) ->
            System.out.println(payment.receipt());
    case AwaitResult.Failed(var conditions, var failure) ->
            System.out.println(failure.message());
}
```

| Access | Meaning |
| --- | --- |
| `result.orThrow()` | Selected value, including null, or the same failure as ordinary `await` |
| `result.conditions()` | Immutable histories in condition declaration order |
| `Satisfied.result()` | Successful result |
| `Failed.failure()` | `AwaitFailure`: `reason()`, `message()`, original `cause()`, immutable `suppressed()` |

`orThrow()` does not rerun the source or check. For [captured states](usage.md#ordered-states),
its result is the list of source observations. `tryAwait` constructs no wrapper
exception until a failed result's `orThrow()` is called.

## Histories

Each `ConditionHistory<OBSERVED>` has two fields:

- `condition()`: the original check for this position.
- `attempts()`: an immutable `NavigableMap<Long, Attempt<OBSERVED>>`.

One ordinary condition has one history; three captured stages have three.
Unreached stages have empty maps. Reusing a condition at multiple positions still
produces separate histories.

Each map key is the **inclusive last attempt number**, counted across the whole wait.
Consecutive equal observations with unchanged status, mismatch, and exception
instance share an entry, retaining the latest observation:

```text
key → observed
  4 → 0          attempts 1–4
  6 → 10         attempts 5–6
 12 → 20         attempts 7–12
 24 → 30         attempts 13–24
 30 → 40         attempts 25–30
```

A range starts after the preceding key, or after the previous condition's last
attempt (1 for the first condition). `attempts().size()` counts retained states,
not polling calls. For a nonempty map, `lastEntry().getValue()` gives the latest state.

A captured sequence can therefore look like this:

```text
captured(equalTo("created"), equalTo("paid"))
conditions[0]: 1 → Satisfied("created")
conditions[1]: 3 → Unsatisfied("pending", ...)
               4 → Satisfied("paid")
orThrow(): ["created", "paid"]
```

`persisting` extends the final condition's history. Changing phase alone does not
start a new entry. History has no timing or progress objects; the terminal failure
message contains elapsed time, phase, stage number, and sequence reason.
A timeout after an intermediate success identifies the next stage separately.

## Attempts

`Attempt<OBSERVED>` has four immutable variants:

| Variant | Fields | Meaning |
| --- | --- | --- |
| `Satisfied` | `observed` | Check succeeded; later captured stages may still be pending |
| `Unsatisfied` | `observed`, `mismatch`, `cause` | Check rejected the value; polling may continue |
| `FailedAfterObservation` | `observed`, `operation`, `cause`, `location` | Observation was obtained, then execution failed |
| `FailedBeforeObservation` | `operation`, `cause` | No observation was obtained |

`observed` may legitimately be null; a failure before observation has no such
accessor. Every attempt exposes `cause()`, null when absent. Both failure variants
implement `Attempt.Failure<OBSERVED>`: the cause is non-null, operation is `SOURCE`,
`CONDITION`, or `WAITING`, and `location()` optionally identifies nested work.
Java record patterns retain observation types without casts.

Attempts store source observations, not extracted results. A returned mismatch
can carry a cause and still allow retries. An escaping throwable ends the wait;
a source returning null followed by a thrown check is `FailedAfterObservation(null, ...)`.
An interrupt after source retrieval also has an observation, even if the check never ran.

## What history retains

- History groups repetitions without trimming earlier states. 999 identical
  mismatches followed by success produce keys 999 and 1000; the check still ran
  1000 times. Every changed value, diagnostic transition, or new exception instance
  starts an entry. Returning to an earlier value creates a new chronological entry.
- Observations use `Objects.deepEquals`: object equality or array contents. Equality
  should be side-effect free. History never calls `hashCode`, `toString`, or exception
  messages for grouping. If equality throws, states stay separate and the wait outcome
  is unchanged. Repeated references to the same exception may share an entry.
- Containers of history records are immutable; observations retain caller-owned
  references. Awium does not copy or freeze objects. Reusing one mutable object loses
  its earlier contents; later changes are visible in earlier entries. `matches`
  cannot record which fields its predicate read. Status and mismatch strings stay fixed.
- Memory grows with changes and distinct errors; there is no fixed limit.
  [Ordinary `await`](usage.md#the-chain) does not retain or compare attempt records.

## Reading diagnostics

```java
System.out.println(result);                         // Outcome and all histories
result.conditions().forEach(System.out::println);   // Histories only
```

Each history prints its condition, operands, and reason once, followed by
`Through #N: satisfied`, `unsatisfied`, or `failed (...)`. Unreached conditions show
`Result: not attempted`. An individual attempt prints only its observation and status.

Histories record polling attempts, not each candidate inside a quantifier.
Nested mismatches retain completed selection steps; `(checked N)` is the number
of evaluated candidates in iteration order, not attempts or necessarily container size.
Nested errors include paths such as `Within: optional value → collection elements[1]`.
Candidate indices are zero-based; attempt numbers start at 1.
Built-ins retain measured facts such as size, length, actual class, duplicate index,
out-of-order indices, or the first missing/forbidden operand index. These facts
survive later observation changes without re-running the check.

`Observed: unavailable` differs from `Observed now: null`. Text is quoted and
control characters escaped; objects and containers use their own representation.
Cause type and message share one field; times use exact units such as `1 s 20 ms`.
Failed renderers yield `<unprintable>` and a `Rendering failure` line, preserving
other details. Original exceptions and stack traces remain available via `cause()`.
Terminal failure text is saved at completion; histories and condition operands
are rendered on demand from their current contents.

## Failures and interruption

Ordinary `await` throws a wrapper exposing `failure()`; `tryAwait` returns the same
structured failure:

| Wrapper | `failure.reason()` |
| --- | --- |
| `AwaitAssertionError extends AssertionError` | `TIMEOUT`, `PERSISTENCE_FAILED` |
| `AwaitExecutionException extends RuntimeException` | `SOURCE_FAILED`, `CONDITION_FAILED`, `INTERRUPTED`, `WAIT_FAILED` |

The message and original cause come from `AwaitFailure`. Distinct rendering failures
are retained by identity in `suppressed()` and on the thrown wrapper's `getSuppressed()`;
the original throwable is never modified to attach diagnostics.

Null sources/conditions and invalid eager arguments fail before polling. Every
throwable escaping a source, check, callback factory, or parker stops the wait.
Only [`asserted`](conditions.md#callbacks) converts an `AssertionError` into a retry.
Custom clock failures propagate directly.

An interrupt flag set before or during execution stops polling, including before
the next nested candidate. The flag is preserved through clock reads and diagnostic
formatting. An existing condition cause is retained; a service interruption cause
is created only when none exists. A callback throwing `InterruptedException` with
an already-cleared flag is an ordinary thrown failure: its type does not restore
the flag. Callbacks execute on the caller; interruption cannot force a blocked
callback to return.
