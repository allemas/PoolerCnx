package com.allemas.pool;

import org.h2.jdbc.JdbcSQLNonTransientException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class ConnectionConcurrencySpecs {


    private static Logger log = LoggerFactory.getLogger(ConnectionConcurrencySpecs.class);

    private static Supplier<Connection> h2Supplier() {
        return () -> {
            try {
                return DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;TRACE_LEVEL_SYSTEM_OUT=2");
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create H2 connection", e);
            }
        };
    }


    private static Thread createThread(Statement st, CountDownLatch countDownLatch, AtomicReference<Exception> ex,
                                       AtomicLong timeElapsed) {
        return new Thread(() -> {
            Long elapsed = null;
            try {
                countDownLatch.await();
                elapsed = System.currentTimeMillis();

                log.info("Thread {} - BEFORE executeQuery: {}",
                        Thread.currentThread().getName(), System.currentTimeMillis());

                String query = "SELECT X, COUNT(*), RANDOM() FROM SYSTEM_RANGE(1, 10000000) " +
                        "GROUP BY X ORDER BY X DESC";
                ResultSet rs = st.executeQuery(query);
                log.info("Thread {} - AFTER executeQuery: {}",
                        Thread.currentThread().getName(), System.currentTimeMillis());

                long count = 0;
                log.info("Thread {} - BEFORE rs.next()", Thread.currentThread().getName());
                while (rs.next()) {
                    count++;
                }
                log.info("Thread {} - AFTER rs.next()", Thread.currentThread().getName());

                log.info("Thread read " + count + " rows");
            } catch (SQLException e) {
                log.error("Thread SQLException ", e);
                ex.set(e);
            } catch (InterruptedException e) {
                log.error("Error InterruptedException while executing query Thread 1: " + e.getMessage());
                ex.set(e);
            } finally {
                timeElapsed.set((System.currentTimeMillis() - elapsed));
            }
        });
    }


    /**
     * Demonstrates that sharing a JDBC Statement across threads breaks
     * predictably. Each new executeQuery on a Statement implicitly closes
     * the previous ResultSet (per JDBC spec).
     * <p>
     * Two threads share one Statement and run executeQuery concurrently.
     * The second executeQuery invalidates the first thread's ResultSet,
     * causing it to crash with "object is already closed" on the next
     * read. The other thread, holding the most recent ResultSet, succeeds.
     * Which thread crashes depends on scheduling — a textbook race condition.
     * <p>
     * Confirms that connections AND statements must be thread-confined.
     * <p>
     * See also: java.sql.Statement Javadoc — "All execution methods in the
     * Statement interface implicitly close a current ResultSet object of
     * the statement if an open one exists."
     * https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/java/sql/Statement.html
     */
    @Test
    public void concurrent_use_of_same_connection_and_statement_throws() throws Exception {
        DefaultPool<Connection> pool = new DefaultPool<>(new PoolConfig(1, 4, 200), h2Supplier());
        PooledEntity<Connection> entity = pool.acquire();
        log.info("Thread - Create thread and executes queries with the SAME statement");
        Statement st = entity.getConnexion().createStatement();
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Exception> ex = new AtomicReference<>();

        Thread a = createThread(st, start, ex, null);
        Thread b = createThread(st, start, ex, null);
        a.start();
        b.start();
        start.countDown();

        a.join();
        b.join();
        st.close();

        Exception c = ex.get();
        log.error(c.getMessage(), c);

        Assertions.assertNotNull(c);
        Assertions.assertEquals(ex.get().getClass(), JdbcSQLNonTransientException.class);


    }


    @Test
    public void concurrent_use_of_same_connection_with_dedicated_statement() throws Exception {
        DefaultPool<Connection> pool = new DefaultPool<>(new PoolConfig(1, 4, 200), h2Supplier());
        PooledEntity<Connection> entity = pool.acquire();
        CountDownLatch start = new CountDownLatch(1);

        AtomicReference<Exception> ex = new AtomicReference<>();
        AtomicLong timeElapsed1 = new AtomicLong();
        AtomicLong timeElapsed2 = new AtomicLong();

        log.info("Thread - create threads with dedicated statement");
        Thread a = createThread(entity.getConnexion().createStatement(), start, ex, timeElapsed1);
        Thread b = createThread(entity.getConnexion().createStatement(), start, ex, timeElapsed2);

        a.start();
        b.start();
        start.countDown();

        a.join();
        b.join();

        log.info("Thread 1 took {}ms, Thread 2 took {}ms", timeElapsed1.get(), timeElapsed2.get());
        Assertions.assertTrue(timeElapsed2.get() > timeElapsed1.get());

    }


    @Test
    public void concurrent_use_of_same_connection_with_dedicated_pooled_connexion() throws Exception {
        DefaultPool<Connection> pool = new DefaultPool<>(new PoolConfig(2, 4, 200), h2Supplier());
        CountDownLatch start = new CountDownLatch(1);

        AtomicReference<Exception> exception1 = new AtomicReference<>();
        AtomicReference<Exception> exception2 = new AtomicReference<>();
        PooledEntity<Connection> entity = pool.acquire();
        AtomicLong timeElapsed1 = new AtomicLong();
        AtomicLong timeElapsed2 = new AtomicLong();

        Thread a = createThread(entity.getConnexion().createStatement(), start, exception1, timeElapsed1);

        PooledEntity<Connection> entity2 = pool.acquire();
        Thread b = createThread(entity2.getConnexion().createStatement(), start, exception2, timeElapsed2);

        a.start();
        b.start();
        start.countDown();

        a.join();
        b.join();

        log.info("Thread 1 took {}ms, Thread 2 took {}ms", timeElapsed1.get(), timeElapsed2.get());
        Assertions.assertTrue(timeElapsed2.get() > timeElapsed1.get());


    }
}
