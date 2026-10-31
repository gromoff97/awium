# Development

[README](../README.md)

## Build and checks

Run from `awium-conditions`:

```sh
./gradlew clean build
```

The dependency is `io.github.gromoff97:awium-core:0.1.0-SNAPSHOT`.
When the sibling `awium-core` directory exists, `settings.gradle.kts` substitutes
its independent build. Core's tests are not part of this build.
To use core sources elsewhere:

```sh
./gradlew --include-build /path/to/awium-core build
```

There is no parent Gradle project or shared build configuration. The core snapshot
is not published; local development needs its sources. Tests and their helpers
live in this project's `src/test`. They use only core's public API, with no dependency
on its test code. Conditions are probed directly; `captured` stages use the public
run contract. Fake clocks and engine timing tests belong to core.

Checks cover condition behavior, fluent type inference, incompatible-type rejection,
and consumers of the two JARs. Architecture tests reject access to core internals
and shared production packages. The only product dependency
allowed is core. Tests use JUnit 6, AssertJ, Mockito, Commons Lang, compile-testing,
and ArchUnit; `slf4j-jdk14` handles test logging. Integration tests cover nesting,
stage transitions, diagnostics, and concurrent reuse of `captured`.

PIT uses `ALL`: run `./gradlew pitest` **only on explicit user request**.
It is separate from `build`, `check`, and `test`.

## Responsibilities

| Component | Owns |
| --- | --- |
| `Conditions`, domain catalogs | Public factories; no checking algorithms |
| `conditions.internal.*Condition` | Algorithms, operands, mismatch descriptions, and composition |
| `CapturedCondition.Run` | Stage transitions, collected observations, and stage diagnostics |

Implementations use core's public extension contracts, never its engine internals.
Each run has separate mutable state; reusable definitions stay immutable.
The Java module `io.github.gromoff97.awium.conditions` exports only
`io.github.gromoff97.awium.conditions` and requires core transitively.
Concrete helpers in `.conditions.internal` are unexported.
