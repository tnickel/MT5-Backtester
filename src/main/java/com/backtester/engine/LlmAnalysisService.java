package com.backtester.engine;

import com.backtester.database.DatabaseManager;
import com.backtester.report.OptimizationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;

/**
 * Calls OpenRouter API to analyze sensitivity data with an LLM.
 * API key is stored in the local SQLite database (user home), never in git.
 */
public class LlmAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(LlmAnalysisService.class);

    public static final String SETTING_API_KEY = "openrouter_api_key";
    public static final String SETTING_MODEL = "openrouter_model";
    public static final String SETTING_PROMPT = "openrouter_prompt";
    public static final String SETTING_MAX_TOKENS = "openrouter_max_tokens";
    public static final String SETTING_PERFORMANCE_WEIGHT = "openrouter_performance_weight";
    public static final String SETTING_STABILITY_WEIGHT = "openrouter_stability_weight";
    public static final double DEFAULT_PERFORMANCE_WEIGHT = 0.6;
    public static final double DEFAULT_STABILITY_WEIGHT = 0.4;
    public static final String DEFAULT_MODEL = "openai/gpt-4o-mini";
    public static final int DEFAULT_MAX_TOKENS = 16384;
    public static final String DEFAULT_PROMPT = "Du bekommst Sensitivitätsdaten und Performance-Metriken von N Strategien (Passes).\n" +
            "Du hast MEHR Daten als eine simple Formel: Kennlinien-Datenpunkte, Kurvenformen, Performance-Werte.\n" +
            "Nutze diese Informationen für eine TIEFERE Analyse als eine Formel liefern könnte!\n\n" +
            "Deine Antwort hat EXAKT 3 Teile:\n\n" +
            "TEIL 1: Eine einzige Markdown-Tabelle mit GENAU N Zeilen (eine pro Pass):\n" +
            "| Pass | Status | Score | Profit (BT/FW) | Trades (BT/FW) | CV worst | Fragile | Kurvenform | Fazit |\n" +
            "Status = ✅ Robust / ⚠️ Fragil / ❌ Überoptimiert\n" +
            "Score = Stabilitäts-Score 0-100 (Ganzzahl)\n" +
            "Profit (BT/FW) = Nettogewinn im Backtest und Forward, z.B. \"5200 / 1800\"\n" +
            "Trades (BT/FW) = Anzahl der Trades im Backtest und Forward, z.B. \"120 / 45\"\n" +
            "Kurvenform = dominante Form über alle Parameter: Plateau / Glocke / Peak / Cliff / Chaotisch\n" +
            "Fazit = MAX 5 Wörter\n\n" +
            "TEIL 2: GENAU N Zeilen in diesem Format (eine pro Pass):\n" +
            "STABILITY_SCORE|PassNummer|Score\n\n" +
            "TEIL 3: Begründung pro Strategie (JEDE auf EIGENER Zeile, getrennt durch Leerzeile!):\n" +
            "Format pro Zeile: **Pass XXXXX (Score):** 2-3 Sätze mit Analyse der Kurvenform und Konsistenz.\n\n" +
            "ANALYSE-ANLEITUNG (nutze ALLE verfügbaren Daten!):\n\n" +
            "1. KURVENFORM-ANALYSE (30% des Scores):\n" +
            "   Schau dir die Kennlinien-Datenpunkte an (z.B. '48→$850, 50→$980, 52→$950'):\n" +
            "   - Plateau: Werte ändern sich kaum bei Param-Variation → ROBUST (80-100)\n" +
            "   - Glocke: sanfter symmetrischer Abfall beidseitig → GUT (60-80)\n" +
            "   - Peak: ein einzelner hoher Wert, Nachbarn viel niedriger → ÜBEROPTIMIERT (<40)\n" +
            "   - Cliff: eine Seite stabil, andere bricht ein → RISKANT (40-60)\n" +
            "   - Chaotisch: Werte springen ohne Muster → INSTABIL (<30)\n" +
            "   Bewerte PRO PARAMETER separat, dann bilde Gesamturteil über alle Parameter.\n\n" +
            "2. CV-ANALYSE (25% des Scores):\n" +
            "   - avg_cv <15%=exzellent, 15-25%=gut, 25-40%=ok, >40%=schlecht\n" +
            "   - worst_cv >100% = starker Abzug\n\n" +
            "3. PERFORMANCE-KONTEXT (25% des Scores):\n" +
            "   - BT/FW Profit: FW_Profit > 0 UND proportional zu BT = Bonus\n" +
            "   - Drawdown: DD < 10% = Bonus, DD > 20% = Abzug\n" +
            "   - Profit Factor: PF > 1.5 = gut, PF > 2.0 = sehr gut\n" +
            "   - Trade-Anzahl: FW_Trades > 20 = statistisch signifikant\n" +
            "   - Combined_Score: hoher Score = gute Gesamtperformance\n\n" +
            "4. BT/FW KONSISTENZ (20% des Scores):\n" +
            "   - Vergleiche BT vs. FW Kennlinien desselben Parameters: gleiche Form = robust\n" +
            "   - BT CV vs. FW CV pro Parameter: ähnliche Werte = konsistent\n" +
            "   - Performance BT vs. FW: proportionale Ergebnisse = gut\n\n" +
            "BEISPIEL (mit 2 Passes):\n\n" +
            "Eingabe-Daten:\n" +
            "Pass 22244: BT_Profit=5200.00 | FW_Profit=1800.00 | BT_Trades=120 | FW_Trades=45 | BT_PF=1.85 | FW_PF=1.60 | Combined_Score=72.5\n" +
            "Pass 22244 | StopLoss | BT | CV=8.50% [ROBUST]\n" +
            "  Kennlinie: 40→$4800, 45→$5100, 50→$5200, 55→$5150, 60→$4900\n" +
            "Pass 22244 | StopLoss | FW | CV=12.30% [ROBUST]\n" +
            "  Kennlinie: 40→$1600, 45→$1750, 50→$1800, 55→$1780, 60→$1650\n\n" +
            "Erwartete Antwort:\n" +
            "| Pass | Status | Score | Profit (BT/FW) | Trades (BT/FW) | CV worst | Fragile | Kurvenform | Fazit |\n" +
            "|---|---|---|---|---|---|---|---|---|\n" +
            "| 22244 | ✅ Robust | 82 | 5200 / 1800 | 120 / 45 | 12.30% | 0 | Plateau | Breites Plateau, konsistent |\n\n" +
            "STABILITY_SCORE|22244|82\n\n" +
            "**Pass 22244 (82):** StopLoss zeigt breites Plateau (BT: $4800-$5200, FW: $1600-$1800). " +
            "BT und FW Kennlinien haben identische Glockenform. Niedrige CVs (8.5%/12.3%), guter PF (1.85/1.60).\n\n" +
            "REGELN:\n" +
            "- KEINE Einleitung, KEINE Begrüßung, KEIN zusätzlicher Text\n" +
            "- Anzahl Tabellenzeilen = Anzahl STABILITY_SCORE Zeilen = Anzahl Begründungen = N\n" +
            "- Nutze die volle Bandbreite 0-100, bewerte RELATIV zueinander\n" +
            "- Begründe Kurvenform-Urteil mit konkreten Datenpunkten aus den Kennlinien";

    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";

    public LlmAnalysisService() {
        // Auto-migrate old prompts that don't include the latest format
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String customPrompt = db.getSetting(SETTING_PROMPT);
            if (customPrompt != null && !customPrompt.isEmpty() && !customPrompt.contains("Profit (BT/FW)")) {
                log.info("Migrating old KI prompt to include profit and trade columns");
                db.saveSetting(SETTING_PROMPT, DEFAULT_PROMPT);
            }
        } catch (Exception e) {
            log.error("Failed to auto-migrate KI prompt", e);
        }
    }

    /**
     * Runs the full LLM analysis: loads data, builds prompt, calls API, returns response.
     *
     * @param activePasses pass numbers to analyze
     * @param expertName EA name
     * @param symbol trading symbol
     * @param performanceData optional performance metrics per pass (from CombinedPass). May be null.
     * @return The LLM's analysis text, or an error message.
     */
    public String analyzeStrategies(java.util.List<Integer> activePasses, String expertName, String symbol,
                                    java.util.Map<Integer, PassPerformance> performanceData) {
        DatabaseManager db = DatabaseManager.getInstance();

        // 1. Check API key
        String apiKey = db.getSetting(SETTING_API_KEY);
        if (apiKey == null || apiKey.isBlank()) {
            return "ERROR: Kein OpenRouter API-Key konfiguriert.\n\n" +
                   "Bitte klicke auf '⚙ KI-Einstellungen' und gib deinen API-Key ein.\n" +
                   "Du bekommst einen Key unter: https://openrouter.ai/keys";
        }

        String model = db.getSetting(SETTING_MODEL, DEFAULT_MODEL);
        String customPrompt = db.getSetting(SETTING_PROMPT, DEFAULT_PROMPT);
        int maxTokens = DEFAULT_MAX_TOKENS;
        try {
            String maxTokensSetting = db.getSetting(SETTING_MAX_TOKENS);
            if (maxTokensSetting != null && !maxTokensSetting.isBlank()) {
                maxTokens = Integer.parseInt(maxTokensSetting.trim());
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid max_tokens setting, using default: {}", DEFAULT_MAX_TOKENS);
        }

        // Auto-migrate old prompts that don't include the latest format
        if (customPrompt != null && !customPrompt.contains("Profit (BT/FW)")) {
            log.info("Migrating old KI prompt to include profit and trade columns");
            customPrompt = DEFAULT_PROMPT;
            db.saveSetting(SETTING_PROMPT, DEFAULT_PROMPT);
        }

        // 2. Load sensitivity data from DB (now includes curves + metrics)
        String sensitivityData = loadSensitivityData(activePasses, expertName, symbol, performanceData);
        if (sensitivityData == null || sensitivityData.isEmpty()) {
            return "ERROR: Keine Sensitivitätsdaten in der Datenbank für die ausgewählten Passes.\n\n" +
                   "Bitte führe zuerst eine Sensitivitätsanalyse im Backtester durch.";
        }

        // 3. Build the prompt
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(sensitivityData, customPrompt);

        // 4. Call OpenRouter
        log.info("Calling OpenRouter API with model: {} (max_tokens: {})", model, maxTokens);
        return callOpenRouter(apiKey, model, systemPrompt, userPrompt, maxTokens);
    }

    /**
     * Backwards-compatible overload without performance data.
     */
    public String analyzeStrategies(java.util.List<Integer> activePasses, String expertName, String symbol) {
        return analyzeStrategies(activePasses, expertName, symbol, null);
    }

    /**
     * Performance data for a single pass, extracted from CombinedPass.
     */
    public static class PassPerformance {
        public final double btProfit, fwProfit;
        public final int btTrades, fwTrades;
        public final double btPf, fwPf;
        public final double btDd, fwDd;
        public final double btRecovery, fwRecovery;
        public final double combinedScore;

        public PassPerformance(OptimizationResult.CombinedPass cp) {
            this.btProfit = cp.getBtProfit();
            this.fwProfit = cp.getFwProfit();
            this.btTrades = cp.getBtTrades();
            this.fwTrades = cp.getFwTrades();
            this.btPf = cp.getBtPf();
            this.fwPf = cp.getFwPf();
            this.btDd = cp.getBtDd();
            this.fwDd = cp.getFwDd();
            this.btRecovery = cp.getBtRecovery();
            this.fwRecovery = cp.getFwRecovery();
            this.combinedScore = cp.getScore();
        }
    }

    private String buildSystemPrompt() {
        return "Du bist ein erfahrener Quant-Analyst mit Expertise in Parameter-Sensitivitätsanalyse. " +
               "Antworte IMMER auf Deutsch. " +
               "Du analysierst Kennlinien-Datenpunkte, Kurvenformen und Performance-Metriken. " +
               "Deine Stärke: Du erkennst MUSTER in den Daten, die eine Formel nicht sehen kann — " +
               "z.B. ob ein Parameter ein breites Plateau hat oder nur einen spitzen Peak. " +
               "Starte SOFORT mit der Tabelle. KEINE Begrüßung, KEINE Einleitung.";
    }

    private String buildUserPrompt(String sensitivityData, String customPrompt) {
        return customPrompt + "\n\n" +
               "Hier sind die Ergebnisse aus meiner Datenbank:\n\n" +
               sensitivityData;
    }

    private String loadSensitivityData(java.util.List<Integer> activePasses, String expertName, String symbol,
                                        java.util.Map<Integer, PassPerformance> performanceData) {
        if (activePasses == null || activePasses.isEmpty()) {
            return null;
        }

        try {
            DatabaseManager db = DatabaseManager.getInstance();

            // Find the most recent run_timestamp for this expert and symbol to target the current run
            long latestTimestamp = -1;
            String tsSql = "SELECT MAX(run_timestamp) as max_ts FROM SENSITIVITY_DETAIL WHERE expert_name = ? AND symbol = ?";
            try (java.sql.Connection conn = db.getConnection();
                 java.sql.PreparedStatement pstmt = conn.prepareStatement(tsSql)) {
                pstmt.setString(1, expertName);
                pstmt.setString(2, symbol);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        latestTimestamp = rs.getLong("max_ts");
                    }
                }
            }

            boolean useTimestamp = (latestTimestamp > 0);
            StringBuilder inClause = new StringBuilder();
            for (int i = 0; i < activePasses.size(); i++) {
                if (i > 0) inClause.append(",");
                inClause.append(activePasses.get(i));
            }

            StringBuilder sb = new StringBuilder();

            // === SECTION 1: Performance Overview per Pass ===
            sb.append("=== PERFORMANCE-ÜBERSICHT ===\n");
            log.info("[LLM-Data] performanceData is {} with {} entries for {} passes",
                    performanceData == null ? "NULL" : "present",
                    performanceData != null ? performanceData.size() : 0,
                    activePasses.size());
            if (performanceData != null && !performanceData.isEmpty()) {
                for (Integer passNum : activePasses) {
                    PassPerformance perf = performanceData.get(passNum);
                    if (perf != null) {
                        sb.append(String.format(java.util.Locale.US,
                                "Pass %d: BT_Profit=%.2f | FW_Profit=%.2f | BT_Trades=%d | FW_Trades=%d | " +
                                "BT_PF=%.2f | FW_PF=%.2f | BT_DD=%.2f%% | FW_DD=%.2f%% | " +
                                "BT_Recovery=%.2f | FW_Recovery=%.2f | Combined_Score=%.1f\n",
                                passNum,
                                perf.btProfit, Double.isNaN(perf.fwProfit) ? 0.0 : perf.fwProfit,
                                perf.btTrades, perf.fwTrades,
                                perf.btPf, Double.isNaN(perf.fwPf) ? 0.0 : perf.fwPf,
                                perf.btDd, Double.isNaN(perf.fwDd) ? 0.0 : perf.fwDd,
                                perf.btRecovery, Double.isNaN(perf.fwRecovery) ? 0.0 : perf.fwRecovery,
                                perf.combinedScore));
                    }
                }
            } else {
                sb.append("(Keine Performance-Daten verfügbar)\n");
            }

            // === SECTION 2: Sensitivity Overview (aggregated CVs) ===
            sb.append("\n=== SENSITIVITÄTS-ÜBERSICHT ===\n");

            String overviewSql = "SELECT pass_number, pass_name, expert_name, symbol, " +
                    "ROUND(AVG(cv), 2) as avg_cv, ROUND(MAX(cv), 2) as worst_cv, " +
                    "SUM(CASE WHEN verdict='ROBUST' THEN 1 ELSE 0 END) as robust_count, " +
                    "SUM(CASE WHEN verdict='ACCEPTABLE' THEN 1 ELSE 0 END) as acceptable_count, " +
                    "SUM(CASE WHEN verdict='FRAGILE' THEN 1 ELSE 0 END) as fragile_count " +
                    "FROM SENSITIVITY_DETAIL " +
                    "WHERE expert_name = ? AND symbol = ? " +
                    (useTimestamp ? "AND run_timestamp = ? " : "") +
                    "AND pass_number IN (" + inClause + ") " +
                    "GROUP BY pass_number ORDER BY avg_cv";

            // === SECTION 3: Detailed parameter data WITH curves ===
            String detailSql = "SELECT pass_number, parameter_name, period, cv, verdict, " +
                    "base_value, base_profit, mean_profit, min_profit, max_profit, num_variants, curve_json " +
                    "FROM SENSITIVITY_DETAIL " +
                    "WHERE expert_name = ? AND symbol = ? " +
                    (useTimestamp ? "AND run_timestamp = ? " : "") +
                    "AND pass_number IN (" + inClause + ") " +
                    "ORDER BY pass_number, parameter_name, period";

            try (java.sql.Connection conn = db.getConnection();
                 java.sql.PreparedStatement pstmtOverview = conn.prepareStatement(overviewSql);
                 java.sql.PreparedStatement pstmtDetail = conn.prepareStatement(detailSql)) {

                pstmtOverview.setString(1, expertName);
                pstmtOverview.setString(2, symbol);
                int nextIdx = 3;
                if (useTimestamp) {
                    pstmtOverview.setLong(nextIdx++, latestTimestamp);
                }

                pstmtDetail.setString(1, expertName);
                pstmtDetail.setString(2, symbol);
                nextIdx = 3;
                if (useTimestamp) {
                    pstmtDetail.setLong(nextIdx++, latestTimestamp);
                }

                // Overview
                try (ResultSet rs = pstmtOverview.executeQuery()) {
                    while (rs.next()) {
                        sb.append(String.format(java.util.Locale.US,
                                "Pass %d (%s): avg_cv=%.2f%%, worst_cv=%.2f%%, robust=%d, acceptable=%d, fragile=%d\n",
                                rs.getInt("pass_number"), rs.getString("pass_name"),
                                rs.getDouble("avg_cv"), rs.getDouble("worst_cv"),
                                rs.getInt("robust_count"), rs.getInt("acceptable_count"), rs.getInt("fragile_count")));
                    }
                }

                sb.append("\n=== PARAMETER-DETAILS MIT KENNLINIEN ===\n");

                // Detailed data with curve points
                try (ResultSet rs = pstmtDetail.executeQuery()) {
                    while (rs.next()) {
                        int passNum = rs.getInt("pass_number");
                        String paramName = rs.getString("parameter_name");
                        String period = rs.getString("period");
                        String baseValue = rs.getString("base_value");
                        double cv = rs.getDouble("cv");
                        String verdict = rs.getString("verdict");
                        double baseProfit = rs.getDouble("base_profit");
                        double meanProfit = rs.getDouble("mean_profit");
                        double minProfit = rs.getDouble("min_profit");
                        double maxProfit = rs.getDouble("max_profit");
                        int numVariants = rs.getInt("num_variants");
                        String curveJson = rs.getString("curve_json");

                        sb.append(String.format(java.util.Locale.US,
                                "\nPass %d | %s | %s | base=%s | CV=%.2f%% [%s]\n",
                                passNum, paramName, period, baseValue, cv, verdict));
                        sb.append(String.format(java.util.Locale.US,
                                "  Profit: base=%.2f, mean=%.2f, min=%.2f, max=%.2f (%d Varianten)\n",
                                baseProfit, meanProfit, minProfit, maxProfit, numVariants));

                        // Parse and format curve data points compactly
                        if (curveJson != null && !curveJson.isEmpty() && !curveJson.equals("[]")) {
                            String compactCurve = formatCurveCompact(curveJson);
                            if (compactCurve != null && !compactCurve.isEmpty()) {
                                sb.append("  Kennlinie: ").append(compactCurve).append("\n");
                            }
                        }
                    }
                }
            }

            String result = sb.toString();
            log.info("Loaded enhanced sensitivity data for LLM: {} characters", result.length());
            return result;

        } catch (Exception e) {
            log.error("Failed to load sensitivity data", e);
            return null;
        }
    }

    /**
     * Formats curve JSON data into a compact human-readable string.
     * Input:  [{"paramValue":1.50000,"profit":850.00},{"paramValue":2.00000,"profit":920.00},...]
     * Output: 1.5→$850, 2.0→$920, ...
     */
    private String formatCurveCompact(String curveJson) {
        try {
            com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(curveJson).getAsJsonArray();
            if (arr.size() == 0) return null;

            StringBuilder curve = new StringBuilder();
            for (int i = 0; i < arr.size(); i++) {
                com.google.gson.JsonObject point = arr.get(i).getAsJsonObject();
                double paramVal = point.get("paramValue").getAsDouble();
                double profit = point.get("profit").getAsDouble();

                if (i > 0) curve.append(", ");

                // Format param value: remove trailing zeros
                String paramStr;
                if (paramVal == Math.floor(paramVal) && !Double.isInfinite(paramVal)) {
                    paramStr = String.valueOf((long) paramVal);
                } else {
                    paramStr = String.format(java.util.Locale.US, "%.2f", paramVal);
                }

                curve.append(String.format(java.util.Locale.US, "%s→$%.0f", paramStr, profit));
            }
            return curve.toString();
        } catch (Exception e) {
            log.warn("Failed to parse curve JSON for LLM: {}", e.getMessage());
            return null;
        }
    }

    private String callOpenRouter(String apiKey, String model, String systemPrompt, String userPrompt, int maxTokens) {
        try {
            // Build JSON body manually to avoid Gson dependency issues
            String jsonBody = "{"
                    + "\"model\":\"" + escapeJson(model) + "\","
                    + "\"messages\":["
                    + "{\"role\":\"system\",\"content\":\"" + escapeJson(systemPrompt) + "\"},"
                    + "{\"role\":\"user\",\"content\":\"" + escapeJson(userPrompt) + "\"}"
                    + "],"
                    + "\"max_tokens\":" + maxTokens + ","
                    + "\"temperature\":0.0"
                    + "}";

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENROUTER_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("HTTP-Referer", "https://mt5-backtester.local")
                    .header("X-Title", "MT5 Backtester Sensitivity Analysis")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(120))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("OpenRouter API error: {} - {}", response.statusCode(), response.body());
                return "ERROR: OpenRouter API Fehler (HTTP " + response.statusCode() + ")\n\n" +
                       parseErrorMessage(response.body());
            }

            // Parse response - extract content from JSON
            return extractContent(response.body());

        } catch (java.net.http.HttpTimeoutException e) {
            return "ERROR: Timeout - Die KI-Analyse hat zu lange gedauert (> 120 Sekunden).\n" +
                   "Versuche es erneut oder wähle ein schnelleres Modell.";
        } catch (Exception e) {
            log.error("LLM API call failed", e);
            return "ERROR: API-Aufruf fehlgeschlagen: " + e.getMessage();
        }
    }

    private String extractContent(String json) {
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            if (root.has("choices")) {
                com.google.gson.JsonArray choices = root.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    com.google.gson.JsonObject choice = choices.get(0).getAsJsonObject();
                    
                    String finishReason = choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull() 
                                          ? choice.get("finish_reason").getAsString() : "";
                    
                    if (choice.has("message")) {
                        com.google.gson.JsonObject message = choice.getAsJsonObject("message");
                        if (message.has("refusal") && !message.get("refusal").isJsonNull()) {
                            return "⚠️ **Das Modell hat die Antwort verweigert.**\n\nGrund: " + message.get("refusal").getAsString() + 
                                   "\n\nTipp: Wähle in den KI-Einstellungen ein anderes Modell (z.B. openai/gpt-4o-mini), das weniger strenge Filter für Finanzthemen hat.";
                        }
                        
                        StringBuilder result = new StringBuilder();
                        
                        // Handle "Thinking" models (like DeepSeek-R1 or Kimi)
                        if (message.has("reasoning") && !message.get("reasoning").isJsonNull()) {
                            result.append("> **Gedankengänge der KI:**\n");
                            String reasoning = message.get("reasoning").getAsString();
                            result.append("> ").append(reasoning.replace("\n", "\n> "));
                            result.append("\n\n---\n\n");
                        }
                        
                        if (message.has("content") && !message.get("content").isJsonNull()) {
                            result.append(message.get("content").getAsString());
                        } else if (result.length() == 0) {
                            return "ERROR: Keine Antwort generiert. (Finish reason: " + finishReason + ")";
                        }
                        
                        if ("length".equals(finishReason)) {
                            result.append("\n\n⚠️ **Achtung: Die Antwort wurde abgeschnitten, da das Modell zu viel geschrieben hat (Maximales Token-Limit erreicht).**");
                        }
                        
                        return result.toString();
                    }
                }
            }
            return "ERROR: Unerwartetes API-Antwortformat.\n\n" + json;
        } catch (Exception e) {
            return "ERROR: Konnte Antwort nicht parsen.\n\n" + e.getMessage() + "\n" + json;
        }
    }

    private String parseErrorMessage(String json) {
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            if (root.has("error")) {
                com.google.gson.JsonObject error = root.getAsJsonObject("error");
                if (error.has("message")) {
                    return error.get("message").getAsString();
                }
            }
            return json;
        } catch (Exception e) {
            return json;
        }
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
