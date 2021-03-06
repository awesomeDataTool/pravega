/**
 * Copyright (c) 2017 Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.pravega.controller.eventProcessor;

/**
 * Functional interface that provides a Directive, which is either Restart, Resume,
 * or Stop, on encountering an exception while executing event procssors's user-defined methods.
 */
@FunctionalInterface
public interface ExceptionHandler {

    enum Directive {
        Restart,
        Resume,
        Stop
    }

    /**
     * The default decider that Stops the event processor on pre-defined event processor exceptions,
     * and Restarts it otherwise.
     */
    ExceptionHandler DEFAULT_EXCEPTION_HANDLER = (Throwable y) -> {
        if (y instanceof EventProcessorInitException ||
                y instanceof EventProcessorReinitException) {
            return ExceptionHandler.Directive.Stop;
        } else {
            return ExceptionHandler.Directive.Restart;
        }
    };

    /**
     * The decider method that returns a Directive.
     *
     * @param throwable Exception
     * @return Directive
     */
    Directive run(Throwable throwable);
}
