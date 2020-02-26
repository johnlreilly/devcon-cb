package com.example.circuitbreaker;

import java.util.function.Supplier;

import static com.example.circuitbreaker.CircuitBreaker.CircuitBreakerState.*;
/**
 * This is what holds the state and the main logic of circuit breaker. The circuit breaker starts in a closed state and
 * runs any operation that implements the {@code Supplier<T>} interface. The operation is executed based on the current state,
 * and the result is returned to the caller. After an operation runs, the circuit breaker will transition states based
 * on the effect that the operation had on the circuit breaker.
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <h3>Step 1: Define thresholds and configuration</h3>
 * The circuit breaker is configured to:
 * <ul>
 *     <li>transition from closed to open after 3 failures</li>
 *     <li>recover from closed state failures at a rate of 10 successes per failure</li>
 *     <li>recover from all closed state failures after 60 seconds of zero failures</li>
 *     <li>transition from open to half-open after a 5 second timeout</li>
 *     <li>transition from half-open to closed after 2 successful calls</li>
 *     <li>transition from half-open to open after a failed call</li>
 * </ul>
 *
 * <p>
 * Add the following line in your current caller class. The values can be changed to your liking.
 * </p>
 *
 * <pre>
 * {@code
 * int failureThreshold = 2;
 * int failureRecoveryRate = 10;
 * int errorGapThreshold = 60;
 * int timeoutInSeconds = 5;
 * int successThreshold = 2;
 * }
 * </pre>
 *
 * <h3>Step 2: Instantiate a circuit breaker</h3>
 *
 * Add this line in your current caller class, and don't forget to pass in the configuration that was created in step 1.
 *
 * <pre>
 * {@code
 * CircuitBreaker circuitBreaker = new CircuitBreaker(
 *          failureThreshold,
 *          failureRecoveryRate,
 *          errorGapThreshold,
 *          timeoutInSeconds,
 *          successThreshold
 * );
 * }
 * </pre>
 *
 * <h3>Step 3: Run an operation with the circuit breaker</h3>
 * Create an anonymous lambda function that calls the supplier service, and pass the function to the circuit breaker run
 * method. The circuit breaker will invoke the function on your behalf.
 *
 * <pre>
 * {@code
 * circuitBreaker.run(() ->
 * {
 *     // call supplier service here
 * });
 * }
 * </pre>
 *
 *
 * <h3>Step 4 (Optional): Change the transition logic or the methods to apply thresholds differently</h3>
 * How the operation is applied is defined in the method {@code run(Supplier<T> operation)}.
 */
class CircuitBreaker
{
    public enum CircuitBreakerState {CLOSED, OPEN, HALF_OPEN} // All of the circuit breaker states.
    static class CircuitBreakerOpenException extends RuntimeException {} // Breaker is OPEN & not accepting calls
    private CircuitBreakerState state; // The current circuit breaker state. It may be Closed, Open, or Half-Open.
    private double closedFailureCounter; // The amount of failures that occurred in closed state.
    private double closedRecoveryRate; // The rate of successful calls to recover from a failed call.
    private int closedFailureThreshold; // number of times before the Circuit trips from the Closed state.
    private long openTimeout; // The time that open state times out.
    private int openTimeoutThreshold; // number of seconds to wait until moving to a HalfOpen state.
    private int halfOpenSuccessCounter; // The amount of successful calls that occurred in half-open state.
    private int halfOpenSuccessThreshold; // number of times before the Circuit moves out of the HalfOpen state.
    private long maxErrorGapThreshold; // maximum number of nanoseconds since last error, before resetting counter
    private long lastTimeOfError; //time in nanoseconds of the last error occurred

    /**
     * Creates a circuit breaker.
     * @param maxNumberFailuresToTrip number of times before the Circuit trips from the Closed state
     * @param numberOfCallsToRecover number of successful calls to recover from a failed call
     * @param maxSecondsBetweenErrors maximum number of seconds since last error, before resetting counter
     * @param numberSecondsToHalfOpen number of seconds to wait until moving to a HalfOpen state
     * @param numberOfSuccessesToReOpen number of times before the Circuit moves out of the HalfOpen state
     */
    CircuitBreaker(int maxNumberFailuresToTrip, int numberOfCallsToRecover,
                   int maxSecondsBetweenErrors, int numberSecondsToHalfOpen, int numberOfSuccessesToReOpen)
    {
        this.closedFailureThreshold = maxNumberFailuresToTrip;
        this.closedRecoveryRate = 1.0 / numberOfCallsToRecover;
        this.maxErrorGapThreshold = maxSecondsBetweenErrors * 1000000000L;
        this.halfOpenSuccessThreshold = numberOfSuccessesToReOpen;
        this.openTimeoutThreshold = numberSecondsToHalfOpen;
        setState(CLOSED); // Start the circuit breaker in Closed State
    }

