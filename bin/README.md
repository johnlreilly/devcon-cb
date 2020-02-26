# Circuit Breaker for Java
1. [Pattern Overview](#pattern-overview)
1. [Compiling and Testing](#compiling-and-testing)

## Pattern Overview

The purpose of this template codebase is to learn how to 
build a circuit breaker in Java.

The Circuit Breaker pattern will provide the ability to 
prevent a caller from repeatedly trying to request an 
operation on a supplier service that is no longer reachable, 
failed, or not responding in a timely manner due to load or 
other application errors. The pattern can help maintain 
the response time of the system by quickly rejecting a request 
for an operation that's likely to fail, rather than 
waiting for the operation to time out, or never return. 
This is a similar concept to an electrical Circuit Breaker 
in your home. In this application, the electrical Circuit 
Breaker would trip (open state) when an error condition 
(too much current) is detected.  This will cut the power to 
the device until the problem is resolved and the circuit is 
reset to a closed state, manually or by a person. This is the 
same concept in the software Circuit Breaker pattern except 
to be truly effective, there is an automated way to trigger 
the reset.

The Circuit Breaker pattern will allow the caller of an 
unresponsive supplier service to quickly revert to an 
existing error condition without waiting for a 
timeout. This ability will provide a time for the 
unavailable service to recover.

- Caller: The object that is calling a remote service.
- Supplier Service: The functionality being invoked.
- Circuit Breaker: An intermediary or proxy that protects
the client from using the service when it’s unreliable.

## Compiling and Testing

To compile and run all unit tests, run the following 
command: 

```shell script
mvn clean test
```
