package com.poolercnx.metrics;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Registers additional Micrometer {@link Gauge}s that surface HikariCP pool
 * statistics as Prometheus metrics.
 *
 * <p>HikariCP already integrates with Micrometer via
 * {@code HikariConfig#setMetricRegistry}, which publishes metrics under the
 * {@code hikaricp.*} namespace.  This class adds higher-level gauges that are
 * easier to alert on:</p>
 *
 * <ul>
 *   <li>{@code poolercnx.pool.utilization} – fraction of the pool that is
 *       actively in use (0.0 – 1.0).  A sustained value near 1.0 means the
 *       pool is saturated and callers will start queueing.</li>
 *   <li>{@code poolercnx.pool.active}   – active connection count</li>
 *   <li>{@code poolercnx.pool.idle}     – idle connection count</li>
 *   <li>{@code poolercnx.pool.pending}  – threads waiting for a connection</li>
 *   <li>{@code poolercnx.pool.total}    – total connections in the pool</li>
 * </ul>
 */
@Component
public class PoolMetrics {

    private final DataSource dataSource;
    private final MeterRegistry meterRegistry;

    public PoolMetrics(DataSource dataSource, MeterRegistry meterRegistry) {
        this.dataSource = dataSource;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void registerMetrics() {
        if (!(dataSource instanceof HikariDataSource hikari)) {
            return;
        }

        Gauge.builder("poolercnx.pool.active", hikari, ds -> {
                    HikariPoolMXBean pool = ds.getHikariPoolMXBean();
                    return pool != null ? pool.getActiveConnections() : 0;
                })
                .description("Number of connections currently in use")
                .tag("pool", hikari.getPoolName())
                .register(meterRegistry);

        Gauge.builder("poolercnx.pool.idle", hikari, ds -> {
                    HikariPoolMXBean pool = ds.getHikariPoolMXBean();
                    return pool != null ? pool.getIdleConnections() : 0;
                })
                .description("Number of connections sitting idle in the pool")
                .tag("pool", hikari.getPoolName())
                .register(meterRegistry);

        Gauge.builder("poolercnx.pool.pending", hikari, ds -> {
                    HikariPoolMXBean pool = ds.getHikariPoolMXBean();
                    return pool != null ? pool.getThreadsAwaitingConnection() : 0;
                })
                .description("Number of threads waiting to acquire a connection")
                .tag("pool", hikari.getPoolName())
                .register(meterRegistry);

        Gauge.builder("poolercnx.pool.total", hikari, ds -> {
                    HikariPoolMXBean pool = ds.getHikariPoolMXBean();
                    return pool != null ? pool.getTotalConnections() : 0;
                })
                .description("Total number of connections managed by the pool")
                .tag("pool", hikari.getPoolName())
                .register(meterRegistry);

        Gauge.builder("poolercnx.pool.utilization", hikari, ds -> {
                    HikariPoolMXBean pool = ds.getHikariPoolMXBean();
                    if (pool == null || ds.getMaximumPoolSize() == 0) {
                        return 0.0;
                    }
                    return (double) pool.getActiveConnections() / ds.getMaximumPoolSize();
                })
                .description("Pool utilization ratio: active / maximumPoolSize (0.0 – 1.0)")
                .tag("pool", hikari.getPoolName())
                .register(meterRegistry);
    }
}
