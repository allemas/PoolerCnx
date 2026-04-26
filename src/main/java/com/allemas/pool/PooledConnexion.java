package com.allemas.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;

import java.util.function.Supplier;

public class PooledConnexion<T extends Connection> implements AutoCloseable {
    private final Logger logger = LoggerFactory.getLogger(PooledConnexion.class);

    private final T connexion;

    private boolean active;
    private final boolean closed;

    public PooledConnexion(Supplier<T> connexionBuilder) {
        connexion = connexionBuilder.get();
        active = false;
        closed = false;

    }

    @Override
    public void close() throws Exception {
        connexion.close();
    }

    public boolean isClosed() {
        return closed;
    }

    public void setActive() {
        active = true;
    }

    public boolean isActive() {
        return active;
    }

    public T getConnexion() {
        return connexion;
    }
}