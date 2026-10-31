# Awium Core

The Java 21 wait engine: source polling, condition contracts, results, and histories.
Execution stays on the calling thread. Product dependencies: only the JDK.

## Installation

The snapshot is not published. With the repository cloned next to your Gradle project,
add to `settings.gradle.kts`:

```kotlin
includeBuild("../awium/awium-core")
```

Add to `build.gradle.kts`:

```kotlin
dependencies {
    testImplementation("io.github.gromoff97:awium-core:0.1.0-SNAPSHOT")
}
```

## Implementing a condition

Implement the open `Condition<INPUT, RESULT>` interface with two methods:

```java
final class PaidPayment implements Condition<Payment, Payment> {
    @Override
    public String description() { return "payment is paid"; }

    @Override
    public ConditionResult<Payment> probe(Payment actual) {
        return actual.paid() ? satisfied(actual) : unsatisfied("payment was unpaid");
    }
}

Payment payment = await(paymentRepository::load)
        .until(new PaidPayment().because("Checkout must complete"));
```

Choose a different `RESULT` to extract a value. `description()` names the check
in diagnostics and keeps `Condition` distinct from predicate lambdas during overload resolution.

Keep the definition immutable. Override `newAttempt()` to create an independent,
stateful evaluator on first evaluation of each use. For a custom lifecycle, override
`newRun(definition, recording)` and return a fresh `execution.AbstractConditionRun`:
`probe` checks each observation, `complete` decides completion, and `result` returns
the final value. The supplied definition carries any `because` reason. The base class
records history and assembles the result; protected hooks customize history scopes
and diagnostics. Nested uses wait for completion and return that run's final value.
Polling, timeout, and persistence remain the engine's responsibility.

## Check outcomes

`ConditionResult<RESULT>` supplies these static factories:

| Factory | Meaning |
| --- | --- |
| `satisfied(value)` | Chosen result, including null; pass `actual` to return the observation |
| `unsatisfied()`, `unsatisfied(message)`, `unsatisfied(message, cause)` | Mismatch with optional diagnostics |

There is no no-argument success or user-returnable uncontrolled state. The declared
result determines the terminal type: `ConditionResult<CharSequence>`
yields `CharSequence` even with a `String` source.
`mapValue(function)` transforms only a satisfied value; mismatches retain their diagnostics.

## Reasons

`because(nonBlankReason)` returns an immutable copy whose reason appears in diagnostics.
Calling it again replaces the reason; an outer reason overrides an inherited nested reason.
Keep a condition's specific type: `AwaitCondition` alone does not retain the constraints
needed by `until`.

## Documentation

- [Usage](docs/usage.md): sources and timing.
- [Diagnostics](docs/diagnostics.md): results, histories, and failures.
- [Development](docs/development.md): build and extension architecture.

Entry points and contracts are in `io.github.gromoff97.awium`, results in `.result`,
checked callbacks in `.function`, and lifecycle support in `.execution`.
Ready-made checks belong to the separate [Awium Conditions](../awium-conditions/README.md) project.

Licensed under [Apache License 2.0](LICENSE).
