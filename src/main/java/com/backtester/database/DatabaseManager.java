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
    private final java.util.Map<String, String> settingsCache = new java.util.concurrent.ConcurrentHashMap<>();

    private DatabaseManager() {
        // Find user home to place DB
        String userHome = System.getProperty("user.home");
        File dir = new File(userHome, ".mt5_backtester");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File dbFile = new File(dir, "history.db");
        dbUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath() + "?journal_mode=WAL&busy_timeout=10000";
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

        String sqlEaParamSettings = "CREATE TABLE IF NOT EXISTS EA_PARAMETER_SETTINGS (" +
                "expert_name TEXT," +
                "symbol TEXT," +
                "period TEXT," +
                "parameters_json TEXT," +
                "updated_at INTEGER," +
                "PRIMARY KEY (expert_name, symbol, period)" +
                ");";

        String sqlWorkflowState = "CREATE TABLE IF NOT EXISTS WORKFLOW_STATE (" +
                "id INTEGER PRIMARY KEY," +
                "expert_name TEXT," +
                "symbol TEXT," +
                "period TEXT," +
                "from_date TEXT," +
                "to_date TEXT," +
                "deposit INTEGER," +
                "currency TEXT," +
                "leverage TEXT," +
                "tick_model INTEGER," +
                "ea_parameters_json TEXT," +
                "opt_result_json TEXT," +
                "selected_diverse_passes_json TEXT," +
                "sensitivity_results_json TEXT," +
                "ki_report_text TEXT," +
                "final_selected_passes_json TEXT," +
                "last_active_step INTEGER," +
                "validation_results_json TEXT," +
                "ki_gate_bypassed INTEGER DEFAULT 0" +
                ");";

        String sqlWorkflowStrategyConfigs = "CREATE TABLE IF NOT EXISTS WORKFLOW_STRATEGY_CONFIGS (" +
                "expert_name TEXT PRIMARY KEY," +
                "config_json TEXT," +
                "updated_at INTEGER" +
                ");";

        String sqlStrategyReviews = "CREATE TABLE IF NOT EXISTS STRATEGY_REVIEWS (" +
                "expert_name TEXT," +
                "symbol TEXT," +
                "period TEXT," +
                "run_timestamp INTEGER," +
                "pass_number INTEGER," +
                "review_text TEXT," +
                "color_rating TEXT," +
                "PRIMARY KEY (expert_name, symbol, period, run_timestamp, pass_number)" +
                ");";

        String sqlStrategyAutomaticReviews = "CREATE TABLE IF NOT EXISTS STRATEGY_AUTOMATIC_REVIEWS (" +
                "expert_name TEXT," +
                "symbol TEXT," +
                "period TEXT," +
                "run_timestamp INTEGER," +
                "pass_number INTEGER," +
                "result_1y_json TEXT," +
                "result_2y_json TEXT," +
                "PRIMARY KEY (expert_name, symbol, period, run_timestamp, pass_number)" +
                ");";

        String sqlCustomProjects = "CREATE TABLE IF NOT EXISTS CUSTOM_PROJECTS (" +
                "id TEXT PRIMARY KEY," +
                "name TEXT," +
                "expert TEXT," +
                "symbol TEXT," +
                "period TEXT," +
                "created_timestamp INTEGER," +
                "last_run_timestamp INTEGER," +
                "project_json TEXT" +
                ");";

        String sqlParamTranslations = "CREATE TABLE IF NOT EXISTS EA_PARAMETER_TRANSLATIONS (" +
                "expert_name TEXT," +
                "parameter_name TEXT," +
                "display_name TEXT," +
                "PRIMARY KEY (expert_name, parameter_name)" +
                ");";

        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(sqlHistory);
            stmt.execute(sqlSavedConfig);
            stmt.execute(sqlEaParamSettings);
            stmt.execute(sqlWorkflowState);
            stmt.execute(sqlWorkflowStrategyConfigs);
            stmt.execute(sqlStrategyReviews);
            stmt.execute(sqlStrategyAutomaticReviews);
            stmt.execute(sqlCustomProjects);
            stmt.execute(sqlParamTranslations);

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

            // Migration: WORKFLOW_STATE gained validation_results_json (Step 7)
            // and ki_gate_bypassed (visible KI-gate fallback across restarts)
            try {
                ResultSet rs = stmt.executeQuery("PRAGMA table_info(WORKFLOW_STATE)");
                boolean hasValidation = false;
                boolean hasKiGate = false;
                while (rs.next()) {
                    String col = rs.getString("name");
                    if ("validation_results_json".equals(col)) hasValidation = true;
                    if ("ki_gate_bypassed".equals(col)) hasKiGate = true;
                }
                if (!hasValidation) {
                    log.info("Migrating WORKFLOW_STATE: adding validation_results_json column...");
                    stmt.execute("ALTER TABLE WORKFLOW_STATE ADD COLUMN validation_results_json TEXT");
                }
                if (!hasKiGate) {
                    log.info("Migrating WORKFLOW_STATE: adding ki_gate_bypassed column...");
                    stmt.execute("ALTER TABLE WORKFLOW_STATE ADD COLUMN ki_gate_bypassed INTEGER DEFAULT 0");
                }
            } catch (Exception e) {
                log.warn("Could not migrate WORKFLOW_STATE for validation/ki-gate columns", e);
            }

            // Migration: CUSTOM_PROJECTS sort_order column
            try {
                ResultSet rs = stmt.executeQuery("PRAGMA table_info(CUSTOM_PROJECTS)");
                boolean hasSortOrder = false;
                while (rs.next()) {
                    if ("sort_order".equalsIgnoreCase(rs.getString("name"))) {
                        hasSortOrder = true;
                        break;
                    }
                }
                if (!hasSortOrder) {
                    log.info("Migrating CUSTOM_PROJECTS: adding sort_order column...");
                    stmt.execute("ALTER TABLE CUSTOM_PROJECTS ADD COLUMN sort_order INTEGER DEFAULT 0");
                }
            } catch (Exception e) {
                log.warn("Could not migrate CUSTOM_PROJECTS for sort_order column", e);
            }

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

            // Apply legacy defaults and the trade-first profile exactly once.
            // The version marker protects later user customizations that happen
            // to equal one of the historic default values.
            try {
                int selectionProfileVersion = 0;
                try (ResultSet selectionProfile = stmt.executeQuery(
                        "SELECT value FROM APP_SETTINGS WHERE key = 'workflow.selection.profile.version'")) {
                    if (selectionProfile.next()) {
                        try {
                            selectionProfileVersion = Integer.parseInt(selectionProfile.getString(1));
                        } catch (NumberFormatException ignored) {
                            selectionProfileVersion = 0;
                        }
                    }
                }
                boolean migrateTradeRecoveryProfile = selectionProfileVersion < 1;
                if (migrateTradeRecoveryProfile) {
                    stmt.execute("UPDATE APP_SETTINGS SET value = '10' WHERE key = 'opt.weight.consistency' AND value = '15'");
                    stmt.execute("UPDATE APP_SETTINGS SET value = '10' WHERE key = 'opt.weight.risk' AND value = '15'");
                    stmt.execute("UPDATE APP_SETTINGS SET value = '25' WHERE key = 'opt.weight.sampleSize' AND value = '10'");
                    stmt.execute("UPDATE APP_SETTINGS SET value = '5' WHERE key = 'opt.weight.tailRisk' AND value = '10'");
                    stmt.execute("UPDATE APP_SETTINGS SET value = '30' WHERE key = 'opt.weight.fwTrades' AND value = '5'");
                    stmt.execute("UPDATE APP_SETTINGS SET value = '25' WHERE key = 'opt.weight.recovery' AND value = '5'");
                    stmt.execute("UPDATE APP_SETTINGS SET value = '0.01' WHERE key = 'opt.filter.minBtProfit' AND value = '0.0'");
                    stmt.execute("UPDATE APP_SETTINGS SET value = '0.01' WHERE key = 'opt.filter.minFwProfit' AND value = '0.0'");
                    stmt.execute("UPDATE APP_SETTINGS SET value = '100' WHERE key = 'opt.filter.minBtTrades' AND value = '1'");
                    stmt.execute("UPDATE APP_SETTINGS SET value = '50' WHERE key = 'opt.filter.minFwTrades' AND value = '0'");
                    stmt.execute("UPDATE APP_SETTINGS SET value = '1.0' WHERE key = 'opt.filter.minBtRecovery' AND value = '0.0'");
                    stmt.execute("UPDATE APP_SETTINGS SET value = '1.0' WHERE key = 'opt.filter.minFwRecovery' AND value = '0.0'");

                    String[][] profile = {
                        {"opt.weight.btProfit", "7"},
                        {"opt.weight.fwProfit", "7"},
                        {"opt.weight.consistency", "6"},
                        {"opt.weight.risk", "3"},
                        {"opt.weight.equityConsist", "3"},
                        {"opt.weight.sampleSize", "23"},
                        {"opt.weight.fwTrades", "30"},
                        {"opt.weight.recovery", "21"},
                        {"opt.weight.recovery.min", "1.0"},
                        {"opt.weight.recovery.max", "5.0"},
                        {"opt.filter.minBtProfit", "0.01"},
                        {"opt.filter.minFwProfit", "0.01"},
                        {"opt.filter.minBtTrades", "100"},
                        {"opt.filter.minFwTrades", "50"},
                        {"opt.filter.minBtRecovery", "1.0"},
                        {"opt.filter.minFwRecovery", "1.0"},
                        {"workflow.selection.profile.version", "1"}
                    };
                    for (String[] setting : profile) {
                        try (PreparedStatement upsert = conn.prepareStatement(
                                "INSERT INTO APP_SETTINGS(key, value) VALUES(?, ?) " +
                                "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
                            upsert.setString(1, setting[0]);
                            upsert.setString(2, setting[1]);
                            upsert.executeUpdate();
                        }
                    }
                    log.info("Applied trade-first workflow selection profile (version 1).");
                    log.info("Migrated old default settings in APP_SETTINGS.");
                }
            } catch (Exception e) {
                log.warn("Could not migrate app settings defaults", e);
            }

            log.info("SQLite database initialized at: {}", dbUrl);
            settingsCache.clear();
        } catch (SQLException e) {
            log.error("Error initializing Database", e);
        }
    }

    public int saveRun(String runType, String expertName, long timestamp, String resultJson, String htmlPath) {
        String sql = "INSERT INTO HISTORY_RUNS(run_type, expert_name, timestamp, result_json, html_path) VALUES(?,?,?,?,?)";
        int generatedId = -1;
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, runType);
            pstmt.setString(2, expertName);
            pstmt.setLong(3, timestamp);
            pstmt.setString(4, resultJson);
            pstmt.setString(5, htmlPath);
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    generatedId = rs.getInt(1);
                }
            }
            log.info("Saved {} run for {} to database with ID {}.", runType, expertName, generatedId);
        } catch (SQLException e) {
            log.error("Failed to save run to database", e);
        }
        return generatedId;
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

    /** Returns whether this exact strategy has persisted rows in a specific robustness run. */
    public boolean hasSensitivityDetails(long runTimestamp, int passNumber, String passName) {
        if (runTimestamp <= 0L || passName == null || passName.isBlank()) return false;
        String sql = "SELECT 1 FROM SENSITIVITY_DETAIL " +
                "WHERE run_timestamp = ? AND pass_number = ? AND pass_name = ? LIMIT 1";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setLong(1, runTimestamp);
            p.setInt(2, passNumber);
            p.setString(3, passName);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("Failed to check sensitivity details for run {} / pass {}", runTimestamp, passNumber, e);
            return false;
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
        if (value == null) {
            settingsCache.put(key, "__NULL__");
        } else {
            settingsCache.put(key, value);
        }
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
        String cached = settingsCache.get(key);
        if (cached != null) {
            return "__NULL__".equals(cached) ? null : cached;
        }
        String sql = "SELECT value FROM APP_SETTINGS WHERE key = ?";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, key);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    String val = rs.getString("value");
                    if (val != null) {
                        settingsCache.put(key, val);
                    } else {
                        settingsCache.put(key, "__NULL__");
                    }
                    return val;
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get setting '{}'", key, e);
        }
        settingsCache.put(key, "__NULL__");
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

    public void updateBatchResults(int id, String resultsJson) {
        String sql = "UPDATE MULTI_BACKTEST_BATCHES SET results_json = ? WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, resultsJson);
            p.setInt(2, id);
            p.executeUpdate();
            log.info("Updated batch ID {} results in database.", id);
        } catch (SQLException e) {
            log.error("Failed to update batch ID: " + id, e);
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

    public void saveEaParameterSettings(String expertName, String symbol, String period, String parametersJson) {
        String sql = "INSERT OR REPLACE INTO EA_PARAMETER_SETTINGS (expert_name, symbol, period, parameters_json, updated_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, expertName);
            pstmt.setString(2, symbol);
            pstmt.setString(3, period);
            pstmt.setString(4, parametersJson);
            pstmt.setLong(5, System.currentTimeMillis());
            pstmt.executeUpdate();
            log.info("Saved EA parameter settings to DB for: {} [{}, {}]", expertName, symbol, period);
        } catch (SQLException e) {
            log.error("Failed to save EA parameter settings to database for: " + expertName, e);
        }
    }

    public String getEaParameterSettings(String expertName, String symbol, String period) {
        String sql = "SELECT parameters_json FROM EA_PARAMETER_SETTINGS WHERE expert_name = ? AND symbol = ? AND period = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, expertName);
            pstmt.setString(2, symbol);
            pstmt.setString(3, period);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("parameters_json");
                }
            }
        } catch (SQLException e) {
            log.error("Failed to fetch EA parameter settings from database for: " + expertName, e);
        }
        return null;
    }

    public void deleteRunsByType(String runType) {
        String sql = "DELETE FROM HISTORY_RUNS WHERE run_type = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, runType);
            int count = pstmt.executeUpdate();
            log.info("Deleted runs of type {} ({} total) from database.", runType, count);
        } catch (SQLException e) {
            log.error("Failed to delete runs of type " + runType, e);
        }
    }

    public void saveWorkflowState(
            String expertName, String symbol, String period,
            String fromDate, String toDate, int deposit,
            String currency, String leverage, int tickModel,
            String eaParametersJson, String optResultJson,
            String selectedDiversePassesJson, String sensitivityResultsJson,
            String kiReportText, String finalSelectedPassesJson,
            int lastActiveStep, String validationResultsJson,
            boolean kiGateBypassed) {

        String sql = "INSERT OR REPLACE INTO WORKFLOW_STATE (" +
                "id, expert_name, symbol, period, from_date, to_date, deposit, " +
                "currency, leverage, tick_model, ea_parameters_json, opt_result_json, " +
                "selected_diverse_passes_json, sensitivity_results_json, ki_report_text, " +
                "final_selected_passes_json, last_active_step, validation_results_json, ki_gate_bypassed" +
                ") VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, expertName);
            p.setString(2, symbol);
            p.setString(3, period);
            p.setString(4, fromDate);
            p.setString(5, toDate);
            p.setInt(6, deposit);
            p.setString(7, currency);
            p.setString(8, leverage);
            p.setInt(9, tickModel);
            p.setString(10, eaParametersJson);
            p.setString(11, optResultJson);
            p.setString(12, selectedDiversePassesJson);
            p.setString(13, sensitivityResultsJson);
            p.setString(14, kiReportText);
            p.setString(15, finalSelectedPassesJson);
            p.setInt(16, lastActiveStep);
            p.setString(17, validationResultsJson);
            p.setInt(18, kiGateBypassed ? 1 : 0);
            p.executeUpdate();
            log.info("Saved workflow state to database.");
        } catch (SQLException e) {
            log.error("Failed to save workflow state", e);
        }
    }

    public Object[] getWorkflowState() {
        String sql = "SELECT * FROM WORKFLOW_STATE WHERE id = 1";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return new Object[]{
                    rs.getString("expert_name"),
                    rs.getString("symbol"),
                    rs.getString("period"),
                    rs.getString("from_date"),
                    rs.getString("to_date"),
                    rs.getInt("deposit"),
                    rs.getString("currency"),
                    rs.getString("leverage"),
                    rs.getInt("tick_model"),
                    rs.getString("ea_parameters_json"),
                    rs.getString("opt_result_json"),
                    rs.getString("selected_diverse_passes_json"),
                    rs.getString("sensitivity_results_json"),
                    rs.getString("ki_report_text"),
                    rs.getString("final_selected_passes_json"),
                    rs.getInt("last_active_step"),
                    rs.getString("validation_results_json"),
                    rs.getInt("ki_gate_bypassed") != 0
                };
            }
        } catch (SQLException e) {
            log.error("Failed to get workflow state", e);
        }
        return null;
    }

    public void clearWorkflowState() {
        String sql = "DELETE FROM WORKFLOW_STATE WHERE id = 1";
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            log.info("Cleared workflow state.");
        } catch (SQLException e) {
            log.error("Failed to clear workflow state", e);
        }
    }

    public void saveWorkflowStrategyConfig(String expertName, String configJson) {
        String sql = "INSERT OR REPLACE INTO WORKFLOW_STRATEGY_CONFIGS (expert_name, config_json, updated_at) VALUES (?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, expertName);
            pstmt.setString(2, configJson);
            pstmt.setLong(3, System.currentTimeMillis());
            pstmt.executeUpdate();
            log.info("Saved strategy config to DB for: {}", expertName);
        } catch (SQLException e) {
            log.error("Failed to save strategy config to database for: " + expertName, e);
        }
    }

    public String getWorkflowStrategyConfig(String expertName) {
        String sql = "SELECT config_json FROM WORKFLOW_STRATEGY_CONFIGS WHERE expert_name = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, expertName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("config_json");
                }
            }
        } catch (SQLException e) {
            log.error("Failed to fetch strategy config from database for: " + expertName, e);
        }
        return null;
    }

    public void deleteWorkflowStrategyConfig(String expertName) {
        String sql = "DELETE FROM WORKFLOW_STRATEGY_CONFIGS WHERE expert_name = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, expertName);
            pstmt.executeUpdate();
            log.info("Deleted workflow strategy config for {}", expertName);
        } catch (SQLException e) {
            log.error("Failed to delete workflow strategy config for: " + expertName, e);
        }
    }

    public void deleteEaParameterSettings(String expertName) {
        if (expertName == null || expertName.trim().isEmpty()) return;
        String baseName = com.backtester.config.EaParameterManager.extractEaBaseName(expertName);
        String sql = "DELETE FROM EA_PARAMETER_SETTINGS WHERE expert_name = ? OR expert_name LIKE ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, expertName);
            pstmt.setString(2, "%" + baseName + "%");
            int count = pstmt.executeUpdate();
            log.info("Deleted EA parameter settings for {} ({} records deleted)", expertName, count);
        } catch (SQLException e) {
            log.error("Failed to delete EA parameter settings for: " + expertName, e);
        }
    }

    public static class StrategyReview {
        private final String expertName;
        private final String symbol;
        private final String period;
        private final long runTimestamp;
        private final int passNumber;
        private final String reviewText;
        private final String colorRating;

        public StrategyReview(String expertName, String symbol, String period, long runTimestamp, int passNumber, String reviewText, String colorRating) {
            this.expertName = expertName;
            this.symbol = symbol;
            this.period = period;
            this.runTimestamp = runTimestamp;
            this.passNumber = passNumber;
            this.reviewText = reviewText;
            this.colorRating = colorRating;
        }

        public String getExpertName() { return expertName; }
        public String getSymbol() { return symbol; }
        public String getPeriod() { return period; }
        public long getRunTimestamp() { return runTimestamp; }
        public int getPassNumber() { return passNumber; }
        public String getReviewText() { return reviewText; }
        public String getColorRating() { return colorRating; }
    }

    public void saveStrategyReview(String expertName, String symbol, String period, long runTimestamp, int passNumber, String reviewText, String colorRating) {
        String sql = "INSERT OR REPLACE INTO STRATEGY_REVIEWS(expert_name, symbol, period, run_timestamp, pass_number, review_text, color_rating) VALUES(?,?,?,?,?,?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, expertName);
            pstmt.setString(2, symbol);
            pstmt.setString(3, period);
            pstmt.setLong(4, runTimestamp);
            pstmt.setInt(5, passNumber);
            pstmt.setString(6, reviewText);
            pstmt.setString(7, colorRating);
            pstmt.executeUpdate();
            log.info("Saved strategy review for {} (Pass {}) with rating {}.", expertName, passNumber, colorRating);
        } catch (SQLException e) {
            log.error("Failed to save strategy review", e);
        }
    }

    public List<StrategyReview> getAllStrategyReviews() {
        String sql = "SELECT * FROM STRATEGY_REVIEWS";
        List<StrategyReview> reviews = new ArrayList<>();
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                reviews.add(new StrategyReview(
                    rs.getString("expert_name"),
                    rs.getString("symbol"),
                    rs.getString("period"),
                    rs.getLong("run_timestamp"),
                    rs.getInt("pass_number"),
                    rs.getString("review_text"),
                    rs.getString("color_rating")
                ));
            }
        } catch (SQLException e) {
            log.error("Failed to fetch strategy reviews", e);
        }
        return reviews;
    }

    public HistoryRun getRunById(int id) {
        String sql = "SELECT * FROM HISTORY_RUNS WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new HistoryRun(
                        rs.getInt("id"),
                        rs.getString("run_type"),
                        rs.getString("expert_name"),
                        rs.getLong("timestamp"),
                        rs.getString("result_json"),
                        rs.getString("html_path")
                    );
                }
            }
        } catch (SQLException e) {
            log.error("Failed to fetch run by ID: " + id, e);
        }
        return null;
    }

    public void updateRunResultJson(int id, String resultJson) {
        String sql = "UPDATE HISTORY_RUNS SET result_json = ? WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, resultJson);
            pstmt.setInt(2, id);
            pstmt.executeUpdate();
            log.info("Updated result_json for history run ID {}.", id);
        } catch (SQLException e) {
            log.error("Failed to update history run result_json", e);
        }
    }

    public void deleteStrategyReview(String expertName, String symbol, String period, long runTimestamp, int passNumber) {
        String sql = "DELETE FROM STRATEGY_REVIEWS WHERE expert_name = ? AND symbol = ? AND period = ? AND run_timestamp = ? AND pass_number = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, expertName);
            pstmt.setString(2, symbol);
            pstmt.setString(3, period);
            pstmt.setLong(4, runTimestamp);
            pstmt.setInt(5, passNumber);
            pstmt.executeUpdate();
            log.info("Deleted strategy review for {} (Pass {}).", expertName, passNumber);
        } catch (SQLException e) {
            log.error("Failed to delete strategy review", e);
        }
    }

    public static class AutomaticReview {
        private final String expertName;
        private final String symbol;
        private final String period;
        private final long runTimestamp;
        private final int passNumber;
        private final String result1yJson;
        private final String result2yJson;

        public AutomaticReview(String expertName, String symbol, String period, long runTimestamp, int passNumber, String result1yJson, String result2yJson) {
            this.expertName = expertName;
            this.symbol = symbol;
            this.period = period;
            this.runTimestamp = runTimestamp;
            this.passNumber = passNumber;
            this.result1yJson = result1yJson;
            this.result2yJson = result2yJson;
        }

        public String getExpertName() { return expertName; }
        public String getSymbol() { return symbol; }
        public String getPeriod() { return period; }
        public long getRunTimestamp() { return runTimestamp; }
        public int getPassNumber() { return passNumber; }
        public String getResult1yJson() { return result1yJson; }
        public String getResult2yJson() { return result2yJson; }
    }

    public void saveAutomaticReview(String expertName, String symbol, String period, long runTimestamp, int passNumber, String result1yJson, String result2yJson) {
        String sql = "INSERT OR REPLACE INTO STRATEGY_AUTOMATIC_REVIEWS(expert_name, symbol, period, run_timestamp, pass_number, result_1y_json, result_2y_json) VALUES(?,?,?,?,?,?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, expertName);
            pstmt.setString(2, symbol);
            pstmt.setString(3, period);
            pstmt.setLong(4, runTimestamp);
            pstmt.setInt(5, passNumber);
            pstmt.setString(6, result1yJson);
            pstmt.setString(7, result2yJson);
            pstmt.executeUpdate();
            log.info("Saved automatic review for {} (Pass {}).", expertName, passNumber);
        } catch (SQLException e) {
            log.error("Failed to save automatic review", e);
        }
    }

    public AutomaticReview getAutomaticReview(String expertName, String symbol, String period, long runTimestamp, int passNumber) {
        String sql = "SELECT * FROM STRATEGY_AUTOMATIC_REVIEWS WHERE expert_name = ? AND symbol = ? AND period = ? AND run_timestamp = ? AND pass_number = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, expertName);
            pstmt.setString(2, symbol);
            pstmt.setString(3, period);
            pstmt.setLong(4, runTimestamp);
            pstmt.setInt(5, passNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new AutomaticReview(
                        rs.getString("expert_name"),
                        rs.getString("symbol"),
                        rs.getString("period"),
                        rs.getLong("run_timestamp"),
                        rs.getInt("pass_number"),
                        rs.getString("result_1y_json"),
                        rs.getString("result_2y_json")
                    );
                }
            }
        } catch (SQLException e) {
            log.error("Failed to fetch automatic review", e);
        }
        return null;
    }

    public List<AutomaticReview> getAllAutomaticReviews() {
        String sql = "SELECT * FROM STRATEGY_AUTOMATIC_REVIEWS";
        List<AutomaticReview> reviews = new ArrayList<>();
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                reviews.add(new AutomaticReview(
                    rs.getString("expert_name"),
                    rs.getString("symbol"),
                    rs.getString("period"),
                    rs.getLong("run_timestamp"),
                    rs.getInt("pass_number"),
                    rs.getString("result_1y_json"),
                    rs.getString("result_2y_json")
                ));
            }
        } catch (SQLException e) {
            log.error("Failed to fetch automatic reviews", e);
        }
        return reviews;
    }

    public void deleteAutomaticReview(String expertName, String symbol, String period, long runTimestamp, int passNumber) {
        String sql = "DELETE FROM STRATEGY_AUTOMATIC_REVIEWS WHERE expert_name = ? AND symbol = ? AND period = ? AND run_timestamp = ? AND pass_number = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, expertName);
            pstmt.setString(2, symbol);
            pstmt.setString(3, period);
            pstmt.setLong(4, runTimestamp);
            pstmt.setInt(5, passNumber);
            pstmt.executeUpdate();
            log.info("Deleted automatic review for {} (Pass {}).", expertName, passNumber);
        } catch (SQLException e) {
            log.error("Failed to delete automatic review", e);
        }
    }

    public void saveParameterTranslation(String expertName, String parameterName, String displayName) {
        String sql = "INSERT OR REPLACE INTO EA_PARAMETER_TRANSLATIONS (expert_name, parameter_name, display_name) VALUES (?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, expertName);
            pstmt.setString(2, parameterName);
            pstmt.setString(3, displayName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to save parameter translation", e);
        }
    }

    public String getParameterTranslation(String expertName, String parameterName) {
        String sql = "SELECT display_name FROM EA_PARAMETER_TRANSLATIONS WHERE expert_name = ? AND parameter_name = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, expertName);
            pstmt.setString(2, parameterName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("display_name");
                }
            }
        } catch (SQLException e) {
            log.error("Failed to fetch parameter translation", e);
        }
        return null;
    }

    public java.util.Map<String, String> getParameterTranslations(String expertName) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        String sql = "SELECT parameter_name, display_name FROM EA_PARAMETER_TRANSLATIONS WHERE expert_name = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, expertName);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("parameter_name"), rs.getString("display_name"));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to fetch parameter translations for " + expertName, e);
        }
        return map;
    }


    public static String translatePortugueseParameter(String rawName) {
        if (rawName == null || rawName.isEmpty()) return "";
        
        // Skip dummy params
        if (rawName.matches("^[a-z]\\d+$") || rawName.matches("^ln\\d+$")) {
            return rawName;
        }

        // Check if it's a section divider like "BANDAS_BOLLINGER_X"
        String name = rawName;
        boolean isSection = name.endsWith("_X");
        if (isSection) {
            name = name.substring(0, name.length() - 2);
        }
        
        // Dictionary of common Portuguese/Spanish-to-English trading terms
        java.util.Map<String, String> dict = new java.util.HashMap<>();
        dict.put("inp", ""); // Remove Inp prefix
        dict.put("estrategia", "Strategy");
        dict.put("compra", "Buy");
        dict.put("venda", "Sell");
        dict.put("distancia", "Distance");
        dict.put("maxima", "Max");
        dict.put("minima", "Min");
        dict.put("periodo", "Period");
        dict.put("metodo", "Method");
        dict.put("preco", "Price");
        dict.put("desvio", "Deviation");
        dict.put("inferior", "Lower");
        dict.put("superior", "Upper");
        dict.put("habilitar", "Enable");
        dict.put("parar", "Stop");
        dict.put("tempo", "Time");
        dict.put("antes", "Before");
        dict.put("depois", "After");
        dict.put("moeda", "Currency");
        dict.put("noticias", "News");
        dict.put("hora", "Hour");
        dict.put("comecar", "Start");
        dict.put("mudar", "Change");
        dict.put("dia", "Day");
        dict.put("conta", "Account");
        dict.put("lucro", "Profit");
        dict.put("prejuizo", "Loss");
        dict.put("saldo", "Balance");
        dict.put("fator", "Factor");
        dict.put("lote", "Lot");
        dict.put("inicial", "Initial");
        dict.put("minimo", "Min");
        dict.put("maximo", "Max");
        dict.put("ordens", "Orders");
        dict.put("matriz", "Grid");
        dict.put("pendente", "Pending");
        dict.put("cancelar", "Cancel");
        dict.put("linhas", "Lines");
        dict.put("grafico", "Chart");
        dict.put("avisos", "Alerts");
        dict.put("comentario", "Comment");
        dict.put("saque", "Withdrawal");
        dict.put("otimizacao", "Optimization");
        dict.put("dinheiro", "Money");
        dict.put("gerenciar", "Manage");
        dict.put("ajustar", "Adjust");
        dict.put("cotacao", "Quote");
        dict.put("lista", "List");
        dict.put("ativo", "Active");
        dict.put("ativos", "Active");
        dict.put("robo", "Robot");
        dict.put("rebaixamento", "Drawdown");
        dict.put("abertura", "Opening");
        dict.put("operacao", "Operation");
        dict.put("sinal", "Signal");
        dict.put("confirmacao", "Confirmation");
        dict.put("qtde", "Qty");
        dict.put("aumentar", "Increase");
        dict.put("tamanho", "Size");
        dict.put("painel", "Panel");
        dict.put("informacao", "Info");
        dict.put("fechar", "Close");
        dict.put("dias", "Days");
        dict.put("domingo", "Sunday");
        dict.put("segunda", "Monday");
        dict.put("terca", "Tuesday");
        dict.put("quarta", "Wednesday");
        dict.put("quinta", "Thursday");
        dict.put("sexta", "Friday");
        dict.put("sabado", "Saturday");
        dict.put("bandas", "Bands");
        dict.put("bollinger", "Bollinger");
        dict.put("kelter", "Keltner");
        dict.put("keltner", "Keltner");
        dict.put("canais", "Channels");
        dict.put("calculo", "Calculation");
        dict.put("multiplicador", "Multiplier");
        dict.put("rapida", "Fast");
        dict.put("lenta", "Slow");
        dict.put("media", "MA");
        dict.put("medias", "MAs");
        dict.put("moveis", "Moving");
        dict.put("nivel", "Level");
        dict.put("passo", "Step");
        dict.put("filtro", "Filter");
        dict.put("volume", "Volume");
        dict.put("desbloqueio", "Unlock");
        dict.put("preset", "Preset");
        dict.put("cuidado", "Warning");
        dict.put("dados", "Data");
        dict.put("criar", "Create");
        dict.put("cor", "Color");
        dict.put("texto", "Text");
        dict.put("modo", "Mode");
        dict.put("usar", "Use");
        dict.put("proximo", "Next");
        dict.put("ganho", "Gain");
        dict.put("perda", "Loss");
        dict.put("reduzir", "Reduce");
        dict.put("ultima", "Last");
        dict.put("somar", "Add");
        dict.put("travar", "Lock");
        dict.put("tipo", "Type");
        dict.put("buscar", "Target");
        dict.put("indicador", "Indicator");
        dict.put("contrario", "Contrary");
        dict.put("valor", "Value");
        dict.put("ordem", "Order");
        dict.put("suporte", "Support");
        dict.put("resistencia", "Resistance");
        dict.put("lento", "Slow");
        dict.put("rapido", "Fast");
        dict.put("recup", "Recovery");
        dict.put("recuperacao", "Recovery");
        dict.put("ou", "Or");
        dict.put("breakeven", "BreakEven");
        dict.put("somado", "Added");
        dict.put("valores", "Values");
        dict.put("deletar", "Delete");
        dict.put("automatico", "Automatic");
        dict.put("pontos", "Points");
        dict.put("botao", "Button");
        dict.put("sequencia", "Sequence");
        dict.put("mesmo", "Same");
        dict.put("anterior", "Previous");
        dict.put("deslocamento", "Offset");
        dict.put("capital", "Equity");
        dict.put("menor", "Lowest");
        dict.put("maior", "Highest");
        dict.put("noticia", "News");
        dict.put("ganhadoras", "Winning");
        dict.put("perdedoras", "Losing");
        dict.put("fora", "Outside");
        dict.put("limite", "Limit");
        
        // Split by underscores and translate each token
        String[] tokens = name.split("_");
        java.util.List<String> translated = new java.util.ArrayList<>();
        for (String token : tokens) {
            String lowerToken = token.toLowerCase();
            if (dict.containsKey(lowerToken)) {
                String trans = dict.get(lowerToken);
                if (!trans.isEmpty()) {
                    translated.add(trans);
                }
            } else {
                // Keep original word if not in dictionary, but capitalize first letter
                if (!token.isEmpty()) {
                    translated.add(Character.toUpperCase(token.charAt(0)) + token.substring(1).toLowerCase());
                }
            }
        }
        
        if (translated.isEmpty()) return rawName;
        
        // Join with space
        String joined = String.join(" ", translated);
        if (isSection) {
            joined = "■ " + joined + " ■";
        }
        return joined;
    }

    // ─── CUSTOM PROJECTS PERSISTENCE ──────────────────────────────────────────

    public boolean saveCustomProject(com.backtester.workflow.CustomProject project) {
        if (project == null || project.getId() == null) return false;
        String sql = "INSERT INTO CUSTOM_PROJECTS (id, name, expert, symbol, period, created_timestamp, last_run_timestamp, sort_order, project_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET " +
                "name = excluded.name, " +
                "expert = excluded.expert, " +
                "symbol = excluded.symbol, " +
                "period = excluded.period, " +
                "created_timestamp = excluded.created_timestamp, " +
                "last_run_timestamp = excluded.last_run_timestamp, " +
                "sort_order = excluded.sort_order, " +
                "project_json = excluded.project_json;";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            com.google.gson.Gson gson = createCustomProjectGson();
            String json = gson.toJson(project);

            pstmt.setString(1, project.getId());
            pstmt.setString(2, project.getName());
            pstmt.setString(3, project.getExpert());
            pstmt.setString(4, project.getSymbol());
            pstmt.setString(5, project.getPeriod());
            pstmt.setLong(6, project.getCreatedTimestamp());
            pstmt.setLong(7, project.getLastRunTimestamp());
            pstmt.setInt(8, project.getSortOrder());
            pstmt.setString(9, json);
            pstmt.executeUpdate();
            log.info("Saved custom project: {} ({})", project.getName(), project.getId());
            return true;
        } catch (SQLException | RuntimeException e) {
            log.error("Failed to save custom project: " + project.getName(), e);
            return false;
        }
    }

    public List<com.backtester.workflow.CustomProject> getAllCustomProjects() {
        List<com.backtester.workflow.CustomProject> list = new ArrayList<>();
        String sql = "SELECT project_json FROM CUSTOM_PROJECTS ORDER BY sort_order ASC, created_timestamp DESC;";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            com.google.gson.Gson gson = createCustomProjectGson();
            while (rs.next()) {
                String json = rs.getString("project_json");
                if (json != null && !json.isEmpty()) {
                    try {
                        com.backtester.workflow.CustomProject proj = gson.fromJson(json, com.backtester.workflow.CustomProject.class);
                        if (proj != null) list.add(proj);
                    } catch (Exception ex) {
                        log.warn("Failed to parse CustomProject JSON: " + ex.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to load custom projects", e);
        }
        return list;
    }

    public static com.google.gson.Gson createCustomProjectGson() {
        return new com.google.gson.GsonBuilder()
                // MT5 reports may legitimately contain missing/undefined metrics.
                // Gson otherwise throws and silently prevents the whole project
                // (including custom databanks) from being persisted.
                .serializeSpecialFloatingPointValues()
                .setPrettyPrinting()
                .create();
    }

    public void deleteCustomProject(String id) {
        if (id == null || id.isEmpty()) return;
        String sql = "DELETE FROM CUSTOM_PROJECTS WHERE id = ?;";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.executeUpdate();
            log.info("Deleted custom project with id: {}", id);
        } catch (SQLException e) {
            log.error("Failed to delete custom project: " + id, e);
        }
    }
}
