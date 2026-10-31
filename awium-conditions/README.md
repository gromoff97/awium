# Awium Conditions

Ready-made conditions for [Awium Core](../awium-core/README.md): values, text,
optionals, collections, maps, and ordered states. Requires **Java 21**; its only
product dependency is `awium-core`.

```java
Payment payment = await(paymentRepository::find)
        .upTo(ofSeconds(5))
        .until(present);
```

`find()` returns `Optional<Payment>`; `until(present)` returns its `Payment`.
Use `tryAwait` for an outcome with attempt history.

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

- [Core usage](../awium-core/docs/usage.md): sources and timing.
- [Conditions](docs/conditions.md): checks, `because`, nested conditions, and `captured`.
- [Core diagnostics](../awium-core/docs/diagnostics.md): results, histories, and failures.
- [Development](docs/development.md): build, tests, and internal responsibilities.

Factories are in `io.github.gromoff97.awium.conditions`.

Licensed under [Apache License 2.0](LICENSE).
