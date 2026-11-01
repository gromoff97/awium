# Awium Conditions

Ready-made conditions for Awium Core: values, text,
optionals, collections, maps, and ordered states. Requires **Java 21**; its only
product dependency is `awium-core`.

```java
Payment payment = await(paymentRepository::find)
        .upTo(ofSeconds(5))
        .until(present);
```

`find()` returns `Optional<Payment>`; `until(present)` returns its `Payment`.
`upTo` limits the wait; `every` sets the polling interval. Callbacks run on the
calling thread, so a blocked callback cannot be forcibly stopped.

Use `tryAwait(source).until(condition)` to get an outcome with attempt history.
`result.orThrow()` returns the selected value or throws the wait failure;
`out.println(result)` shows the outcome and histories. Reading a result never
repeats the checks.

## Installation

The snapshot is unpublished. Clone Awium beside your Gradle project and add to `settings.gradle.kts`:

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

- [Conditions](docs/conditions.md): checks, `because`, nested conditions, and `captured`.
- [Development](docs/development.md): build, tests, and internal responsibilities.

Factories are grouped by family under `io.github.gromoff97.awium.conditions`.

Licensed under [Apache License 2.0](LICENSE).
