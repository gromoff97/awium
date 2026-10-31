# Development

[README](../README.md)

## Build and checks

```sh
./gradlew check                       # Both modules
./gradlew :awium-core:build           # Core without compiling or testing conditions
./gradlew :awium-conditions:build     # Conditions and its core dependency
```

`check` covers behavior, fluent compilation, packaged-module boundaries, and zero
third-party product compile/runtime dependencies. Tests use JUnit 6, AssertJ, Mockito,
compile-testing, ArchUnit, and Commons Lang via Gradle `testImplementation`.
Mockito uses JDK proxies and reflection without a Java agent.
Test logging uses `slf4j-jdk14` via `testRuntimeOnly`.

PIT uses `ALL`: run `./gradlew pitest` **only on explicit user request**, never
through `build`, `check`, or `test`.

Behavior tests share the product package and use package-private `usingTime(clock, parker)`
with `FakeTime`. This replaces time for one configuration. Both callbacks use the
same time base in nanoseconds: the clock is monotonic, and the parker may return early.
Clock failures propagate directly; parker failures stop the wait. Clock reads preserve
the interrupt flag. Production defaults are `System.nanoTime` and `LockSupport.parkNanos`.

Integration tests cover platform/virtual callers, cancellation, and concurrent
state isolation. Ordinary compiled tests demonstrate valid API use; negative
compilation tests protect incompatible-type rejection. Packaged-JAR consumer tests
use real time and only public methods.

## Responsibilities

| Component | Owns |
| --- | --- |
| `Condition`, `AwaitCondition` families | Immutable definitions, type constraints, and creation of independent runs |
| `Conditions`, domain catalogs | Public factories; no checking algorithms |
| `conditions.internal.*Condition` | Check algorithms, operands, mismatch descriptions, and composition |
| `AwaitCondition.CheckDefinition` | Shared evaluator storage and run creation for built-in checks |
| `execution.CheckRun` | One check's evaluation and final value |
| `conditions.CapturedCondition.Run` | Stage transitions, collected observations, and stage diagnostics |
| `execution.AbstractConditionRun` | Shared history recording and terminal result assembly |
| `execution.AttemptHistory` | Invocation numbering and grouping within each condition's history |
| `Await`; fluent await classes | Source entry points and configuration |
| `AwaitSupport` | Starting a run, passing it to the engine, and returning its result |
| `execution.ConditionRun` | Uniform lifecycle contract; no sequence or stage concepts |
| `execution.ConditionExecution` | Lazy evaluator initialization and nested check invocation |
| `internal.WaitEngine` | Polling, deadlines, and persistence from immutable step snapshots |
| `internal.AttemptExecution`; `ExecutionStep` | Source/check calls, failures, and attempt timing |
| `execution.FailureDiagnostics`; `execution.DiagnosticText` | Terminal formatting; guarded value rendering |

`ConditionResult` is the sole check outcome. Exceptions retain their original cause;
`ProbeFailure` adds the location of a nested check before history records the failure.

## Type and module boundaries

`Condition<INPUT, RESULT>` is open to consumer implementations. Its required
`probe` and `description` methods keep predicate overloads unambiguous.
`AwaitCondition` carries metadata and run creation. `ApplicableCondition` constrains
inputs and comparisons; `InputCondition` fixes the comparison constraint for input-only
checks. Its separate type supports single-source type hints in `captured`.
`CollectingCondition` declares a source-observation list result; terminal bounds
check inputs. `CapturedCondition` owns its stages in `awium-conditions`.
`PreservesObservation` shares a terminal overload across preserving checks and comparisons.
Selector contracts infer outputs from source types; their implementations must return
the corresponding element, entry, or value. Custom checks normally implement
`Condition<INPUT, RESULT>`, which declares both types explicitly.

`awium-core` exports `io.github.gromoff97.awium`, `.result`, `.function`, and `.execution`.
It depends only on the JDK. `awium-conditions` exports `.conditions` and depends on
core's public API. Their Java modules are `io.github.gromoff97.awium.core` and
`io.github.gromoff97.awium.conditions`; neither shares a package with the other.
Both `.internal` packages are unexported. No core `permits` list names a built-in condition.

The execution package supplies lifecycle contracts and shared implementation.
`WaitTiming` exposes durations without clock-injection hooks. Core test fixtures
share fake time and assertions with condition tests; they are not product dependencies.

Architecture tests keep the engine independent of condition implementations,
fluent facades, and formatting. Implementations cannot call the engine or catalogs.
A condition run owns its completion decision, history scopes, result, and diagnostic
context. The engine controls when it may run, whether it must persist, and when waiting
ends. Mutable execution state belongs to one run; reusable definitions remain immutable.
