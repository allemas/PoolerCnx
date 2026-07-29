package com.allemas.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;


public class PooledEntity<T extends Connection> implements AutoCloseable {

    private final Logger logger = LoggerFactory.getLogger(PooledEntity.class);

    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);
    private final T connexion;
    private State state;
    private final int id;

    public PooledEntity(int identity, Supplier<T> connexionBuilder) {
        id = identity;
        logger.info("Create entity#{}", id);
        connexion = connexionBuilder.get();
        state = State.IDLE;
    }

    /**
     * Recycle lifecycle inspired by HikariCP: passing the pool instance
     * at construction allows the entity to return itself to the pool
     * for recycling on close().
     */
    @Override
    public void close() throws Exception {
        logger.info("close entity#{} (state was {})", id, state);

        if (!state.equals(State.ACQUIRED))
            throw new IllegalStateConnexionException("This connexion is use");
        state = State.IDLE;
    }

    public boolean isIdle() {
        return state.equals(State.IDLE);
    }

    public boolean isClosed() {
        return state.equals(State.CLOSED);
    }

    public void markUsed() {
        logger.info("mark used entity#{}", id);
        state = State.ACQUIRED;
    }

    public T getConnexion() {
        return connexion;
    }

    public Integer getId() {
        return id;
    }

    @Override
    public String toString() {
        return "PoolEntity#" + id + "[" + state + "]";
    }

    public State getState() {
        return state;
    }

    public void markClosed() {
        logger.info("mark closed entity#{} (state was {})", id, state);
        state = State.CLOSED;
    }

    public static <T extends Connection> PooledEntity<T> build(int id, Supplier<T> connexionBuilder) {
        return new PooledEntity<>(id, connexionBuilder);
    }


}