# Usage

[README](../README.md) · [Diagnostics](diagnostics.md)

## The chain

```java
Payment payment = await(paymentRepository::load)
        .every(ofMillis(100)).upTo(ofSeconds(5))
        .until(paid);
```

`paid` is a `Condition<Payment, Payment>`. `until` polls one condition, reading the
source at most once per attempt and returning the result without another read.

Immutable configurations are reusable. Each terminal call starts
fresh timing and execution state.

Execution stays on the calling platform or virtual thread with its `ThreadLocal`
values. Callers synchronize shared mutable values; Awium creates no worker.

## Sources

Both entry points accept one `Callable<? extends OBSERVED>`: a stored callable,
lambda, or method reference. Sources may throw checked exceptions.

```java
Callable<Payment> source = paymentRepository::load;
Payment payment = await(source).until(paid);
```

Core treats values and containers alike: the condition declares its input and
result. `await` returns that result; `tryAwait` returns `AwaitResult<OBSERVED, RESULT>`.

## Timing

| Setting | Default | Duration |
| --- | --- | --- |
| `every(interval)` | 100 ms | Positive |
| `upTo(timeout)` | 10 s | Positive |
| `persisting(duration)` | Zero: disabled | Non-negative |

Settings accept `Duration` in any order, validate immediately, and use the last value.

- The timeout includes run creation. Unless interrupted, the first attempt starts
  immediately. Callbacks cannot be cancelled; acquisition attempts must finish before the deadline.
  Late results remain in diagnostics, and no further attempts start.
- Intervals may exceed the timeout; parking is capped at the remaining acquisition time.
- After success, `persisting` keeps checking for its duration, possibly beyond `upTo`.
  Any persistence mismatch fails immediately.

## Handling a wait

Both entry points accept `handling(factory)`. The factory creates an
`AwaitHandler<OBSERVED>` for each terminal call; timing methods retain it.

```java
final class Trace<OBSERVED> extends DefaultAwaitHandler<OBSERVED> {
    @Override
    public void onAttemptFinish(AttemptFinished<OBSERVED> event) {
        out.println(event.number() + ": " + event.attempt());
        super.onAttemptFinish(event);
    }
}

var result = tryAwait(paymentRepository::load)
        .handling(Trace::new)
        .until(paid);
```

`AwaitHandler` and `DefaultAwaitHandler` are in `.execution`; event records are
nested in `AwaitHandler`. Callbacks run on the caller thread:

| Method | Input |
| --- | --- |
| `onStart` | Root condition, interval, timeout, and persistence duration; before run creation |
| `onAttemptStart` | Attempt number and elapsed time; before reading the source |
| `onAttemptFinish` | Number, completion time, root attempt, history slot, and history attempt |
| `onFinish` | Elapsed time, optional failure reason, and optional declared history conditions |

Each started attempt has one finish event, including source and probe failures.
Failures during initialization or parking produce a diagnostic attempt with the
same pair of events. Times are relative to the start of the wait. Time in user
start and attempt callbacks counts towards waiting deadlines; callbacks cannot be cancelled.

`event.attempt()` describes the root check. `historyAttempt()` and `historySlot()`
carry the report supplied by that condition; both attempts are the same object
when no projection is needed. Nested checks produce no additional callbacks.

`onFinish` returns the histories included in `AwaitResult` and failure diagnostics;
it cannot change the check verdict or selected value. Its empty failure reason
means success. Absent history declarations mean the condition failed to provide
them; an available empty list means it deliberately declared no histories.
Result assembly and failure rendering follow this callback.

`DefaultAwaitHandler` groups equal observations and diagnostics, retaining each
group's last attempt number. Extend it to add logging, or implement the interface
to choose another storage policy. `onStart` and `onAttemptStart` default to no-ops.
Keep state inside the factory-created handler; observations remain references.

Handler failures stop the wait as `WAIT_FAILED` and preserve any earlier execution
failure as the primary cause. If a custom handler cannot return valid histories,
core falls back to the last reported attempt. Its `onFinish` is not retried.
