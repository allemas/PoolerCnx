package com.allemas.pool;

public record PoolConfig(
        int initIdleConnexions, int maxSize, int acquireTimeout, int initialScanDelay, int scanEvery,
        int initialRecycleDelay, int recycleEvery
) {
    public PoolConfig {
        if (initIdleConnexions > maxSize) {
            throw new IllegalArgumentException("min connexion size could not be greater than max size");
        }
    }


    public static PoolConfig auto() {
        return new PoolConfig(2, 4, 200, 100, 500, 500, 800);
    }

    @Override
    public String toString() {
        return "PoolConfig{initIdleConnexions=" + initIdleConnexions + ", maxSize=" + maxSize + ", acquireTimeout=" + acquireTimeout + "}";
    }
};