package com.allemas.pool;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.function.Supplier;

public class AcquireAndCloseJDBCPooledCnx {

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
    public void createRealConnexionAndTryIncentive() throws SQLException {
        Connection connection = h2Supplier().get();
        connection.close();

        Assertions.assertTrue(connection.isClosed());
    }

    @Test
    public void tryAcquireCloseAndCheckState() throws SQLException {
        DefaultPool<Connection> pooler = new DefaultPool<>(
                new PoolConfig(1, 1, 200, 100, 500, 500, 800)
                , h2Supplier());

        PooledEntity<Connection> cnx = pooler.acquire();
        Assertions.assertNotNull(cnx);

        Assertions.assertFalse(cnx.getConnexion().isClosed());
        Assertions.assertEquals(State.ACQUIRED, cnx.getState());
        Assertions.assertEquals(pooler.acquiredConnexions(), 1);

        cnx.getConnexion().close();
        pooler.scan();
        Assertions.assertTrue(cnx.getConnexion().isClosed());
        Assertions.assertEquals(State.CLOSED, cnx.getState());
        Assertions.assertEquals(pooler.acquiredConnexions(), 0);
    }

    @Test
    public void tryAcquireCloseAndCheckStateAsync() throws SQLException, InterruptedException {
        DefaultPool<Connection> pooler = new DefaultPool<>(
                new PoolConfig(1, 1, 200, 100, 500, 500, 800)
                , h2Supplier());

        PooledEntity<Connection> cnx = pooler.acquire();
        Assertions.assertNotNull(cnx);

        Assertions.assertFalse(cnx.getConnexion().isClosed());
        Assertions.assertEquals(pooler.acquiredConnexions(), 1);
        Assertions.assertEquals(State.ACQUIRED, cnx.getState());

        cnx.getConnexion().close();
        Thread.sleep(100); // wait async vacuum

        Assertions.assertTrue(cnx.getConnexion().isClosed());
        Assertions.assertEquals(State.CLOSED, cnx.getState());
        Assertions.assertEquals(pooler.acquiredConnexions(), 0);
    }

    @Test
    public void tryAcquireCloseAndCheckClosedState() throws SQLException, InterruptedException {
        DefaultPool<Connection> pooler = new DefaultPool<>(
                new PoolConfig(1, 1, 200, 100, 500, 500, 800)
                , h2Supplier());
        PooledEntity<Connection> cnx = pooler.acquire();
        Assertions.assertNotNull(cnx);
        Assertions.assertFalse(cnx.getConnexion().isClosed());
        Assertions.assertEquals(pooler.acquiredConnexions(), 1);
        Assertions.assertEquals(State.ACQUIRED, cnx.getState());
        cnx.getConnexion().close();

        Thread.sleep(100);
        Assertions.assertTrue(cnx.getConnexion().isClosed());
        Assertions.assertEquals(State.CLOSED, cnx.getState());
        Assertions.assertEquals(pooler.acquiredConnexions(), 0);

        Assertions.assertThrows(IllegalStateConnexionException.class, () -> pooler.acquire());

        Thread.sleep(900);
        Assertions.assertDoesNotThrow(() -> pooler.acquire());

    }

}
