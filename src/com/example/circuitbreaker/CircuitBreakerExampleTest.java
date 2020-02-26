package com.example.circuitbreaker;

import org.junit.Test;

import static com.example.circuitbreaker.CircuitBreaker.CircuitBreakerOpenException;
import static org.junit.Assert.assertEquals;

/**
 * This is an example of how to use the circuit breaker.
 */
public class CircuitBreakerExampleTest
{
    private static final int FAILURE_THRESHOLD = 3; // transition from closed to open after 3 failures.
    private static final int RECOVERY_RATE = 10; // recover from failures at a rate of 10 successes per failure.
    private static final int ERROR_GAP_THRESHOLD = 60; // recover from failures after 60 seconds of no failures.
    private static final int TIMEOUT_IN_SECONDS = 30; // transition from open to half-open after a 30 second timeout.
    private static final int SUCCESS_THRESHOLD = 2; // transition from half-open to closed after 2 successful calls.

    /**
     * When the circuit breaker runs an operation, it returns the result of the operation.
     */
    @Test
    public void callOperationUsingCircuitBreakerTest()
    {
        try
        {
            //configure the circuit breaker with the desired values
            CircuitBreaker circuitBreaker = new CircuitBreaker(
                    FAILURE_THRESHOLD,
                    RECOVERY_RATE,
                    ERROR_GAP_THRESHOLD,
                    TIMEOUT_IN_SECONDS,
                    SUCCESS_THRESHOLD
            );

            //request the circuit breaker to run the specified function
            int sum = circuitBreaker.run(() -> externalMathSvcCalculateAreaCallback(2, 3));

            //verify that the Circuit Breaker returned the correct value
            assertEquals(6, sum);
        }
        catch (CircuitBreakerOpenException ex)
        {
            //method was not called, Circuit Breaker was OPEN
            throw ex;
        }
        catch (Exception ex)
        {
            //method call failed
            throw ex;
        }
    }

    /**
     * This is the method that you wish to have proxied by the Circuit Breaker. If the circuit breaker is closed it will
     * trigger this method to be called. The method can have any return value and take any # of parameters.
     * @param width the width of the shape
     * @param length the length of the shape
     * @return the area of the shape
     */
    private int externalMathSvcCalculateAreaCallback(int width, int length)
    {
        //place logic here to make remote service call handling any HTTP/REST logic as needed
        //for the purposes of the example this is simply represented below as a math formula
        //.......
        return width * length;
    }
}
