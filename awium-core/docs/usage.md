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

Pass a lambda or method reference; direct values are not accepted. Sources may
throw checked exceptions. Both wait modes accept the same source types and timing settings.

A stored JDK `Callable` selects the general value overload. Pass it as a method
reference to select the container overload:

```java
Callable<Optional<Payment>> source = paymentRepository::find;
var optionalWait = await(source::call);
```

To pass a stored source directly, use one of `Await`'s source types:
`OptionalSource<VALUE>`, `CollectionSource<VALUES>`, or `MapSource<ENTRIES>`.
All extend `Callable`.

### Inference limits

| Source | Remedy |
| --- | --- |
| Ambiguous reference, e.g. `StringBuilder::new` or `Optional::empty` | Use a lambda: `await(() -> new StringBuilder())`. |
| Only returns null | Specify a category: `OptionalSource<Payment> source = () -> null`. |
| Wildcard method reference selects the general overload | Use `await(() -> repository.findAllView())` or a typed view. |

`CollectionViewSource<ELEMENT, VALUES>` and `MapViewSource<KEY, VALUE, ENTRIES>`
declare container types and selected bounds separately.

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
