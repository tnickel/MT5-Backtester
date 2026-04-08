package com.backtester.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private static DatabaseManager instance;
    private final String dbUrl;

    private DatabaseManager() {
        // Find user home to place DB
        String userHome = System.getProperty("user.home");
        File dir = new File(userHome, ".mt5_backtester");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File dbFile = new File(dir, "history.db");
        dbUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        initializeDatabase();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    private void initializeDatabase() {
        String sqlHistory = "CREATE TABLE IF NOT EXISTS HISTORY_RUNS (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "run_type TEXT," +
                "expert_name TEXT," +
                "timestamp INTEGER," +
                "result_json TEXT," +
                "html_path TEXT" +
                ");";

        String sqlSavedConfig = "CREATE TABLE IF NOT EXISTS EA_SAVED_CONFIGS (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "expert_name TEXT," +
                "config_name TEXT," +
                "parameters_json TEXT," +
                "updated_at INTEGER" +
                ");";

        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(sqlHistory);
            stmt.execute(sqlSavedConfig);

            // Migrate old EA_CONFIGS to EA_SAVED_CONFIGS
            try {
                ResultSet rs = stmt.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='EA_CONFIGS'");
                if (rs.next() && rs.getInt(1) > 0) {
                    log.info("Migrating old EA_CONFIGS to new EA_SAVED_CONFIGS...");
                    stmt.execute("INSERT INTO EA_SAVED_CONFIGS (expert_name, config_name, parameters_json, updated_at) " +
                                 "SELECT expert_name, 'Default Config', parameters_json, updated_at FROM EA_CONFIGS");
                    stmt.execute("DROP TABLE EA_CONFIGS");
                    log.info("Migration successful.");
                }
            } catch (Exception e) {
                log.warn("Could not migrate EA_CONFIGS (might not exist or already migrated)", e);
            }

            log.info("SQLite database initialized at: {}", dbUrl);
        } catch (SQLException e) {
            log.error("Error initializing Database", e);
        }
    }

    public void saveRun(String runType, String expertName, long timestamp, String resultJson, String htmlPath) {
        String sql = "INSERT INTO HISTORY_RUNS(run_type, expert_name, timestamp, result_json, html_path) VALUES(?,?,?,?,?)";
        
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, runType);
            pstmt.setString(2, expertName);
            pstmt.setLong(3, timestamp);
            pstmt.setString(4, resultJson);
            pstmt.setString(5, htmlPath);
            pstmt.executeUpdate();
            log.info("Saved {} run for {} to database.", runType, expertName);
        } catch (SQLException e) {
            log.error("Failed to save run to database", e);
        }
    }

    public List<HistoryRun> getAllRuns() {
        String sql = "SELECT * FROM HISTORY_RUNS ORDER BY timestamp DESC";
        List<HistoryRun> runs = new ArrayList<>();
        
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                HistoryRun run = new HistoryRun(
                    rs.getInt("id"),
                    rs.getString("run_type"),
                    rs.getString("expert_name"),
                    rs.getLong("timestamp"),
                    rs.getString("result_json"),
                    rs.getString("html_path")
                );
                runs.add(run);
            }
        } catch (SQLException e) {
            log.error("Failed to fetch runs from database", e);
        }
        return runs;
    }

    public List<EaDbConfig> getEaConfigsList(String expertName) {
        String sql = "SELECT * FROM EA_SAVED_CONFIGS WHERE expert_name = ? ORDER BY updated_at DESC";
        List<EaDbConfig> configs = new ArrayList<>();
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, expertName);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    configs.add(new EaDbConfig(
                        rs.getInt("id"),
                        rs.getString("expert_name"),
                        rs.getString("config_name"),
                        rs.getString("parameters_json"),
                        rs.getLong("updated_at")
                    ));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to fetch EA configs list for: " + expertName, e);
        }
        return configs;
    }

    public void insertEaConfig(String expertName, String configName, String parametersJson) {
        String sql = "INSERT INTO EA_SAVED_CONFIGS (expert_name, config_name, parameters_json, updated_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, expertName);
            pstmt.setString(2, configName);
            pstmt.setString(3, parametersJson);
            pstmt.setLong(4, System.currentTimeMillis());
            pstmt.executeUpdate();
            log.info("Inserted new DB config '{}' for {}", configName, expertName);
        } catch (SQLException e) {
            log.error("Failed to insert EA config to database for: " + expertName, e);
        }
    }

    public void updateEaConfig(int id, String configName, String parametersJson) {
        String sql = "UPDATE EA_SAVED_CONFIGS SET config_name = ?, parameters_json = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, configName);
            pstmt.setString(2, parametersJson);
            pstmt.setLong(3, System.currentTimeMillis());
            pstmt.setInt(4, id);
            pstmt.executeUpdate();
            log.info("Updated DB config ID {}", id);
        } catch (SQLException e) {
            log.error("Failed to update EA config ID: " + id, e);
        }
    }

    public void deleteEaConfig(int id) {
        String sql = "DELETE FROM EA_SAVED_CONFIGS WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
            log.info("Deleted DB config ID {}", id);
        } catch (SQLException e) {
            log.error("Failed to delete EA config ID: " + id, e);
        }
    }
}
