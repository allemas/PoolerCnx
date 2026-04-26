package com.poolercnx.controller;

import com.poolercnx.model.PoolStatus;
import com.poolercnx.model.QueryResult;
import com.poolercnx.service.QueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * REST API that exposes pool behaviour and triggers for observability demos.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET  /api/query}          – execute a whitelisted SQL statement</li>
 *   <li>{@code GET  /api/pool/status}    – current HikariCP pool counters</li>
 *   <li>{@code POST /api/load/start}     – begin concurrent connection load</li>
 *   <li>{@code POST /api/load/stop}      – stop the running load</li>
 *   <li>{@code GET  /api/load/status}    – whether a load is running</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class PoolController {

    /**
     * SQL statements accepted by the {@code /api/query} endpoint.
     *
     * <p>Restricting execution to a fixed set of read-only, parameterless
     * statements prevents SQL injection and limits the blast radius of the
     * demo endpoint to safe, predictable operations.</p>
     */
    public static final Set<String> ALLOWED_QUERIES = Set.of(
            "SELECT 1",
            "SELECT NOW()",
            "SELECT version()",
            "SELECT current_database()"
    );

    private final QueryService queryService;

    public PoolController(QueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Execute a whitelisted SQL query through the pool.
     *
     * <p>Accepted values: {@code SELECT 1}, {@code SELECT NOW()},
     * {@code SELECT version()}, {@code SELECT current_database()}.
     * Any other value returns {@code 400 Bad Request}.</p>
     *
     * @param sql the statement to run (defaults to {@code SELECT 1})
     */
    @GetMapping("/query")
    public ResponseEntity<?> query(
            @RequestParam(defaultValue = "SELECT 1") String sql) {
        if (!ALLOWED_QUERIES.contains(sql)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Query not allowed. Permitted values: " + ALLOWED_QUERIES));
        }
        QueryResult result = queryService.executeQuery(sql);
        return ResponseEntity.ok(result);
    }

    /**
     * Return the current HikariCP pool status (active, idle, pending, …).
     */
    @GetMapping("/pool/status")
    public ResponseEntity<PoolStatus> poolStatus() {
        return ResponseEntity.ok(queryService.getPoolStatus());
    }

    /**
     * Start a load simulation to observe pool saturation.
     *
     * <p>Request body (all fields optional):</p>
     * <pre>{
     *   "concurrency": 10,   // number of concurrent workers (default 5)
     *   "holdMs": 2000       // ms each worker holds a connection (default 1000)
     * }</pre>
     */
    @PostMapping("/load/start")
    public ResponseEntity<Map<String, Object>> startLoad(
            @RequestBody(required = false) Map<String, Object> params) {

        int concurrency = params != null
                ? (int) params.getOrDefault("concurrency", 5)
                : 5;
        long holdMs = params != null
                ? ((Number) params.getOrDefault("holdMs", 1000)).longValue()
                : 1000L;

        queryService.startLoad(concurrency, holdMs);
        return ResponseEntity.ok(Map.of(
                "status", "started",
                "concurrency", concurrency,
                "holdMs", holdMs
        ));
    }

    /**
     * Stop the running load simulation.
     */
    @PostMapping("/load/stop")
    public ResponseEntity<Map<String, String>> stopLoad() {
        queryService.stopLoad();
        return ResponseEntity.ok(Map.of("status", "stopped"));
    }

    /**
     * Check whether a load simulation is currently active.
     */
    @GetMapping("/load/status")
    public ResponseEntity<Map<String, Object>> loadStatus() {
        return ResponseEntity.ok(Map.of(
                "running", queryService.isLoadRunning()
        ));
    }
}
