# Awium Core

Java 21 source polling, condition contracts, results, and histories. Depends only on the JDK.

## Installation

The snapshot is unpublished. Clone Awium beside your Gradle project and add to `settings.gradle.kts`:

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

Choose another `RESULT` to extract a value. `description()` labels diagnostics
and prevents ambiguity with predicate lambdas.
Keep definitions immutable; [custom lifecycles](docs/development.md) support per-use state.

## Check outcomes

`ConditionResult<RESULT>` has two variants, created with:

| Factory | Meaning |
| --- | --- |
| `satisfied(value)` | Chosen result, including null; pass `actual` to return the observation |
| `unsatisfied()`, `unsatisfied(message)`, `unsatisfied(message, cause)` | Mismatch with optional diagnostics |

The declared result sets the terminal type: `ConditionResult<CharSequence>` yields
`CharSequence` even for a `String` source. `mapValue(function)` transforms success
values and preserves mismatches.

## Reasons

`because(nonBlankReason)` copies the condition, replacing any previous or inherited
reason. Keep its specific type: `AwaitCondition` alone
lacks the constraints required by `until`.

## Documentation

- [Usage](docs/usage.md): sources and timing.
- [Diagnostics](docs/diagnostics.md): results, histories, and failures.
- [Development](docs/development.md): build and extension architecture.

Entry points and contracts are in `io.github.gromoff97.awium`, results in `.result`,
`AwaitAttempt` in `.function`, and lifecycle support in `.execution`.
Ready-made checks belong to the separate [Awium Conditions](../awium-conditions/README.md) project.

Licensed under [Apache License 2.0](LICENSE).
