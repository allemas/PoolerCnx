package com.allemas.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.allemas.pool.State.CLOSED;

public class PoolEntity<T extends Connection> implements AutoCloseable {

    private final Logger logger = LoggerFactory.getLogger(PoolEntity.class);

    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);
    private final T connexion;
    private State state = CLOSED;
    private final int id;

    private Consumer<PoolEntity<T>> recycler;

    public PoolEntity(Supplier<T> connexionBuilder, Consumer<PoolEntity<T>> askRecycling) {
        id = ID_GENERATOR.getAndIncrement();
        connexion = connexionBuilder.get();
        state = State.IDLE;
        recycler = askRecycling;

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

    public static <T extends Connection> PoolEntity<T> build(Supplier<T> connexionBuilder,
                                                             Consumer<PoolEntity<T>> askRecycling) {
        return new PoolEntity<>(connexionBuilder, askRecycling);
    }

}