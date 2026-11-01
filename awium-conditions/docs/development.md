# Development

[README](../README.md)

## Build and checks

From `awium-conditions`, build and measure ordinary test coverage:

```sh
./gradlew clean build jacocoTestReport
```

JaCoCo writes HTML and XML to `build/reports/jacoco/test`.

`settings.gradle.kts` substitutes the sibling `awium-core` build when present,
without running its tests. For core sources elsewhere:

```sh
./gradlew --include-build /path/to/awium-core build
```

Each project has its own Gradle build. The unpublished core snapshot requires local
sources. Tests and helpers stay in this project's `src/test` and use only core's
public API. Probe conditions directly and `captured` through its public run contract;
fake clocks and engine timing tests belong to core.

Checks cover condition behavior, Java fluent inference, incompatible types, both JARs,
nesting, stage transitions, diagnostics, and concurrent `captured` reuse. Architecture
checks reject core internals, shared product packages, and dependencies other than core.
Tests use Kotlin, JUnit 6, AssertJ, Mockito, compile-testing, and ArchUnit;
`slf4j-jdk14` supplies test logging. Plain Java consumers preserve Java inference checks.
Kotlin and its standard library are test-only.

PIT uses `ALL`: run `./gradlew pitest` **only on explicit user request**.
It is separate from `build`, `check`, and `test`.

## Responsibilities

| Component | Owns |
| --- | --- |
| `*Conditions` | Public factories |
| Package-private neighbors | Checks, metadata, and lifecycle |
| `CapturedCondition.Run` | Stage transitions, collected observations, and stage diagnostics |

Mutable state belongs to each run; reusable definitions stay immutable.
Factories live in `conditions.common`, `conditions.collection`, `conditions.map`,
`conditions.optional`, and `conditions.string`. The Java module exports these
packages and `io.github.gromoff97.awium.function.checked` for checked callbacks,
requiring core transitively. Each family exposes only its factory, which returns core contracts. Implementations provide their own `description`, `references`,
and `probe` methods. Cross-family reuse goes through public factories: size checks use
numeric conditions, and map checks use collection conditions on map views. Helpers
remain package-private alongside their callers.
