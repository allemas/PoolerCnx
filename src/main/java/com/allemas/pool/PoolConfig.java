package com.allemas.pool;

public record PoolConfig(
        int minSize, int maxSize, int acquireTimeout
){

    public static PoolConfig auto(){
        return new PoolConfig(2,4,200);
    }

    @Override
    public String toString() {
        return "PoolConfig{minSize=" + minSize + ", maxSize=" + maxSize + ", acquireTimeout=" + acquireTimeout+"}";
    }
};