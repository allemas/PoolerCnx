package com.allemas.pool;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class AcquireJDBCPooledConnexions {


    @Test
    public void acquireSimpleConnexionFromPool() throws SQLException, InterruptedException {
        DefaultPool<Connection> pooler = new DefaultPool<>(PoolConfig.auto(), () -> {
            try {
                return DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

        try (Connection connection = pooler.acquire()) {
            connection.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS test(id INT PRIMARY KEY, name VARCHAR(255))"
            );
        }
    }

    @Test
    public void tryAcquireTwice() throws SQLException, InterruptedException {
        DefaultPool<Connection> pooler = new DefaultPool<>(
                new PoolConfig(1, 1, 200)
                , () -> {
            try {
                return DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(1000);

        pooler.acquire();
        Assertions.assertThrows(IllegalAcquire.class, pooler::acquire);
    }


}
