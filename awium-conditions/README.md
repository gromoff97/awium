# Awium Conditions

Ready-made conditions for [Awium Core](../awium-core/README.md): values, text,
optionals, collections, maps, and ordered states. Requires **Java 21**; its only
product dependency is `awium-core`.

```java
Payment payment = await(paymentRepository::find)
        .upTo(ofSeconds(5))
        .until(present);
```

Here `find()` returns `Optional<Payment>`; `until(present)` returns its `Payment`.
An unsuccessful check is retried until the timeout. Use `tryAwait` to receive
the outcome and attempt history instead of throwing a wait failure.

## Installation

The snapshot is not published. Clone Awium next to your Gradle project
and add to `settings.gradle.kts`:

```kotlin
includeBuild("../awium/awium-conditions")
```

Add to `build.gradle.kts`:

```kotlin
dependencies {
    testImplementation("io.github.gromoff97:awium-conditions:0.1.0-SNAPSHOT")
}
```

## Documentation

- [Core usage](../awium-core/docs/usage.md): sources and timing.
- [Conditions](docs/conditions.md): checks, `because`, nested conditions, and `captured`.
- [Core diagnostics](../awium-core/docs/diagnostics.md): results, histories, and failures.
- [Development](docs/development.md): build, tests, and internal responsibilities.

`settings.gradle.kts` uses the sibling `awium-core` build when present.
Factories are in `io.github.gromoff97.awium.conditions`.

Licensed under [Apache License 2.0](LICENSE).
