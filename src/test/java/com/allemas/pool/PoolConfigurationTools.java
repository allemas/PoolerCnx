package com.allemas.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.text.html.Option;
import java.sql.*;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class ConnectionConcurrencyTools {
    private Logger log = LoggerFactory.getLogger(ConnectionConcurrencyTools.class);

    public Supplier<Connection> h2Supplier() {
        return () -> {
            try {
                return DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;TRACE_LEVEL_SYSTEM_OUT=2");
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create H2 connection", e);
            }
        };
    }


    public Thread createThread(Statement st, CountDownLatch countDownLatch, AtomicReference<Exception> ex,
                               Optional<AtomicLong> timeElapsed) {
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
                if (timeElapsed.isPresent()) {
                    timeElapsed.get().set((System.currentTimeMillis() - elapsed));
                }
            }
        });
    }
}
