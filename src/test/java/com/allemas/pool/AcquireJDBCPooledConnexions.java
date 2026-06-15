package com.allemas.pool;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.function.Supplier;

public class AcquireJDBCPooledConnexions {

    private static Supplier<Connection> h2Supplier() {
        return () -> {
            try {
                return DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create H2 connection", e);
            }
        };
    }


    @Test
    /**
     * Usage: always wrap acquire() in try-with-resources on the PoolEntity,
     * NOT on the Connection itself. Calling close() on the underlying Connection
     * will permanently destroy it — use entity.close() (or release()) instead
     * to return it to the pool.
     */
    public void acquireSimpleConnexionFromPool() throws SQLException, InterruptedException {
        DefaultPool<Connection> pooler = new DefaultPool<>(PoolConfig.auto(), h2Supplier());

        try (PoolEntity<Connection> entity = pooler.acquire()) {
            entity.getConnexion()
                    .createStatement()
                    .execute("CREATE TABLE IF NOT EXISTS test(id INT PRIMARY KEY, name VARCHAR(255))");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void tryAcquireTwice() throws SQLException, InterruptedException {
        DefaultPool<Connection> pooler = new DefaultPool<>(
                new PoolConfig(1, 1, 200)
                , h2Supplier());
        pooler.acquire();
        Assertions.assertThrows(IllegalStateConnexionException.class, pooler::acquire);
    }


}
