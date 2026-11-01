package io.github.gromoff97.awium.conditions.support

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.AwaitCondition.ComparisonCondition
import io.github.gromoff97.awium.AwaitCondition.PreservingCondition
import io.github.gromoff97.awium.Condition
import io.github.gromoff97.awium.execution.ConditionExecution
import io.github.gromoff97.awium.execution.ProbeFailure
import io.github.gromoff97.awium.result.Attempt
import io.github.gromoff97.awium.result.AwaitFailure
import io.github.gromoff97.awium.result.AwaitFailure.Reason
import io.github.gromoff97.awium.result.AwaitResult
import io.github.gromoff97.awium.result.AttemptResult
import io.github.gromoff97.awium.result.AttemptResult.Satisfied
import io.github.gromoff97.awium.result.AttemptResult.Unsatisfied
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.fail

object ConditionAssertions {
    fun evaluate(condition: AwaitCondition, actual: Any?): AttemptResult<*> =
        ConditionExecution.bind<Any?, Any?>(condition).probe(actual)

    fun rejection(condition: AwaitCondition, actual: Any?): Unsatisfied<*> =
        assertInstanceOf(Unsatisfied::class.java, evaluate(condition, actual))

    fun thrown(condition: AwaitCondition, actual: Any?): Throwable {
        val failure = assertThrows(Throwable::class.java) { evaluate(condition, actual) }
        return if (failure is ProbeFailure) failure.cause!! else failure
    }

    fun <SOURCE, RESULT> evaluate(condition: Condition<in SOURCE, out RESULT>, actual: SOURCE): AttemptResult<out RESULT> =
        ConditionExecution.bind(condition).probe(actual)

    fun <SOURCE> evaluate(condition: PreservingCondition<in SOURCE>, actual: SOURCE): AttemptResult<out SOURCE> =
        ConditionExecution.bind<SOURCE, SOURCE>(condition).probe(actual)

    fun <SOURCE> evaluate(condition: ComparisonCondition<out SOURCE>, actual: SOURCE): AttemptResult<out SOURCE> =
        ConditionExecution.bind<SOURCE, SOURCE>(condition).probe(actual)

    fun <RESULT> result(evaluation: AttemptResult<out RESULT>): RESULT =
        if (evaluation is Satisfied<out RESULT>) evaluation.value() else fail("expected a satisfied condition, got $evaluation", evaluation.cause())

    fun mismatch(evaluation: AttemptResult<*>): String? =
        assertInstanceOf(Unsatisfied::class.java, evaluation).mismatch()

    fun failure(result: AwaitResult<*, *>): AwaitFailure =
        assertInstanceOf(AwaitResult.Failed::class.java, result).failure()

    fun failure(expected: Reason, result: AwaitResult<*, *>): AwaitFailure =
        failure(result).also { assertEquals(expected, it.reason()) }

    fun <OBSERVED> attempts(result: AwaitResult<OBSERVED, *>): List<Attempt<OBSERVED>> =
        result.conditions().flatMap { it.attempts().values }

    fun <OBSERVED> observation(attempt: Attempt<OBSERVED>): OBSERVED = when (attempt) {
        is Attempt.SatisfiedObservation -> attempt.observed()
        is Attempt.UnsatisfiedObservation -> attempt.observed()
        is Attempt.FailedAfterObservation -> attempt.observed()
        is Attempt.FailedBeforeObservation -> fail("expected an observation, got $attempt", attempt.cause())
    }

    fun uncontrolled(operation: Attempt.Operation, attempt: Attempt<*>): Attempt.Failure<*> =
        assertInstanceOf(Attempt.Failure::class.java, attempt).also { assertEquals(operation, it.operation()) }
}
