package com.allemas.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;


public class PooledEntity<T extends Connection> implements AutoCloseable {

    private final Logger logger = LoggerFactory.getLogger(PooledEntity.class);

    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);
    private final T connexion;
    private State state;
    private final int id;

    private final Consumer<PooledEntity<T>> recycler;

    public PooledEntity(int identity, Supplier<T> connexionBuilder, Consumer<PooledEntity<T>> garbage) {
        id = identity;
        connexion = connexionBuilder.get();
        state = State.IDLE;
        recycler = garbage;

        logger.info("Create entity#{}", id);
    }

    /**
     * Recycle lifecycle inspired by HikariCP: passing the pool instance
     * at construction allows the entity to return itself to the pool
     * for recycling on close().
     */
    @Override
    public void close() throws Exception {
        logger.info("close entity#{} (state was {})", id, state);

        if (!state.equals(State.IN_USE))
            throw new IllegalStateConnexionException("This connexion is use");
        state = State.IDLE;
        recycler.accept(this);
    }

    public boolean isIdle() {
        return state.equals(State.IDLE);
    }

    public void markUsed() {
        logger.info("mark used entity#{}", id);
        state = State.IN_USE;
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

    public static <T extends Connection> PooledEntity<T> build(int id, Supplier<T> connexionBuilder,
                                                               Consumer<PooledEntity<T>> askRecycling) {
        return new PooledEntity<>(id, connexionBuilder, askRecycling);
    }

}