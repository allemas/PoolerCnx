package com.allemas.pool;

import java.sql.Connection;
import java.util.*;
import java.util.function.Supplier;

public class DefaultPool<T extends Connection> implements Pool<T> {
    final private PoolConfig poolConfig;
    private List<PoolEntity<T>> pool;
    private Supplier<T> cnxSupplier;

    private int activesCnx;

    public DefaultPool(PoolConfig config, Supplier<T> connexionBuilder) {
        poolConfig = config;
        cnxSupplier = connexionBuilder;
        pool = new ArrayList<>();
        initPool();
    }

    private void initPool() {
        for (int i = 0; i < this.poolConfig.initIdleConnexions(); i++) {
            pool.add(new PoolEntity<>(cnxSupplier, this::recycle));
        }
    }

    private PoolEntity<T> buildNewPoolEntity() {
        if (pool.size() >= poolConfig.maxSize())
            throw new IllegalStateConnexionException("Pool max size exceeded");

        PoolEntity<T> poolEntity = new PoolEntity<>(cnxSupplier, this::recycle);
        pool.add(poolEntity);
        return poolEntity;
    }

    @Override
    public PoolEntity<T> acquire() {
        var cnx = this.pool.stream()
                .filter(PoolEntity::isIdle)
                .findFirst()
                .orElseGet(this::buildNewPoolEntity);
        cnx.markUsed();
        activesCnx++;

        return cnx;
    }

    @Override
    public int size() {
        return pool.size();
    }

    public int activeConnections() {
        return activesCnx;
    }

    private void recycle(PoolEntity<T> entity) {
        if (entity == null)
            return;
        activesCnx--;
    }
}
