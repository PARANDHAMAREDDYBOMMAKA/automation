package com.kalvium.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.kalvium.model.AuthConfig;

import jakarta.annotation.PostConstruct;

@Service
public class ConfigStorageService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigStorageService.class);
    private String dbPath;
    private static final String TABLE_NAME = "worklog_config";

    @PostConstruct
    public void init() {
        try {
            Path appData = Paths.get("/app/data");
            if (Files.isWritable(appData.getParent()) || Files.exists(appData)) {
                Files.createDirectories(appData);
                dbPath = "/app/data/worklog.db";
            } else {
                throw new Exception("Not writable");
            }
        } catch (Exception e) {
            dbPath = "worklog.db";
            logger.info("Using local directory for database: " + dbPath);
        }

        initDatabase();
    }

    private void initDatabase() {
        try (Connection conn = getConnection()) {
            String createTableSQL = """
                CREATE TABLE IF NOT EXISTS %s (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    auth_session_id TEXT NOT NULL,
                    keycloak_identity TEXT NOT NULL,
                    keycloak_session TEXT NOT NULL,
                    tasks_completed TEXT,
                    challenges TEXT,
                    blockers TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """.formatted(TABLE_NAME);

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSQL);
                logger.info("Database initialized at: " + dbPath);
            }
        } catch (SQLException e) {
            logger.error("Failed to initialize database", e);
        }
    }

    private Connection getConnection() throws SQLException {
        String url = "jdbc:sqlite:" + dbPath;
        return DriverManager.getConnection(url);
    }

    public void saveConfig(AuthConfig config) {
        String checkSql = "SELECT id FROM %s WHERE auth_session_id = ?".formatted(TABLE_NAME);
        String insertSql = """
            INSERT INTO %s
            (auth_session_id, keycloak_identity, keycloak_session, tasks_completed, challenges, blockers, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """.formatted(TABLE_NAME);
        String updateSql = """
            UPDATE %s
            SET keycloak_identity = ?, keycloak_session = ?, tasks_completed = ?,
                challenges = ?, blockers = ?, updated_at = CURRENT_TIMESTAMP
            WHERE auth_session_id = ?
            """.formatted(TABLE_NAME);

        try (Connection conn = getConnection()) {
            Integer existingId = null;
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, config.getAuthSessionId());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        existingId = rs.getInt("id");
                    }
                }
            }

            if (existingId != null) {
                try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                    pstmt.setString(1, config.getKeycloakIdentity());
                    pstmt.setString(2, config.getKeycloakSession());
                    pstmt.setString(3, config.getTasksCompleted());
                    pstmt.setString(4, config.getChallenges());
                    pstmt.setString(5, config.getBlockers());
                    pstmt.setString(6, config.getAuthSessionId());
                    pstmt.executeUpdate();
                    logger.info("Configuration updated for user (id: {})", existingId);
                }
            } else {
                try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                    pstmt.setString(1, config.getAuthSessionId());
                    pstmt.setString(2, config.getKeycloakIdentity());
                    pstmt.setString(3, config.getKeycloakSession());
                    pstmt.setString(4, config.getTasksCompleted());
                    pstmt.setString(5, config.getChallenges());
                    pstmt.setString(6, config.getBlockers());
                    pstmt.executeUpdate();
                    logger.info("New user configuration saved to database");
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to save configuration", e);
            throw new RuntimeException("Failed to save configuration", e);
        }
    }

    public AuthConfig loadConfig() {
        String sql = "SELECT * FROM %s ORDER BY id LIMIT 1".formatted(TABLE_NAME);

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                AuthConfig config = new AuthConfig();
                config.setAuthSessionId(rs.getString("auth_session_id"));
                config.setKeycloakIdentity(rs.getString("keycloak_identity"));
                config.setKeycloakSession(rs.getString("keycloak_session"));
                config.setTasksCompleted(rs.getString("tasks_completed"));
                config.setChallenges(rs.getString("challenges"));
                config.setBlockers(rs.getString("blockers"));
                return config;
            }
            return null;
        } catch (SQLException e) {
            logger.error("Failed to load configuration", e);
            return null;
        }
    }

    public java.util.List<AuthConfig> loadAllConfigs() {
        java.util.List<AuthConfig> configs = new java.util.ArrayList<>();
        String sql = "SELECT * FROM %s ORDER BY id".formatted(TABLE_NAME);

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                AuthConfig config = new AuthConfig();
                config.setAuthSessionId(rs.getString("auth_session_id"));
                config.setKeycloakIdentity(rs.getString("keycloak_identity"));
                config.setKeycloakSession(rs.getString("keycloak_session"));
                config.setTasksCompleted(rs.getString("tasks_completed"));
                config.setChallenges(rs.getString("challenges"));
                config.setBlockers(rs.getString("blockers"));
                configs.add(config);
            }
            logger.info("Loaded {} user configurations from database", configs.size());
            return configs;
        } catch (SQLException e) {
            logger.error("Failed to load configurations", e);
            return configs;
        }
    }

    public boolean hasConfig() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TABLE_NAME)) {

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        } catch (SQLException e) {
            logger.error("Failed to check configuration", e);
            return false;
        }
    }

    public int getUserCount() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TABLE_NAME)) {

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            logger.error("Failed to count users", e);
            return 0;
        }
    }

    public void resetDatabase() {
        String deleteSql = "DELETE FROM %s".formatted(TABLE_NAME);

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(deleteSql);
            logger.info("Database reset successfully - all user configurations deleted");
        } catch (SQLException e) {
            logger.error("Failed to reset database", e);
            throw new RuntimeException("Failed to reset database", e);
        }
    }
}
