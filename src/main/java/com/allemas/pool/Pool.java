package com.allemas.pool;

import java.sql.Connection;

public interface Pool<T extends Connection> {
    PooledEntity<T> acquire();
    int size();
}
