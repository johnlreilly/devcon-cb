package com.example.circuitbreaker;

import com.example.circuitbreaker.CircuitBreaker.CircuitBreakerOpenException;
import org.junit.Before;
import org.junit.Test;

import java.util.function.Supplier;

import static com.example.circuitbreaker.CircuitBreaker.CircuitBreakerState.*;
import static org.junit.Assert.*;

/**
 * These are the junit tests for the circuit breaker.
 */
public class CircuitBreakerTest
{
    private static final String DUMMY_RESULT = "result";
    private static final int FAILURE_THRESHOLD = 3;
    private static final int RECOVERY_RATE = 10;
    private static final int ERROR_GAP_THRESHOLD = 5;
    private static final int TIMEOUT_IN_SECONDS = 2;
    private static final int SUCCESS_THRESHOLD = 2;

    private final MockOperation mockOperation = new MockOperation();
    private CircuitBreakerTimeStub circuitBreaker;

    /**
     * Create a new circuit breaker before running each test.
     */
    @Before
    public void before()
    {
        circuitBreaker = new CircuitBreakerTimeStub(
                FAILURE_THRESHOLD,
                RECOVERY_RATE,
                ERROR_GAP_THRESHOLD,
                TIMEOUT_IN_SECONDS,
                SUCCESS_THRESHOLD
        );
    }

    /**
     * When the circuit breaker is created, the circuit breaker state is closed.
     */
    @Test
    public void getsCurrentStateInClosedStateTest()
    {
        assertEquals(CLOSED, circuitBreaker.getState());
    }

    /**
     * When the circuit breaker runs an operation, the circuit breaker invokes the operation.
     */
    @Test
    public void runsOperationInClosedStateTest()
    {
        circuitBreaker.run(mockOperation);

        mockOperation.verify();
        assertEquals(CLOSED, circuitBreaker.getState());
    }

    /**
     * When the circuit breaker runs an operation, the circuit breaker returns the result of the operation.
     */
    @Test
    public void returnsResultInClosedStateTest()
    {
        mockOperation.set(DUMMY_RESULT);

        assertEquals(DUMMY_RESULT, circuitBreaker.run(mockOperation));
        assertEquals(CLOSED, circuitBreaker.getState());
    }

    /**
     * When the circuit breaker runs an operation, and the operation throws an exception, the circuit breaker escalates
     * the exception.
     */
    @Test(expected = TestException.class)
    public void escalatesExceptionInClosedStateTest()
    {
        mockOperation.throwException(new TestException());
        circuitBreaker.run(mockOperation);
    }

    /**
     * Given the failure count is 1, and the failure threshold is 3, when the circuit breaker runs an operation, and the
     * operation throws an exception, the circuit breaker state is closed.
     */
    @Test
    public void stateRemainsUnderThresholdInClosedStateTest()
    {
        mockOperation.throwException(new TestException());
        runThroughFailure(mockOperation, FAILURE_THRESHOLD - 1);

        assertEquals(CLOSED, circuitBreaker.getState());
    }

    /**
     * Given the failure count is 2, and the failure threshold is 3, when the circuit breaker runs an operation, and the
     * operation throws an exception, the circuit breaker state is open.
     */
    @Test
    public void transitionsToOpenStateOnThresholdInClosedStateTest()
    {
        mockOperation.throwException(new TestException());
        runThroughFailure(mockOperation, FAILURE_THRESHOLD);

        assertEquals(OPEN, circuitBreaker.getState());
    }

    /**
     * Given the failure count is 2, and the failure threshold is 3, and the error gap threshold is 5 seconds,
     * when the circuit breaker doesn't receive an operation for 6 seconds, and the circuit breaker then runs an
     * operation, and the operation throws an exception, the circuit breaker state is closed.
     */
    @Test
    public void closedStateRecoversWithTimeTest()
    {
        circuitBreaker.setTime(toNano(0));
        mockOperation.throwException(new TestException());
        runThroughFailure(mockOperation, FAILURE_THRESHOLD - 1);

        circuitBreaker.setTime(toNano(ERROR_GAP_THRESHOLD + 1));
        runThroughFailure(mockOperation, FAILURE_THRESHOLD - 1);

        assertEquals(CLOSED, circuitBreaker.getState());
    }

