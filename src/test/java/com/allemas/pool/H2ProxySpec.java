package com.allemas.pool;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.function.Supplier;

public class H2ProxySpec {

    private static Supplier<Connection> h2Supplier() {
        return () -> {
            try {
                return DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create H2 connection", e);
            }
        };
    }

    @Test
    public void testH2Proxy() throws Exception {
        DefaultPool<Connection> pool = new DefaultPool<>(new PoolConfig(1, 4, 200, 100, 500, 500, 800), h2Supplier());
        try (PooledEntity<Connection> entity = pool.acquire()) {

            try (Statement st = entity.getConnexion().createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(255))");
            }
        }

        try (PooledEntity<Connection> entity = pool.acquire()) {
            try (PreparedStatement ps = entity.getConnexion().prepareStatement(
                    "INSERT INTO users (id, name) VALUES (?, ?)")) {
                ps.setInt(1, 1);
                ps.setString(2, "Alice");
                ps.executeUpdate();

                ps.setInt(1, 2);
                ps.setString(2, "Bob");
                ps.executeUpdate();
            }
        }

        try (PooledEntity<Connection> entity = pool.acquire()) {
            try (Statement st = entity.getConnexion().createStatement();
                 ResultSet rs = st.executeQuery("SELECT id, name FROM users ORDER BY id")) {

                Assertions.assertTrue(rs.next(), "expected first row");
                Assertions.assertEquals(1, rs.getInt("id"));
                Assertions.assertEquals("Alice", rs.getString("name"));

                Assertions.assertTrue(rs.next(), "expected second row");
                Assertions.assertEquals(2, rs.getInt("id"));
                Assertions.assertEquals("Bob", rs.getString("name"));

                Assertions.assertFalse(rs.next(), "no more rows expected");
            }
        }


    }
}
