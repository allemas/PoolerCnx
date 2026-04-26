package com.allemas.pool;




import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultPool<T extends Connection> implements Pool<T> {

    private final static Logger LOGGER = Logger.getLogger(DefaultPool.class.getName());

    final private PoolConfig poolConfig;
    private List<PooledConnexion<T>> pool;
    private Supplier<T> cnxSupplier;

    private ScheduledExecutorService executor;

    public DefaultPool(PoolConfig config, Supplier<T> connexionBuilder) {
        poolConfig = config;
        cnxSupplier = connexionBuilder;
        pool = new ArrayList<>();
        executor = Executors.newScheduledThreadPool(1);
        initPool();
        setKeepAlive();
    }

    private void setKeepAlive() {
        this.executor
                .scheduleAtFixedRate(
                        this::keepAliveChecking, 0, 100, TimeUnit.MILLISECONDS);
    }

    private void initPool() {
        int minSize = this.poolConfig.minSize();
        for (int i = 0; i < minSize; i++) {
            pool.add(new PooledConnexion<>(cnxSupplier));
        }
    }

    @Override
    public T acquire() throws InterruptedException {
        var cnx = this.pool.stream().filter(connexion -> !connexion.isClosed() && !connexion.isActive()).findFirst().orElseThrow(IllegalAcquire::new);
        cnx.setActive();
        return cnx.getConnexion();
    }

    @Override
    public int size() {
        return pool.size();
    }


    public Runnable keepAliveChecking() {
        return () -> {
            LOGGER.info("message to log RUNNING");
            pool.stream().forEach(connexion -> {
                try {
                    if (connexion.getConnexion().isClosed()) {
                        this.recycle(connexion);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        };
    }

    public void recycle(PooledConnexion<T> connexion) {
        pool.remove(connexion);
    }

}