    /**
     * Given the failure count is 2, and the failure threshold is 3, and the recovery rate is 10, when the circuit
     * breaker runs 10 operations without the operation throwing an exception, and the circuit breaker runs one
     * operation where the operation throws an exception, the circuit breaker state is closed. When the circuit breaker
     * runs an operation one more time, and the operation throws an exception, the circuit breaker state is open.
     */
    @Test
    public void closedStateRecoversWithSuccessfulOperationsTest()
    {
        mockOperation.throwException(new TestException());
        runThroughFailure(mockOperation, FAILURE_THRESHOLD - 1);

        mockOperation.notThrowException();
        run(mockOperation, RECOVERY_RATE - 1);

        mockOperation.throwException(new TestException());
        runThroughFailure(mockOperation);
        assertEquals(CLOSED, circuitBreaker.getState());

        runThroughFailure(mockOperation);
        assertEquals(OPEN, circuitBreaker.getState());
    }

    /**
     * Given the circuit breaker transitions to open state, the circuit breaker state is open.
     */
    @Test
    public void getsCurrentStateInOpenStateTest()
    {
        transitionToOpenState();
        assertEquals(OPEN, circuitBreaker.getState());
    }

    /**
     * Given the circuit breaker transitions to open state, when the circuit breaker runs an operation, the circuit
     * breaker does not invoke the operation.
     */
    @Test
    public void neverRunsOperationInOpenStateTest()
    {
        transitionToOpenState();
        runThroughFailure(mockOperation);

        mockOperation.verifyNot();
        assertEquals(OPEN, circuitBreaker.getState());
    }

    /**
     * Given the circuit breaker transitions to open state, when the circuit breaker runs an operation, the circuit
     * breaker throws an open circuit breaker exception.
     */
    @Test(expected = CircuitBreakerOpenException.class)
    public void throwsExceptionInOpenStateTest()
    {
        transitionToOpenState();
        circuitBreaker.run(mockOperation);
    }

    /**
     * Given the circuit breaker transitions to open state, and the open timeout is 2 seconds, when 1 second has passed,
     * the circuit breaker state is open.
     */
    @Test
    public void stateRemainsUnderThresholdInOpenStateTest()
    {
        transitionToOpenState();
        circuitBreaker.setTime(toNano(TIMEOUT_IN_SECONDS - 1));
        runThroughFailure(mockOperation);

        assertEquals(OPEN, circuitBreaker.getState());
    }

    /**
     * Given the circuit breaker transitions to open state, and the open timeout is 2 seconds, when 3 seconds has
     * passed, the circuit breaker state is half-open.
     */
    @Test
    public void transitionsToHalfOpenStateOnThresholdInOpenStateTest()
    {
        transitionToOpenState();
        circuitBreaker.setTime(toNano(TIMEOUT_IN_SECONDS + 1));
        runThroughFailure(mockOperation);

        assertEquals(HALF_OPEN, circuitBreaker.getState());
    }

    /**
     * Given the circuit breaker transitions to half-open state, the circuit breaker state is half-open.
     */
    @Test
    public void getsCurrentStateInHalfOpenStateTest()
    {
        transitionToHalfOpenState();
        assertEquals(HALF_OPEN, circuitBreaker.getState());
    }

    /**
     * Given the circuit breaker transitions to half-open state, when the circuit breaker runs an operation, the circuit
     * breaker invokes the operation.
     */
    @Test
    public void runsOperationInHalfOpenStateTest()
    {
        transitionToHalfOpenState();
        runThroughFailure(mockOperation);

        mockOperation.verify();
        assertEquals(HALF_OPEN, circuitBreaker.getState());
    }

    /**
     * Given the circuit breaker transitions to half-open state, when the circuit breaker runs an operation, the circuit
     * breaker returns the result of the operation.
     */
    @Test
    public void returnsResultInHalfOpenStateTest()
    {
        transitionToHalfOpenState();
        mockOperation.set(DUMMY_RESULT);

        assertEquals(DUMMY_RESULT, circuitBreaker.run(mockOperation));
        assertEquals(HALF_OPEN, circuitBreaker.getState());
    }

