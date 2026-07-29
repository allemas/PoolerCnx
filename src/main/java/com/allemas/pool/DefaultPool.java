package com.allemas.pool;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class DefaultPool<T extends Connection> implements Pool<T> {
    final private PoolConfig poolConfig;
    private List<PooledEntity<T>> pool;
    private Supplier<T> cnxSupplier;

    private int createdCnx = 0;

    public DefaultPool(PoolConfig config, Supplier<T> connexionBuilder) {
        poolConfig = config;
        cnxSupplier = connexionBuilder;
        pool = new ArrayList<>();
        initPool();

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                this.scan();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, config.initialScanDelay(), config.scanEvery(), TimeUnit.MILLISECONDS);

        scheduler.scheduleAtFixedRate(this::recycle, config.initialRecycleDelay(), config.recycleEvery(), TimeUnit.MILLISECONDS);


    }

    private void initPool() {
        for (int i = 0; i < this.poolConfig.initIdleConnexions(); i++) {
            pool.add(new PooledEntity<>(this.getCnxIdentity(), cnxSupplier));
        }
    }

    @Override
    public PooledEntity<T> acquire() {
        PooledEntity<T> cnx = this.pool.stream()
                .filter(PooledEntity::isIdle)
                .findFirst()
                .orElseGet(() -> {
                    if (pool.size() >= poolConfig.maxSize())
                        throw new IllegalStateConnexionException("Pool max size exceeded or should be recycled");

                    PooledEntity<T> poolEntity = PooledEntity.build(this.getCnxIdentity(), cnxSupplier);
                    pool.add(poolEntity);
                    return poolEntity;
                });

        cnx.markUsed();
        return cnx;
    }

    @Override
    public int size() {
        return pool.size();
    }

    private int getCnxIdentity() {
        createdCnx++;
        return createdCnx;
    }

    public int acquiredConnexions() {
        return pool.stream().filter(e -> {
            return e.getState().equals(State.ACQUIRED);
        }).toList().size();
    }

    public void scan() throws SQLException {
        for (PooledEntity<T> cnx : pool) {
            if (cnx.getConnexion().isClosed()) {
                cnx.markClosed();
            }
        }
    }

    public void recycle() {
        List<PooledEntity<T>> canBeNuked = new ArrayList<>();

        for (PooledEntity<T> cnx : pool) {
            if (cnx.isClosed()) {
                canBeNuked.add(cnx); // needs traverse the whole List struct
            }
        }
        pool.removeAll(canBeNuked);

        if (pool.size() < this.poolConfig.initIdleConnexions()) {
            int shouldCreated = this.poolConfig.initIdleConnexions() - pool.size();
            for (int i = 0; i < shouldCreated; i++) {
                pool.add(new PooledEntity<>(this.getCnxIdentity(), cnxSupplier));
            }
        }
    }
}
