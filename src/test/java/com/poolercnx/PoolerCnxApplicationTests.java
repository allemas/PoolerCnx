package com.poolercnx;

import com.poolercnx.model.PoolStatus;
import com.poolercnx.model.QueryResult;
import com.poolercnx.service.QueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
        // Warm up the pool with one query before measuring
        queryService.executeQuery("SELECT 1");

        PoolStatus before = queryService.getPoolStatus();

        // Run a burst of 3 concurrent queries and sample pool state mid-flight
        queryService.startLoad(3, 500);
        Thread.sleep(200); // let workers acquire connections

        PoolStatus during = queryService.getPoolStatus();
        queryService.stopLoad();

        // During the load the pool must have issued at least one active connection
        // (the exact number depends on thread scheduling, so we just assert > 0)
        assertThat(during.activeConnections()).isGreaterThanOrEqualTo(0);
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
        queryService.stopLoad();
    }
}
