package com.poolercnx;

import com.poolercnx.controller.PoolController;
import com.poolercnx.model.PoolStatus;
import com.poolercnx.model.QueryResult;
import com.poolercnx.service.QueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that verify the connection-pooling and observability
 * behaviour of PoolerCnx.
 *
 * <p>Tests run against an in-memory H2 database (configured in
 * {@code src/test/resources/application.yml}) so no external infrastructure
 * is required.</p>
 */
@SpringBootTest
class PoolerCnxApplicationTests {

    @Autowired
    private QueryService queryService;

    @AfterEach
    void cleanup() {
        if (queryService.isLoadRunning()) {
            queryService.stopLoad();
        }
    }

    // ------------------------------------------------------------------ query

    @Test
    void executeQuery_returnsResult() {
        QueryResult result = queryService.executeQuery("SELECT 1");
        assertThat(result).isNotNull();
        assertThat(result.query()).isEqualTo("SELECT 1");
        assertThat(result.result()).isNotNull();
        assertThat(result.durationMillis()).isGreaterThanOrEqualTo(0);
    }

    // --------------------------------------------------------------- pool status

    @Test
    void getPoolStatus_returnsPoolMetadata() {
        PoolStatus status = queryService.getPoolStatus();
        assertThat(status).isNotNull();
        assertThat(status.poolName()).isEqualTo("PoolerCnxTestPool");
        assertThat(status.maximumPoolSize()).isEqualTo(5);
        assertThat(status.totalConnections()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void getPoolStatus_activeConnectionsIncreaseDuringLoad() throws InterruptedException {
        // Warm up the pool so the baseline has 0 active connections
        queryService.executeQuery("SELECT 1");
        Thread.sleep(50);

        PoolStatus before = queryService.getPoolStatus();

        // Start workers that each hold a connection for 500 ms and wait
        // 250 ms (midway) for them to be in-flight before sampling.
        queryService.startLoad(3, 500);
        Thread.sleep(250);

        PoolStatus during = queryService.getPoolStatus();

        // During the load the active connection count must exceed the baseline
        assertThat(during.activeConnections()).isGreaterThan(before.activeConnections());
    }

    // ---------------------------------------------------------------- load simulation

    @Test
    void startLoad_setsRunningFlag() throws InterruptedException {
        assertThat(queryService.isLoadRunning()).isFalse();

        queryService.startLoad(2, 300);
        assertThat(queryService.isLoadRunning()).isTrue();

        queryService.stopLoad();
        assertThat(queryService.isLoadRunning()).isFalse();
    }

    @Test
    void startLoad_idempotent_whenAlreadyRunning() {
        queryService.startLoad(2, 200);
        // Second call must not throw and must not start a second executor
        queryService.startLoad(2, 200);
        assertThat(queryService.isLoadRunning()).isTrue();
    }

    // --------------------------------------------------------- query whitelist

    @Test
    void allowedQueries_containsExpectedStatements() {
        assertThat(PoolController.ALLOWED_QUERIES).contains("SELECT 1");
        assertThat(PoolController.ALLOWED_QUERIES).doesNotContain("DROP TABLE users");
    }
}
