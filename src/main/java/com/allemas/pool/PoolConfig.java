package com.allemas.pool;

public record PoolConfig(
        int initIdleConnexions, int maxSize, int acquireTimeout
){
    public PoolConfig{
        if (initIdleConnexions > maxSize){
            throw new IllegalArgumentException("min connexion size could not be greater than max size");
        }
    }

    public static PoolConfig auto(){
        return new PoolConfig(2,4,200);
    }

    @Override
    public String toString() {
        return "PoolConfig{initIdleConnexions=" + initIdleConnexions + ", maxSize=" + maxSize + ", acquireTimeout=" + acquireTimeout+"}";
    }
};