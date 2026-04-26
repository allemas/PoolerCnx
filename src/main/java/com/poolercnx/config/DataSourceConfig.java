package com.poolercnx.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * HikariCP connection pool configuration.
 *
 * <p>HikariCP is a high-performance JDBC connection pool. It maintains a pool of
 * pre-established database connections so that application code can borrow a
 * connection from the pool, use it, and then return it — avoiding the cost of
 * creating a new physical connection for every request.</p>
 *
 * <p>Key pool parameters exposed here:</p>
 * <ul>
 *   <li><b>maximumPoolSize</b>: upper bound on the number of connections kept in
 *       the pool. Requests that arrive when all connections are busy will wait up
 *       to {@code connectionTimeout} ms before failing.</li>
 *   <li><b>minimumIdle</b>: connections HikariCP tries to keep idle and ready.
 *       Setting this equal to {@code maximumPoolSize} gives a fixed-size pool.</li>
 *   <li><b>connectionTimeout</b>: maximum time (ms) a caller will wait for a
 *       connection before a {@link java.sql.SQLTimeoutException} is thrown.</li>
 *   <li><b>idleTimeout</b>: how long a connection may sit idle before it is
 *       retired (only applies when {@code minimumIdle} &lt; {@code maximumPoolSize}).</li>
 *   <li><b>maxLifetime</b>: absolute maximum lifetime of a connection in the pool,
 *       regardless of activity. Keeps credentials and server-side state fresh.</li>
 * </ul>
 *
 * <p><b>Observability</b>: Micrometer automatically binds HikariCP metrics
 * (under the {@code hikaricp.*} namespace) to the configured {@link
 * io.micrometer.core.instrument.MeterRegistry} via Spring Boot's
 * {@code HikariDataSourceMetricsPostProcessor} autoconfiguration.
 * Additional higher-level gauges are published by {@link com.poolercnx.metrics.PoolMetrics}.</p>
 */
@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.hikari.maximum-pool-size:10}")
    private int maximumPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:2}")
    private int minimumIdle;

    @Value("${spring.datasource.hikari.connection-timeout:30000}")
    private long connectionTimeout;

    @Value("${spring.datasource.hikari.idle-timeout:600000}")
    private long idleTimeout;

    @Value("${spring.datasource.hikari.max-lifetime:1800000}")
    private long maxLifetime;

    @Value("${spring.datasource.hikari.pool-name:PoolerCnxPool}")
    private String poolName;

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);

        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);
        config.setPoolName(poolName);

        return new HikariDataSource(config);
    }
}
