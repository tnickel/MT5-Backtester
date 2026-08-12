package com.backtester.workflow;

/**
 * Signals that the chain stopped on purpose and waits for the user, as opposed to
 * failing. The pipeline reports it as a pause: the task stays reopenable and no
 * error dialog is raised.
 */
public class WorkflowPauseException extends RuntimeException {

    public WorkflowPauseException(String message) {
        super(message);
    }
}
