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

    public Connection getConnection() throws SQLException {
        return connect();
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

        String sqlOptState = "CREATE TABLE IF NOT EXISTS OPTIMIZATION_STATE (" +
                "id INTEGER PRIMARY KEY," +
                "opt_result_json TEXT," +
                "selected_passes_json TEXT," +
                "sensitivity_results_json TEXT" +
                ");";

        // Normalized sensitivity table – one row per parameter per period.
        // Designed for easy LLM / SQL consumption.
        String sqlSensitivityDetail = "CREATE TABLE IF NOT EXISTS SENSITIVITY_DETAIL (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "run_timestamp INTEGER," +          // when the analysis ran
                "pass_number INTEGER," +             // which optimization pass
                "pass_name TEXT," +                   // e.g. '15545+15545'
                "expert_name TEXT," +                 // EA name
                "symbol TEXT," +                      // e.g. XAUUSD
                "parameter_name TEXT," +              // which parameter was swept
                "base_value TEXT," +                  // optimized value of this param
                "period TEXT," +                      // 'BT' or 'FW'
                "base_profit REAL," +                 // profit of the optimized strategy for this period
                "mean_profit REAL," +                 // average profit across sweep variants
                "stddev REAL," +                      // standard deviation of profits
                "cv REAL," +                          // coefficient of variation (%%)
                "min_profit REAL," +                  // worst variant profit
                "max_profit REAL," +                  // best variant profit
                "num_variants INTEGER," +             // how many variants were tested
                "sweep_start TEXT," +                 // parameter range start
                "sweep_step TEXT," +                  // parameter range step
                "sweep_end TEXT," +                   // parameter range end
                "curve_json TEXT," +                  // [{paramValue:x, profit:y}, ...]
                "verdict TEXT" +                      // 'ROBUST' / 'ACCEPTABLE' / 'FRAGILE'
                ");";

        // Key-value settings (API keys etc.) – stored in user home, NOT in git.
        String sqlSettings = "CREATE TABLE IF NOT EXISTS APP_SETTINGS (" +
                "key TEXT PRIMARY KEY," +
                "value TEXT" +
                ");";

        String sqlKiReports = "CREATE TABLE IF NOT EXISTS KI_REPORTS (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "run_timestamp INTEGER," +
                "expert_name TEXT," +
                "symbol TEXT," +
                "period TEXT," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "report_markdown TEXT" +
                ");";

        String sqlMultiBatches = "CREATE TABLE IF NOT EXISTS MULTI_BACKTEST_BATCHES (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "batch_name TEXT," +
                "timestamp INTEGER," +
                "html_report_path TEXT," +
                "results_json TEXT" +
                ");";

        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(sqlHistory);
            stmt.execute(sqlSavedConfig);

            // Check if OPTIMIZATION_STATE has the sensitivity_results_json column, otherwise recreate it
            try {
                ResultSet rs = stmt.executeQuery("PRAGMA table_info(OPTIMIZATION_STATE)");
                boolean hasSensitivity = false;
                while (rs.next()) {
                    if ("sensitivity_results_json".equals(rs.getString("name"))) {
                        hasSensitivity = true;
                        break;
                    }
                }
                if (!hasSensitivity) {
                    log.info("Old OPTIMIZATION_STATE schema detected (missing sensitivity_results_json). Recreating table...");
                    stmt.execute("DROP TABLE IF EXISTS OPTIMIZATION_STATE");
                }
            } catch (Exception e) {
                // Ignore
            }
            stmt.execute(sqlOptState);
            stmt.execute(sqlSettings);
            stmt.execute(sqlKiReports);
            stmt.execute(sqlMultiBatches);

            // Check if SENSITIVITY_DETAIL has the new 'verdict' column, otherwise recreate it
            try {
                ResultSet rs = stmt.executeQuery("PRAGMA table_info(SENSITIVITY_DETAIL)");
                boolean hasVerdict = false;
                while (rs.next()) {
                    if ("verdict".equals(rs.getString("name"))) {
                        hasVerdict = true;
                        break;
                    }
                }
                if (!hasVerdict) {
                    log.info("Old SENSITIVITY_DETAIL schema detected. Recreating table...");
                    stmt.execute("DROP TABLE IF EXISTS SENSITIVITY_DETAIL");
                }
            } catch (Exception e) {
                // Ignore
            }
            stmt.execute(sqlSensitivityDetail);

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

    public void deleteRun(int id) {
        String sql = "DELETE FROM HISTORY_RUNS WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
            log.info("Deleted run ID {}", id);
        } catch (SQLException e) {
            log.error("Failed to delete run ID: " + id, e);
        }
    }

    public void deleteAllRuns() {
        String sql = "DELETE FROM HISTORY_RUNS";
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            int count = stmt.executeUpdate(sql);
            log.info("Deleted all runs ({} total)", count);
        } catch (SQLException e) {
            log.error("Failed to delete all runs", e);
        }
    }

    public void saveOptimizationState(String optJson, String selectedJson, String sensitivityJson) {
        String sql = "INSERT OR REPLACE INTO OPTIMIZATION_STATE (id, opt_result_json, selected_passes_json, sensitivity_results_json) VALUES (1, ?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, optJson);
            pstmt.setString(2, selectedJson);
            pstmt.setString(3, sensitivityJson);
            pstmt.executeUpdate();
            log.info("Saved optimization state to database.");
        } catch (SQLException e) {
            log.error("Failed to save optimization state", e);
        }
    }

    public String[] getOptimizationState() {
        String sql = "SELECT opt_result_json, selected_passes_json, sensitivity_results_json FROM OPTIMIZATION_STATE WHERE id = 1";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return new String[]{
                    rs.getString("opt_result_json"),
                    rs.getString("selected_passes_json"),
                    rs.getString("sensitivity_results_json")
                };
            }
        } catch (SQLException e) {
            log.error("Failed to get optimization state", e);
        }
        return null;
    }

    public void clearOptimizationState() {
        String sql = "DELETE FROM OPTIMIZATION_STATE WHERE id = 1";
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            log.info("Cleared optimization state from database.");
        } catch (SQLException e) {
            log.error("Failed to clear optimization state", e);
        }
    }

    // ====================================================================
    // SENSITIVITY_DETAIL – normalized table for LLM / SQL access
    // ====================================================================

    /**
     * Saves a single parameter sweep result as a normalized row.
     * Called once per parameter per period (BT / FW) by SensitivityRunner.
     */
    public void saveSensitivityDetail(
            long runTimestamp, int passNumber, String passName,
            String expertName, String symbol,
            String parameterName, String baseValue, String period,
            double baseProfit, double meanProfit, double stddev, double cv,
            double minProfit, double maxProfit, int numVariants,
            String sweepStart, String sweepStep, String sweepEnd,
            String curveJson, String verdict) {

        String sql = "INSERT INTO SENSITIVITY_DETAIL (" +
                "run_timestamp, pass_number, pass_name, expert_name, symbol, " +
                "parameter_name, base_value, period, base_profit, mean_profit, " +
                "stddev, cv, min_profit, max_profit, num_variants, " +
                "sweep_start, sweep_step, sweep_end, curve_json, verdict" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setLong(1, runTimestamp);
            p.setInt(2, passNumber);
            p.setString(3, passName);
            p.setString(4, expertName);
            p.setString(5, symbol);
            p.setString(6, parameterName);
            p.setString(7, baseValue);
            p.setString(8, period);
            p.setDouble(9, baseProfit);
            p.setDouble(10, meanProfit);
            p.setDouble(11, stddev);
            p.setDouble(12, cv);
            p.setDouble(13, minProfit);
            p.setDouble(14, maxProfit);
            p.setInt(15, numVariants);
            p.setString(16, sweepStart);
            p.setString(17, sweepStep);
            p.setString(18, sweepEnd);
            p.setString(19, curveJson);
            p.setString(20, verdict);
            p.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to save sensitivity detail for {} / {}", parameterName, period, e);
        }
    }

    /**
     * Clears all sensitivity detail rows for a specific run timestamp.
     */
    public void clearSensitivityDetails(long runTimestamp) {
        String sql = "DELETE FROM SENSITIVITY_DETAIL WHERE run_timestamp = ?";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setLong(1, runTimestamp);
            p.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to clear sensitivity details for timestamp {}", runTimestamp, e);
        }
    }

    /**
     * Clears ALL sensitivity detail rows.
     */
    public void clearAllSensitivityDetails() {
        String sql = "DELETE FROM SENSITIVITY_DETAIL";
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            int count = stmt.executeUpdate(sql);
            log.info("Cleared all sensitivity details ({} rows)", count);
            saveSetting("last_ki_analysis_report", "");
        } catch (SQLException e) {
            log.error("Failed to clear all sensitivity details", e);
        }
    }

    // ====================================================================
    // APP_SETTINGS – secure key-value storage (in user home, not in git)
    // ====================================================================

    public void saveSetting(String key, String value) {
        String sql = "INSERT OR REPLACE INTO APP_SETTINGS (key, value) VALUES (?, ?)";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, key);
            p.setString(2, value);
            p.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to save setting '{}'", key, e);
        }
    }

    public String getSetting(String key) {
        String sql = "SELECT value FROM APP_SETTINGS WHERE key = ?";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, key);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) return rs.getString("value");
            }
        } catch (SQLException e) {
            log.error("Failed to get setting '{}'", key, e);
        }
        return null;
    }

    public String getSetting(String key, String defaultValue) {
        String val = getSetting(key);
        return val != null ? val : defaultValue;
    }

    // ====================================================================
    // KI_REPORTS
    // ====================================================================

    public void saveKiReport(long runTimestamp, String expertName, String symbol, String period, String markdown) {
        String sql = "INSERT INTO KI_REPORTS (run_timestamp, expert_name, symbol, period, report_markdown) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setLong(1, runTimestamp);
            p.setString(2, expertName);
            p.setString(3, symbol);
            p.setString(4, period);
            p.setString(5, markdown);
            p.executeUpdate();
            log.info("Saved KI report for {} {} {}", expertName, symbol, period);
        } catch (SQLException e) {
            log.error("Failed to save KI report", e);
        }
    }

    public java.util.List<com.backtester.engine.KiReport> getAllKiReports() {
        java.util.List<com.backtester.engine.KiReport> reports = new java.util.ArrayList<>();
        String sql = "SELECT id, run_timestamp, expert_name, symbol, period, created_at, report_markdown FROM KI_REPORTS ORDER BY id DESC";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                reports.add(new com.backtester.engine.KiReport(
                        rs.getInt("id"),
                        rs.getLong("run_timestamp"),
                        rs.getString("expert_name"),
                        rs.getString("symbol"),
                        rs.getString("period"),
                        rs.getString("created_at"),
                        rs.getString("report_markdown")
                ));
            }
        } catch (SQLException e) {
            log.error("Failed to fetch KI reports", e);
        }
        return reports;
    }

    public void deleteKiReport(int id) {
        String sql = "DELETE FROM KI_REPORTS WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, id);
            p.executeUpdate();
            log.info("Deleted KI report ID {}", id);
        } catch (SQLException e) {
            log.error("Failed to delete KI report", e);
        }
    }

    public void clearKiReports() {
        String sql = "DELETE FROM KI_REPORTS";
        try (Connection conn = connect(); Statement stmt2 = conn.createStatement()) {
            stmt2.executeUpdate(sql);
            log.info("Cleared all KI reports from database.");
        } catch (SQLException e) {
            log.error("Failed to clear KI reports", e);
        }
    }

    // ====================================================================
    // MULTI_BACKTEST_BATCHES – persist multi-backtest batch runs
    // ====================================================================

    public void saveBatch(String batchName, long timestamp, String htmlReportPath, String resultsJson) {
        String sql = "INSERT INTO MULTI_BACKTEST_BATCHES (batch_name, timestamp, html_report_path, results_json) VALUES (?, ?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, batchName);
            p.setLong(2, timestamp);
            p.setString(3, htmlReportPath);
            p.setString(4, resultsJson);
            p.executeUpdate();
            log.info("Saved multi-backtest batch '{}' to database.", batchName);
        } catch (SQLException e) {
            log.error("Failed to save batch '{}'", batchName, e);
        }
    }

    public java.util.List<Object[]> getAllBatches() {
        String sql = "SELECT id, batch_name, timestamp, html_report_path, results_json FROM MULTI_BACKTEST_BATCHES ORDER BY timestamp DESC";
        java.util.List<Object[]> batches = new java.util.ArrayList<>();
        try (Connection conn = connect(); Statement stmt3 = conn.createStatement(); ResultSet rs = stmt3.executeQuery(sql)) {
            while (rs.next()) {
                batches.add(new Object[]{
                    rs.getInt("id"),
                    rs.getString("batch_name"),
                    rs.getLong("timestamp"),
                    rs.getString("html_report_path"),
                    rs.getString("results_json")
                });
            }
        } catch (SQLException e) {
            log.error("Failed to fetch batches from database", e);
        }
        return batches;
    }

    public void deleteBatch(int id) {
        String sql = "DELETE FROM MULTI_BACKTEST_BATCHES WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, id);
            p.executeUpdate();
            log.info("Deleted batch ID {}", id);
        } catch (SQLException e) {
            log.error("Failed to delete batch ID: " + id, e);
        }
    }

    public void clearBatches() {
        String sql = "DELETE FROM MULTI_BACKTEST_BATCHES";
        try (Connection conn = connect(); Statement stmt4 = conn.createStatement()) {
            stmt4.executeUpdate(sql);
            log.info("Cleared all multi-backtest batches from database.");
        } catch (SQLException e) {
            log.error("Failed to clear batches", e);
        }
    }

    public java.util.List<HistoryRun> getRunsByType(String runType) {
        String sql = "SELECT * FROM HISTORY_RUNS WHERE run_type = ? ORDER BY timestamp DESC";
        java.util.List<HistoryRun> runs = new java.util.ArrayList<>();
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, runType);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    runs.add(new HistoryRun(
                        rs.getInt("id"),
                        rs.getString("run_type"),
                        rs.getString("expert_name"),
                        rs.getLong("timestamp"),
                        rs.getString("result_json"),
                        rs.getString("html_path")
                    ));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to fetch runs by type '{}'", runType, e);
        }
        return runs;
    }
}
