package com.allemas.pool;

public class IllegalAcquireException extends RuntimeException {
    public IllegalAcquireException() {
        super("Connexion acquisition could be acquired");
    }

    public IllegalAcquireException(String message) {
        super(message);
    }
}
