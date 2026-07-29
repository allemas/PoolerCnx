package com.allemas.pool;

import java.sql.Connection;
import java.util.*;
import java.util.function.Supplier;

public class DefaultPool<T extends Connection> implements Pool<T> {
    final private PoolConfig poolConfig;
    private List<PooledEntity<T>> pool;
    private Supplier<T> cnxSupplier;

    private int activesCnx;
    private int createdCnx = 0;

    public DefaultPool(PoolConfig config, Supplier<T> connexionBuilder) {
        poolConfig = config;
        cnxSupplier = connexionBuilder;
        pool = new ArrayList<>();
        initPool();
    }

    private void initPool() {
        for (int i = 0; i < this.poolConfig.initIdleConnexions(); i++) {
            pool.add(new PooledEntity<>(this.getCnxIdentity(), cnxSupplier, this::recycle));
        }
    }

    private PooledEntity<T> buildNewPoolEntity() {
        if (pool.size() >= poolConfig.maxSize())
            throw new IllegalStateConnexionException("Pool max size exceeded");

        PooledEntity<T> poolEntity = PooledEntity.build(this.getCnxIdentity(), cnxSupplier, this::recycle);
        pool.add(poolEntity);
        return poolEntity;
    }

    @Override
    public PooledEntity<T> acquire() {
        var cnx = this.pool.stream()
                .filter(PooledEntity::isIdle)
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

    private void recycle(PooledEntity<T> entity) {
        if (entity == null)
            return;
        activesCnx--;
    }

    private int getCnxIdentity() {
        createdCnx++;
        return createdCnx;
    }

}
