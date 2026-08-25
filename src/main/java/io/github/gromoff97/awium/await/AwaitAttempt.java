package io.github.gromoff97.awium.await;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

public record AwaitAttempt<S, R>(long number, Phase phase, Outcome<S, R> outcome) {

    public AwaitAttempt {
        if (number <= 0) {
            throw new IllegalArgumentException("attempt number must be greater than zero");
        }
        requireNonNull(phase, "phase must not be null");
        requireNonNull(outcome, "outcome must not be null");
    }

    public enum Phase { ACQUISITION, STABILIZATION }

    public sealed interface Outcome<S, R> {

        record Satisfied<S, R>(Timing.AfterObservation timing, S observed, R result) implements Outcome<S, R> {

            public Satisfied {
                requireNonNull(timing, "timing must not be null");
            }
        }

        record Unsatisfied<S, R>(Timing.AfterObservation timing, S observed, String mismatch) implements Outcome<S, R> {

            public Unsatisfied {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(mismatch, "mismatch must not be null");
            }
        }

        record AssertionUnsatisfied<S, R>(Timing.AfterObservation timing, S observed,
                String mismatch, AssertionError assertion) implements Outcome<S, R> {

            public AssertionUnsatisfied {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(mismatch, "mismatch must not be null");
                requireNonNull(assertion, "assertion must not be null");
            }
        }

        record WaitingFailed<S, R>(Timing.BeforeRetrieval timing, Throwable failure) implements Outcome<S, R> {

            public WaitingFailed {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(failure, "failure must not be null");
            }
        }

        record SourceRetrievalFailed<S, R>(Timing.BeforeObservation timing, Throwable failure) implements Outcome<S, R> {

            public SourceRetrievalFailed {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(failure, "failure must not be null");
            }
        }

        record SourceInterrupted<S, R>(Timing.AfterObservation timing, S observed,
                InterruptedException failure) implements Outcome<S, R> {

            public SourceInterrupted {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(failure, "failure must not be null");
            }
        }

        record ConditionEvaluationFailed<S, R>(Timing.AfterObservation timing,
                S observed, Throwable failure) implements Outcome<S, R> {

            public ConditionEvaluationFailed {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(failure, "failure must not be null");
            }
        }
    }

    public sealed interface Timing {

        record BeforeRetrieval(Duration startOffset, Duration completionOffset) implements Timing {

            public BeforeRetrieval {
                requireNonNull(startOffset, "start offset must not be null");
                requireNonNull(completionOffset, "completion offset must not be null");
            }
        }

        record BeforeObservation(Duration startOffset, Duration retrievalOffset, Duration completionOffset) implements Timing {

            public BeforeObservation {
                requireNonNull(startOffset, "start offset must not be null");
                requireNonNull(retrievalOffset, "retrieval offset must not be null");
                requireNonNull(completionOffset, "completion offset must not be null");
            }
        }

        record AfterObservation(Duration startOffset, Duration retrievalOffset,
                Duration observationOffset, Duration completionOffset) implements Timing {

            public AfterObservation {
                requireNonNull(startOffset, "start offset must not be null");
                requireNonNull(retrievalOffset, "retrieval offset must not be null");
                requireNonNull(observationOffset, "observation offset must not be null");
                requireNonNull(completionOffset, "completion offset must not be null");
            }
        }
    }
}
