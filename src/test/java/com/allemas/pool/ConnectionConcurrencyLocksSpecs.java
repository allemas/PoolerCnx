package com.allemas.pool;

import org.h2.jdbc.JdbcSQLNonTransientException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Isolated
public class ConnectionConcurrencyLocksSpecs extends PoolConfigurationTools {

    private static Logger log = LoggerFactory.getLogger(ConnectionConcurrencyLocksSpecs.class);


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
        DefaultPool<Connection> pool = new DefaultPool<>(new PoolConfig(1, 4, 200, 100, 500, 500, 800), h2Supplier());
        try (PooledEntity<Connection> entity = pool.acquire()) {
            log.info("Thread - Create thread and executes queries with the SAME statement");
            Statement st = entity.getConnexion().createStatement();
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<Exception> ex = new AtomicReference<>();

            Thread a = createThread(st, start, ex, Optional.empty());
            Thread b = createThread(st, start, ex, Optional.empty());
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
    }


    @Test
    @ResourceLock("h2-timing-test")
    public void concurrent_use_of_same_connection_with_dedicated_statement() throws Exception {
        DefaultPool<Connection> pool = new DefaultPool<>(new PoolConfig(1, 4, 200, 100, 500, 500, 800), h2Supplier());
        PooledEntity<Connection> entity = pool.acquire();
        CountDownLatch start = new CountDownLatch(1);

        AtomicReference<Exception> ex = new AtomicReference<>();
        AtomicLong timeElapsed1 = new AtomicLong();
        AtomicLong timeElapsed2 = new AtomicLong();

        log.info("Thread - create threads with dedicated statement");
        Thread a = createThread(entity.getConnexion().createStatement(), start, ex, Optional.of(timeElapsed1));
        Thread b = createThread(entity.getConnexion().createStatement(), start, ex, Optional.of(timeElapsed2));

        a.start();
        b.start();
        start.countDown();

        a.join();
        b.join();

        log.info("Thread 1 took {}ms, Thread 2 took {}ms", timeElapsed1.get(), timeElapsed2.get());
        Assertions.assertTrue(timeElapsed2.get() > timeElapsed1.get());
    }

}
