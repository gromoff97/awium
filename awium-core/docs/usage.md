# Usage

[README](../README.md) · [Diagnostics](diagnostics.md)

## The chain

```java
Payment payment = await(paymentRepository::load)
        .every(ofMillis(100)).upTo(ofSeconds(5))
        .until(paid);
```

`paid` has type `Condition<Payment, Payment>`. `until` accepts one condition
and starts polling until it succeeds.
Each attempt reads the source once; returning the result needs no extra read.

Configurations and conditions are immutable and reusable. Each terminal call
starts fresh timing and condition execution state.

Execution stays on the calling platform or virtual thread, with its `ThreadLocal`
values. Awium creates no worker. Callers manage synchronization of shared mutable
values.

## Sources

Pass a lambda or method reference; direct values are not accepted. Sources may
throw checked exceptions. Both wait modes accept the same source types and timing settings.

Awium keeps source values by reference, without copying or freezing them. Return
copies from the source when you need independent snapshots.

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
declare the complete container type and selected bound without copying data.
The [condition guide](../../awium-conditions/docs/conditions.md) shows concrete selectors.

## Timing

| Setting | Default | Duration |
| --- | --- | --- |
| `every(interval)` | 100 ms | Positive |
| `upTo(timeout)` | 10 s | Positive |
| `persisting(duration)` | Zero: disabled | Non-negative |

Settings accept `Duration` in any order; the last value wins. Awium validates durations immediately.

- Unless interrupted, the first attempt starts immediately. The timeout cannot
  cancel it. Later attempts must start and finish before the acquisition deadline;
  late results remain in diagnostics but cannot satisfy the wait.
- Intervals may exceed the timeout; parking is capped at the remaining acquisition time.
- After success, `persisting` keeps checking for its duration, possibly beyond `upTo`.
  Any persistence mismatch fails immediately.
