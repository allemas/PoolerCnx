package com.allemas.pool;

public class IllegalAcquire extends RuntimeException {
    public IllegalAcquire() {
        super("Connexion acquisition could be acquired");
    }

    public IllegalAcquire(String message) {
        super(message);
    }
}
