# Awium

Awium waits for a condition and returns a value from the successful observation.
It requires **Java 21**, has **no runtime dependencies**, and runs on the calling thread.

```java
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.OptionalConditions.present;
import static java.time.Duration.ofSeconds;

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
includeBuild("../awium")
```

Add to `build.gradle.kts`:

```kotlin
dependencies {
    testImplementation("io.github.gromoff97:awium:0.1.0-SNAPSHOT")
}
```

## Documentation

Guide examples omit ordinary and static imports.

- [Usage](docs/usage.md): sources and timing.
- [Conditions](docs/conditions.md): checks, `because`, nested conditions, and `captured`.
- [Diagnostics](docs/diagnostics.md): `tryAwait`, results, histories, and failures.
- [Development](docs/development.md): build, tests, and internal responsibilities.

Public entry points and conditions are in `io.github.gromoff97.awium`;
results are in `.result`, and checked callbacks in `.function`.

Licensed under [Apache License 2.0](LICENSE).
