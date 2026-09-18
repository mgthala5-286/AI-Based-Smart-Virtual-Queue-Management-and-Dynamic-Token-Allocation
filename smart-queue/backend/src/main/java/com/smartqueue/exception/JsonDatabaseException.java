package com.smartqueue.exception;

public class JsonDatabaseException extends RuntimeException {

    public JsonDatabaseException(String message) {
        super(message);
    }

    public JsonDatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
