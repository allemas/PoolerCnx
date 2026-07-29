package com.allemas.pool;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class PoolSpecs extends PoolConfigurationTools {

    private static final Logger log = LoggerFactory.getLogger(PoolSpecs.class);

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

        Assertions.assertEquals(0, pool.acquiredConnexions());
        pool.acquire();
        Assertions.assertEquals(1, pool.acquiredConnexions());
        pool.acquire();
        Assertions.assertEquals(2, pool.acquiredConnexions());

        // should be not possible 3 > 2 (max pool size)
        Assertions.assertThrows(IllegalStateConnexionException.class, pool::acquire);
    }

    @Test
    public void verifyConnectionAcquiredAndAddedToPool() throws InterruptedException {
        AtomicInteger atomicInteger = new AtomicInteger(0);
        DefaultPool<StubCnx> pool = new DefaultPool<>(new PoolConfig(1, 3, 200), () -> new StubCnx(atomicInteger));
        Assertions.assertEquals(1, atomicInteger.get());
        Assertions.assertEquals(0, pool.acquiredConnexions());

        pool.acquire();
        Assertions.assertEquals(1, atomicInteger.get());
        Assertions.assertEquals(1, pool.acquiredConnexions());

        pool.acquire();
        Assertions.assertEquals(2, atomicInteger.get());
        Assertions.assertEquals(2, pool.acquiredConnexions());

        pool.acquire();
        Assertions.assertEquals(3, atomicInteger.get());
        Assertions.assertEquals(3, pool.acquiredConnexions());

        Assertions.assertThrows(IllegalStateConnexionException.class, pool::acquire);
    }

    @Test
    /**
     * Validates the full lifecycle of a saturated pool with maxSize=1:
     * acquire fills the pool, a second acquire throws, close releases
     * the entity, and re-acquiring returns the same recycled instance.
     * The pool is then saturated again, confirming the cycle is repeatable.
     */
    public void verifyConnectionAcquiredAndRemovedFromPool() throws Exception {
        DefaultPool<Connection> pool = new DefaultPool<>(new PoolConfig(1, 1, 200), () -> {
            try {
                return DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        Assertions.assertEquals(0, pool.acquiredConnexions());

        var cnx = pool.acquire();
        Assertions.assertThrows(IllegalStateConnexionException.class, pool::acquire);

        cnx.close();
        Assertions.assertEquals(0, pool.acquiredConnexions());
        var cnx2 = pool.acquire();
        Assertions.assertEquals(1, pool.acquiredConnexions());
        Assertions.assertEquals(cnx.getId(), cnx2.getId());
        Assertions.assertThrows(IllegalStateConnexionException.class, pool::acquire);
    }

    @Test
    public void tryAcquireConnectionMultiThreaded() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        DefaultPool<Connection> pool = new DefaultPool<>(new PoolConfig(1, 1, 200), h2Supplier());

        Thread thread1 = new Thread(() -> {
            try {
                countDownLatch.await();
                PooledEntity<Connection> e = pool.acquire();
                log.info("Acquired connection {}", e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        Thread thread2 = new Thread(() -> {
            try {
                countDownLatch.await();
                PooledEntity<Connection> e = pool.acquire();
                log.info("Acquired connection {}", e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });


        thread1.start();
        thread2.start();
        countDownLatch.countDown();
    }


}
