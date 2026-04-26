package com.allemas.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class PoolEntity<T extends Connection> implements AutoCloseable {

    private final Logger logger = LoggerFactory.getLogger(PoolEntity.class);

    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);
    private final T connexion;
    private PoolState state = PoolState.UNKNOWN;
    private final int id;

    public PoolEntity(Supplier<T> connexionBuilder) {
        id = ID_GENERATOR.getAndIncrement();
        connexion = connexionBuilder.get();
        state = PoolState.IDLE;
    }

    /**
     * Recycle lifecycle inspired by HikariCP: passing the pool instance
     * at construction allows the entity to return itself to the pool
     * for recycling on close().
     */
    @Override
    public void close() throws Exception {
        logger.info("close" + id + "connexion");

        if (state != PoolState.IN_USE)
            throw new IllegalStateConnexionException("This connexion is use");
        state = PoolState.IDLE;
    }

    public boolean isClosed() {
        return state == PoolState.CLOSED;
    }

    public boolean isIdle() {
        return state == PoolState.IDLE;
    }

    public void markUsed() {
        state = PoolState.IN_USE;

    }

    public PoolState destroy() throws SQLException {
        connexion.close();
        state = PoolState.CLOSED;
        return state;
    }

    public T getConnexion() {
        return connexion;
    }

    @Override
    public String toString() {
        return "PoolEntity#" + id + "[" + state + "]";
    }
}