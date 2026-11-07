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
checks reject core internals, packages shared with core, and dependencies other than core.
Tests use Kotlin, JUnit 6, AssertJ, Mockito, compile-testing, and ArchUnit;
`slf4j-jdk14` supplies test logging. Plain Java consumers preserve Java inference checks.
Kotlin and its standard library are test-only.

PIT uses `ALL`: run `./gradlew pitest` **only on explicit user request**.
It is separate from `build`, `check`, and `test`.

## Responsibilities

| Component | Owns |
| --- | --- |
| `*Conditions` | Factories returning fluent condition definitions |
| Package-private neighbors | Checks, metadata, and lifecycle |
| `CapturedCondition.Run` | Stage transitions, collected observations, and diagnostic data about the stages |
| `dsl.Await` and builders | Source-category inference; timing and execution inherited from core |
| `dsl.AwaitCondition` families | Source-dependent input and result constraints |
| `dsl.TypedCondition<INPUT, RESULT>` | A core condition that also composes with fluent definitions |

Mutable state belongs to each run; reusable definitions stay immutable.
Public factories live in `conditions.common`, `conditions.collection`, `conditions.map`,
`conditions.optional`, and `conditions.string`. The Java module exports these
packages, `io.github.gromoff97.awium.dsl`, and `.function.checked` for checked callbacks,
requiring core transitively. Each family exposes only its factory. Implementations provide their own `description`, `references`,
and `probe` methods. Cross-family reuse goes through public factories: size checks use
numeric conditions, and map checks use collection conditions on map views. Helpers
remain package-private alongside their callers.

`conditions.composition` shares nested checks behind `CompositionConditions`;
its implementations are package-private. The package is not exported by the module.
Classpath users can technically access its factory, but it is not a supported API:
the family factories enforce its type bounds.

## Fluent definitions and execution

`ApplicableCondition` constrains inputs and comparisons; `InputCondition` fixes
the comparison constraint for input-only checks. `PreservesObservation` returns
the complete source; selected/map-value conditions return the source's element,
entry, or value. `CollectingCondition` returns a list of source observations;
its default `stages()` lists itself. These families retain their constraints
through `because`.

Builders bind source-dependent definitions to core's single `Condition` contract.
They inherit its general `until`; no waiting loop is duplicated. Typed conditions
need no binding. `TypedCondition.from(coreCondition)` enables composition with
fluent definitions while delegating probe, lifecycle, metadata and diagnostics.

Handler events and histories expose core conditions. Use
`AwaitCondition.definitionOf(condition)` to recover an original fluent definition;
it returns an empty optional for a plain core condition.
`historyDefinitions(definition)` declares histories for a `TypedCondition` run;
the bridge keeps root metadata and stage order. Low-level
`AwaitCondition.bind(definition)` trusts the caller's input type; prefer `until`
for compile-time source checks.
