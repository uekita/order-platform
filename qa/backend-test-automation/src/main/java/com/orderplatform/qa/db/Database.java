package com.orderplatform.qa.db;

import com.orderplatform.qa.config.TestConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Read-only database access. Tests verify state; they never mutate the SUT
 * database, which keeps staging safe and avoids cross-test interference.
 */
public final class Database implements AutoCloseable {

    private final TestConfig config;
    private final HikariDataSource dataSource;

    public Database(TestConfig config) {
        this.config = config;
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("qa-db-" + config.environment().id());
        hikari.setJdbcUrl(config.dbUrl());
        hikari.setUsername(config.dbUsername());
        hikari.setPassword(config.dbPassword());
        hikari.setMaximumPoolSize(config.dbPoolSize());
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(config.dbConnectionTimeoutMs());
        hikari.setReadOnly(true);
        hikari.setAutoCommit(true);
        this.dataSource = new HikariDataSource(hikari);
    }

    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... parameters) {
        List<T> results = query(sql, mapper, parameters);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... parameters) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapper.map(resultSet));
                }
                return results;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Database query failed: " + sql, exception);
        }
    }

    public long count(String sql, Object... parameters) {
        return queryOne(sql, resultSet -> resultSet.getLong(1), parameters).orElse(0L);
    }

    public void assertReachable() {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(5)) {
                throw new IllegalStateException("Database connection is not valid: " + config.redactedDbUrl());
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Cannot reach the test database at " + config.redactedDbUrl()
                            + ". Start the local stack with ./scripts/local-stack-up.sh "
                            + "or check the DB_* environment variables.", exception);
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
