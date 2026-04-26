package com.poolercnx.model;

/**
 * Result of executing a single SQL query through the pool.
 *
 * @param query          the SQL statement that was executed
 * @param result         textual summary of what the query returned
 * @param durationMillis wall-clock time in milliseconds for the round-trip
 * @param connectionId   internal HikariCP connection identifier (for tracing)
 */
public record QueryResult(
        String query,
        String result,
        long durationMillis,
        String connectionId
) {}