    /**
     * Given the circuit breaker transitions to half-open state, when the circuit breaker runs an operation, and the
     * operation throws an exception, the circuit breaker escalates the exception.
     */
    @Test(expected = TestException.class)
    public void escalatesExceptionInHalfOpenStateTest()
    {
        transitionToHalfOpenState();
        mockOperation.throwException(new TestException());

        circuitBreaker.run(mockOperation);
    }

    /**
     * Given the circuit breaker transitions to half-open state, when the circuit breaker runs an operation, and the
     * operation throws an exception, the circuit breaker state is open.
     */
    @Test
    public void transitionsToOpenStateOnFailureInHalfOpenStateTest()
    {
        transitionToHalfOpenState();
        mockOperation.throwException(new TestException());
        runThroughFailure(mockOperation);

        assertEquals(OPEN, circuitBreaker.getState());
    }

    /**
     * Given the circuit breaker transitions to half-open state, and the success threshold is 2, and the success counter
     * is 0, when the circuit breaker runs an operation, the circuit breaker state is half-open.
     */
    @Test
    public void stateRemainsUnderThresholdInHalfOpenStateTest()
    {
        transitionToHalfOpenState();
        run(mockOperation, SUCCESS_THRESHOLD - 1);

        assertEquals(HALF_OPEN, circuitBreaker.getState());
    }

    /**
     * Given the circuit breaker transitions to half-open state, and the success threshold is 2, and the success counter
     * is 1, when the circuit breaker runs an operation, the circuit breaker state is closed.
     */
    @Test
    public void transitionsToClosedStateOnThresholdInHalfOpenStateTest()
    {
        transitionToHalfOpenState();
        run(mockOperation, SUCCESS_THRESHOLD);

        assertEquals(CLOSED, circuitBreaker.getState());
    }

    /**
     * Given the circuit breaker transitions to open state, when the admin manually sets the state to closed, the
     * circuit breaker state is closed.
     */
    @Test
    public void adminCanSetStateTest()
    {
        transitionToOpenState();
        assertEquals(OPEN, circuitBreaker.getState());

        circuitBreaker.setState(CLOSED);
        assertEquals(CLOSED, circuitBreaker.getState());
    }

    /**
     * When the admin manually sets the closed failure threshold, the circuit breaker closed failure threshold is the
     * value the admin set.
     */
    @Test
    public void adminCanSetClosedFailureThresholdTest()
    {
        assertEquals(FAILURE_THRESHOLD, circuitBreaker.getClosedFailureThreshold());

        circuitBreaker.setClosedFailureThreshold(FAILURE_THRESHOLD + 1);
        assertEquals(FAILURE_THRESHOLD + 1, circuitBreaker.getClosedFailureThreshold());
    }

    /**
     * When the admin manually sets the closed recovery rate, the circuit breaker closed recovery rate is the
     * value the admin set.
     */
    @Test
    public void adminCanSetClosedRecoveryRateTest()
    {
        assertEquals(1.0 / RECOVERY_RATE, circuitBreaker.getClosedRecoveryRate(), 0);

        circuitBreaker.setClosedRecoveryRate(RECOVERY_RATE + 1);
        assertEquals(1.0/ (RECOVERY_RATE + 1), circuitBreaker.getClosedRecoveryRate(), 0);
    }

    /**
     * When the admin manually sets the max error gap threshold, the circuit breaker max error gap threshold is the
     * value the admin set.
     */
    @Test
    public void adminCanSetMaxErrorGapThresholdTest()
    {
        assertEquals(ERROR_GAP_THRESHOLD * 1000000000L, circuitBreaker.getMaxErrorGapThreshold());

        circuitBreaker.setMaxErrorGapThreshold(ERROR_GAP_THRESHOLD + 1);
        assertEquals((ERROR_GAP_THRESHOLD + 1) * 1000000000L, circuitBreaker.getMaxErrorGapThreshold());
    }