    /**
     * Runs the operation. (Main circuit breaker logic begins here.)
     * @param operation the operation to run
     * @param <T> the type of the operation result
     * @return the operation result
     */
    <T> T run(Supplier<T> operation)
    {
        // Closed State tries to run the operation, and if operation fails, it keeps count.
        if (CLOSED == state)
        {
            try
            {
                T result = operation.get();
                //since call was successful start to recover from errors by decrementing counter
                closedFailureCounter = closedFailureCounter - closedRecoveryRate;
                if (closedFailureCounter < 0)
                {
                    closedFailureCounter = 0;
                }
                return result;
            }
            catch (Exception ex)
            {
                long currentTime = getTime(); //time of current error
                if ((currentTime - lastTimeOfError) > maxErrorGapThreshold) //see when last error occurred
                {
                    closedFailureCounter = 0; //reset counter since too much time has elapsed
                }
                lastTimeOfError = currentTime;
                closedFailureCounter = closedFailureCounter + 1;
                // CLOSED->OPEN: this goes to Open State when the failure counter reaches its threshold
                if (closedFailureCounter >= closedFailureThreshold)
                {
                    setState(OPEN);
                }
                throw ex;
            }
        }

        // Open State does not run the operation, it throws an error.
        else if (OPEN == state)
        {
            // OPEN->HALF_OPEN: this goes to Half-Open State AFTER this call when the timeout reaches or exceeds its threshold
            if (getTime() > openTimeout)
            {
                setState(HALF_OPEN);
            }
            throw new CircuitBreakerOpenException();
        }

        // Half-Open State tries to run the operation, and if operation succeeds, it keeps count. If fails->OPEN state
        else
        {
            try
            {
                T result = operation.get();
                halfOpenSuccessCounter = halfOpenSuccessCounter + 1;

                // HALF_OPEN->CLOSED: this goes to Closed State when the success counter reaches its threshold.
                if (halfOpenSuccessCounter >= halfOpenSuccessThreshold)
                {
                    setState(CLOSED);
                }
                return result;
            }
            catch (Exception ex)
            {
                // HALF_OPEN->OPEN: this goes to Open State when a failure occurs in the HALF_OPEN state
                setState(OPEN);
                throw ex;
            }
        }
    }

    /**
     * Gets the current state of the circuit breaker
     * @return the current state
     */
    CircuitBreakerState getState()
    {
        return state;
    }

    /**
     * Sets a new state and also resets all counters and timeouts
     * @param circuitBreakerState the new state
     */
    void setState(CircuitBreakerState circuitBreakerState)
    {
        this.state = circuitBreakerState;
        closedFailureCounter = 0;
        halfOpenSuccessCounter = 0;
        openTimeout = getTime() + (openTimeoutThreshold * 1000000000L);
        // TODO: add logging for state transitioning (NOSONAR)
    }

    /**
     * Gets the closed failure threshold of the circuit breaker
     * @return the closed failure threshold
     */
    int getClosedFailureThreshold()
    {
        return closedFailureThreshold;
    }

    /**
     * Sets the closed failure threshold of the circuit breaker
     * @param closedFailureThreshold the new closed failure threshold
     */
    void setClosedFailureThreshold(int closedFailureThreshold)
    {
        this.closedFailureThreshold = closedFailureThreshold;
    }

    /**
     * Gets the closed recovery rate of the circuit breaker
     * @return the closed recovery rate
     */
    double getClosedRecoveryRate()
    {
        return closedRecoveryRate;
    }

    /**
     * Sets the closed recovery rate of the circuit breaker
     * @param closedRecoveryRate the new closed recovery rate
     */
    void setClosedRecoveryRate(int closedRecoveryRate)
    {
        this.closedRecoveryRate = 1.0 / closedRecoveryRate;
    }

    /**
     * Gets the max error gap threshold of the circuit breaker
     * @return the max error gap threshold
     */
    long getMaxErrorGapThreshold()
    {
        return maxErrorGapThreshold;
    }

    /**
     * Sets the max error gap threshold of the circuit breaker
     * @param maxErrorGapThreshold the new max error gap threshold
     */
    void setMaxErrorGapThreshold(int maxErrorGapThreshold)
    {
        this.maxErrorGapThreshold = maxErrorGapThreshold * 1000000000L;
    }

    /**
     * Gets the open timeout threshold of the circuit breaker
     * @return the open timeout threshold
     */
    int getOpenTimeoutThreshold()
    {
        return openTimeoutThreshold;
    }

    /**
     * Sets the open timeout threshold of the circuit breaker
     * @param openTimeoutThreshold the new open timeout threshold
     */
    void setOpenTimeoutThreshold(int openTimeoutThreshold)
    {
        this.openTimeoutThreshold = openTimeoutThreshold;
    }

    /**
     * Gets the half-open success threshold of the circuit breaker
     * @return the half-open success threshold
     */
    int getHalfOpenSuccessThreshold()
    {
        return halfOpenSuccessThreshold;
    }

    /**
     * Sets the half-open success threshold of the circuit breaker
     * @param halfOpenSuccessThreshold the new half-open success threshold
     */
    void setHalfOpenSuccessThreshold(int halfOpenSuccessThreshold)
    {
        this.halfOpenSuccessThreshold = halfOpenSuccessThreshold;
    }

    /**
     * Gets an elapsed time that always moves forward.
     * This method is overridable to enable automated testing.
     * @return an elapsed time
     */
    protected long getTime()
    {
        return System.nanoTime();
    }
}
