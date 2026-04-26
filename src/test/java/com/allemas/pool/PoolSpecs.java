package com.allemas.pool;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

public class PoolSpecs {

    @Test
    public void verifyInstantiationConnexion() {
        AtomicInteger atomicInteger = new AtomicInteger(0);
        DefaultPool<StubCnx> pool = new DefaultPool<>(new PoolConfig(5, 20, 200), () -> new StubCnx(atomicInteger));

        Assertions.assertEquals(5, atomicInteger.get());
        Assertions.assertEquals(atomicInteger.get(), pool.size());
    }

    @Test
    public void verifyConnectionAcquired() throws InterruptedException {
        AtomicInteger atomicInteger = new AtomicInteger(0);
        DefaultPool<StubCnx> pool = new DefaultPool<>(new PoolConfig(1, 2, 200), () -> new StubCnx(atomicInteger));

        Assertions.assertEquals(0, pool.activeConnections());
        pool.acquire();
        Assertions.assertEquals(1, pool.activeConnections());
        pool.acquire();
        Assertions.assertEquals(2, pool.activeConnections());
        // should be not possible 3 > 2 (max pool size)
        Assertions.assertThrows(IllegalAcquireException.class, pool::acquire);
    }

    @Test
    public void verifyConnectionAcquiredAndAddedToPool() throws InterruptedException {
        AtomicInteger atomicInteger = new AtomicInteger(0);
        DefaultPool<StubCnx> pool = new DefaultPool<>(new PoolConfig(1, 3, 200), () -> new StubCnx(atomicInteger));
        Assertions.assertEquals(1, atomicInteger.get());
        Assertions.assertEquals(0, pool.activeConnections());

        pool.acquire();
        Assertions.assertEquals(1, atomicInteger.get());
        Assertions.assertEquals(1, pool.activeConnections());

        pool.acquire();
        Assertions.assertEquals(2, atomicInteger.get());
        Assertions.assertEquals(2, pool.activeConnections());

        pool.acquire();
        Assertions.assertEquals(3, atomicInteger.get());
        Assertions.assertEquals(3, pool.activeConnections());

        Assertions.assertThrows(IllegalAcquireException.class, pool::acquire);
    }

    @Test
    public void verifyConnectionAcquiredAndRemovedFromPool() throws InterruptedException, SQLException {
        DefaultPool<Connection> pool = new DefaultPool<>(new PoolConfig(1, 1, 200), () ->
        {
            try {
                return DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        Assertions.assertEquals(0, pool.activeConnections());

        var cnx = pool.acquire();
        Assertions.assertThrows(IllegalAcquireException.class, pool::acquire);

        try {
            cnx.close();
        } catch (Exception e) {

        }
        Assertions.assertEquals(0, pool.activeConnections());

    }


}
