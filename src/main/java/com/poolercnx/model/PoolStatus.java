package com.poolercnx.model;

/**
 * Snapshot of the HikariCP connection pool state at a point in time.
 *
 * <p>Returned by the REST API so consumers can observe pool behaviour without
 * connecting to a metrics back-end.</p>
 *
 * @param poolName        name of the HikariCP pool
 * @param totalConnections total number of connections (active + idle + pending)
 * @param activeConnections connections currently in use by application threads
 * @param idleConnections   connections sitting idle and ready to be borrowed
 * @param pendingThreads    threads waiting to acquire a connection
 * @param maximumPoolSize   configured upper bound on pool size
 * @param minimumIdle       configured lower bound on idle connections
 */
public record PoolStatus(
        String poolName,
        int totalConnections,
        int activeConnections,
        int idleConnections,
        int pendingThreads,
        int maximumPoolSize,
        int minimumIdle
) {}
