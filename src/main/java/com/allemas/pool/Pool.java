package com.allemas.pool;

import java.sql.Connection;

public interface Pool<T extends Connection> {
    T acquire() throws InterruptedException;
    int size();
}
