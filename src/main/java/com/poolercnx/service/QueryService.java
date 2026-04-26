package com.poolercnx.service;

import com.poolercnx.model.PoolStatus;
import com.poolercnx.model.QueryResult;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core service that exercises the connection pool and exposes observable behaviour.
 *
 * <p>Three main responsibilities:</p>
 * <ol>
 *   <li><b>Single query execution</b> – borrows one connection, runs a statement,
 *       records a Micrometer timer, and returns timing + connection metadata.</li>
 *   <li><b>Pool status inspection</b> – reads live HikariCP MXBean counters so
 *       the REST layer can surface them without requiring Prometheus.</li>
 *   <li><b>Load simulation</b> – spawns configurable concurrent threads that each
 *       hold a connection for a configurable duration, making it easy to visualise
 *       pool saturation in Grafana.</li>
 * </ol>
 */
@Service
public class QueryService {

    private static final Logger log = LoggerFactory.getLogger(QueryService.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final MeterRegistry meterRegistry;
    private final Timer queryTimer;

    private final AtomicBoolean loadRunning = new AtomicBoolean(false);
    private ExecutorService loadExecutor;

    public QueryService(JdbcTemplate jdbcTemplate,
                        DataSource dataSource,
                        MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.meterRegistry = meterRegistry;
        this.queryTimer = Timer.builder("poolercnx.query.duration")
                .description("Time taken to execute a query through the pool")
                .register(meterRegistry);
    }

    /**
     * Executes {@code SELECT 1} (or {@code SELECT 1 FROM DUAL} on Oracle-like
     * databases) and returns timing and connection metadata.
     */
    public QueryResult executeQuery(String sql) {
        long start = System.currentTimeMillis();
        String[] connectionId = {""};

        String result = queryTimer.record(() -> {
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                connectionId[0] = conn.toString();
                log.info("Executing query '{}' on connection {}", sql, connectionId[0]);
                return jdbcTemplate.queryForObject(sql, String.class);
            } catch (SQLException e) {
                log.error("Failed to execute query '{}': {}", sql, e.getMessage());
                throw new RuntimeException(e);
            }
        });

        long durationMs = System.currentTimeMillis() - start;
        return new QueryResult(sql, result, durationMs, connectionId[0]);
    }

    /**
     * Returns the current state of the HikariCP pool via its MXBean.
     */
    public PoolStatus getPoolStatus() {
        if (!(dataSource instanceof HikariDataSource hikari)) {
            return new PoolStatus("unknown", 0, 0, 0, 0, 0, 0);
        }

        HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
        if (pool == null) {
            return new PoolStatus(hikari.getPoolName(), 0, 0, 0, 0,
                    hikari.getMaximumPoolSize(), hikari.getMinimumIdle());
        }

        return new PoolStatus(
                hikari.getPoolName(),
                pool.getTotalConnections(),
                pool.getActiveConnections(),
                pool.getIdleConnections(),
                pool.getThreadsAwaitingConnection(),
                hikari.getMaximumPoolSize(),
                hikari.getMinimumIdle()
        );
    }

    /**
     * Starts a load-simulation that opens {@code concurrency} connections
     * simultaneously, each holding its connection for {@code holdMs} ms before
     * releasing it.  The simulation repeats until {@link #stopLoad()} is called.
     *
     * @param concurrency number of concurrent threads hammering the pool
     * @param holdMs      how long (ms) each thread holds its connection
     */
    public void startLoad(int concurrency, long holdMs) {
        if (!loadRunning.compareAndSet(false, true)) {
            log.warn("Load simulation already running – ignoring start request");
            return;
        }

        log.info("Starting load simulation: concurrency={}, holdMs={}", concurrency, holdMs);
        loadExecutor = Executors.newFixedThreadPool(concurrency);

        for (int i = 0; i < concurrency; i++) {
            final int workerId = i;
            loadExecutor.submit(() -> {
                while (loadRunning.get()) {
                    try (Connection conn = dataSource.getConnection()) {
                        log.debug("Worker {} acquired connection {}", workerId, conn);
                        meterRegistry.counter("poolercnx.load.connections.acquired").increment();
                        Thread.sleep(holdMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.warn("Worker {} failed to acquire connection: {}", workerId, e.getMessage());
                        meterRegistry.counter("poolercnx.load.connections.failed").increment();
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            });
        }
    }

    /**
     * Stops the running load simulation and waits up to 5 s for all workers
     * to finish.
     */
    public void stopLoad() {
        if (!loadRunning.compareAndSet(true, false)) {
            log.warn("No load simulation is running");
            return;
        }

        log.info("Stopping load simulation");
        if (loadExecutor != null) {
            loadExecutor.shutdownNow();
            try {
                loadExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Whether a load simulation is currently active. */
    public boolean isLoadRunning() {
        return loadRunning.get();
    }
}