    /**
     * When the admin manually sets the open timeout threshold, the circuit breaker open timeout threshold is the
     * value the admin set.
     */
    @Test
    public void adminCanSetOpenTimeoutThresholdTest()
    {
        assertEquals(TIMEOUT_IN_SECONDS, circuitBreaker.getOpenTimeoutThreshold());

        circuitBreaker.setOpenTimeoutThreshold(TIMEOUT_IN_SECONDS + 1);
        assertEquals(TIMEOUT_IN_SECONDS + 1, circuitBreaker.getOpenTimeoutThreshold());
    }

    /**
     * When the admin manually sets the half-open success threshold, the circuit breaker half-open success threshold is
     * the value the admin set.
     */
    @Test
    public void adminCanSetHalfOpenSuccessThresholdTest()
    {
        assertEquals(SUCCESS_THRESHOLD, circuitBreaker.getHalfOpenSuccessThreshold());

        circuitBreaker.setHalfOpenSuccessThreshold(SUCCESS_THRESHOLD + 1);
        assertEquals(SUCCESS_THRESHOLD + 1, circuitBreaker.getHalfOpenSuccessThreshold());
    }

    /**
     * Helper method to transition from closed state to open state.
     */
    private void transitionToOpenState()
    {
        // transition Closed -> Open
        mockOperation.throwException(new RuntimeException());
        runThroughFailure(mockOperation, FAILURE_THRESHOLD);

        mockOperation.reset();
    }

    /**
     * Helper method to transition from closed state to half-open state.
     */
    private void transitionToHalfOpenState()
    {
        transitionToOpenState();

        // transition Open -> Half-Open
        circuitBreaker.setTime(toNano(TIMEOUT_IN_SECONDS + 1));
        runThroughFailure(mockOperation);

        mockOperation.reset();
    }

    /**
     * Helper method to run an operation that will not throw an exception multiple times.
     */
    private void run(Supplier<String> operation, int n)
    {
        if (n == 0)
        {
            return;
        }

        circuitBreaker.run(operation);
        run(operation, n - 1);
    }

    /**
     * Helper method to run an operation that will throw an exception one time.
     */
    private void runThroughFailure(Supplier<String> operation)
    {
        runThroughFailure(operation, 1);
    }

    /**
     * Helper method to run an operation that will throw an exception multiple times.
     */
    private void runThroughFailure(Supplier<String> operation, int n)
    {
        if (n == 0)
        {
            return;
        }

        try
        {
            circuitBreaker.run(operation);
            runThroughFailure(operation, n - 1);
        }
        catch (Exception ex)
        {
            runThroughFailure(operation, n - 1);
        }
    }

    /**
     * Helper method to convert number in seconds to nanoseconds.
     */
    private long toNano(int timeoutInSeconds)
    {
        return timeoutInSeconds * 1000000000L;
    }

    /**
     * A subclass of CircuitBreaker that stubs the elapsed time. This is necessary to freeze the time for unit testing.
     */
    private static class CircuitBreakerTimeStub extends CircuitBreaker
    {
        long time = 0L;

        CircuitBreakerTimeStub(int maxNumberFailuresToTrip, int numberOfCallsToRecover, int maxSecondsBetweenErrors,
                               int numberSecondsToHalfOpen, int numberOfSuccessesToReOpen)
        {
            super(maxNumberFailuresToTrip, numberOfCallsToRecover, maxSecondsBetweenErrors,
                    numberSecondsToHalfOpen, numberOfSuccessesToReOpen);
        }

        void setTime(long time)
        {
            this.time = time;
        }

        @Override
        protected long getTime()
        {
            return time;
        }
    }

    /**
     * A type of operation decorated with capabilities to verify execution.
     */
    private static class MockOperation implements Supplier<String>
    {
        private boolean verified = false;
        private Supplier<String> operation;

        private MockOperation()
        {
            reset();
        }

        @Override
        public String get()
        {
            return operation.get();
        }

        private void set(String value)
        {
            operation = () ->
            {
                verified = true;
                return value;
            };
        }

        private void notThrowException()
        {
            set(null);
        }

        private void throwException(RuntimeException e)
        {
            operation = () -> {
                throw e;
            };
        }

        private void verify()
        {
            assertTrue(verified);
        }

        private void verifyNot()
        {
            assertFalse(verified);
        }

        private void reset()
        {
            set(null);
            verified = false;
        }
    }

    private static class TestException extends RuntimeException {}
}
