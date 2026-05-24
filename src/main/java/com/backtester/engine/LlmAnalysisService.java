package com.backtester.engine;

import com.backtester.database.DatabaseManager;
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
    public static final String DEFAULT_MODEL = "openai/gpt-4o-mini";
    public static final int DEFAULT_MAX_TOKENS = 16384;
    public static final String DEFAULT_PROMPT = "Du bekommst Sensitivitätsdaten von N Strategien (Passes).\n" +
            "Deine Antwort hat EXAKT 3 Teile:\n\n" +
            "TEIL 1: Eine einzige Markdown-Tabelle mit GENAU N Zeilen (eine pro Pass):\n" +
            "| Pass | Status | Score | CV worst | Fragile | Fazit |\n" +
            "Status = ✅ Robust / ⚠️ Fragil / ❌ Überoptimiert\n" +
            "Score = Stabilitäts-Score 0-100 (Ganzzahl)\n" +
            "Fazit = MAX 5 Wörter\n\n" +
            "TEIL 2: GENAU N Zeilen in diesem Format (eine pro Pass):\n" +
            "STABILITY_SCORE|PassNummer|Score\n\n" +
            "TEIL 3: Kurze Begründung pro Strategie (JEDE auf EIGENER Zeile, getrennt durch Leerzeile!):\n" +
            "Format pro Zeile: **Pass XXXXX (Score):** 1-2 kurze Sätze.\n\n" +
            "Score-Berechnung (RELATIV bewerten, volle Bandbreite 0-100 nutzen!):\n" +
            "- avg_cv (40%): <15%=exzellent(80-100), 15-25%=gut(60-79), 25-40%=ok(40-59), >40%=schlecht(<40)\n" +
            "- Fragile Parameter (25%): 0=ideal, je fragiler desto weniger Punkte\n" +
            "- worst_cv (15%): >100% = starker Abzug\n" +
            "- BT/FW Konsistenz (20%): ähnliche Performance = Bonus\n\n" +
            "Beispiel bei 3 Strategien:\n" +
            "| 22244 | ✅ Robust | 82 | 70.40% | 1 | Stabil, konsistent |\n" +
            "| 22228 | ⚠️ Fragil | 55 | 82.83% | 2 | Akzeptabel, FW schwach |\n" +
            "| 27450 | ❌ Überoptimiert | 18 | 186.30% | 3 | Sehr instabil |\n\n" +
            "STABILITY_SCORE|22244|82\n" +
            "STABILITY_SCORE|22228|55\n" +
            "STABILITY_SCORE|27450|18\n\n" +
            "**Pass 22244 (82):** Niedriger avg_cv, nur 1 fragiler Parameter. BT/FW konsistent.\n\n" +
            "**Pass 22228 (55):** Akzeptabler avg_cv, 2 fragile Parameter. FW schwächer als BT.\n\n" +
            "**Pass 27450 (18):** worst_cv 186%, 3 fragile Parameter. Klar überoptimiert.\n\n" +
            "REGELN:\n" +
            "- KEINE Einleitung, KEINE Begrüßung, KEIN zusätzlicher Text\n" +
            "- Anzahl Tabellenzeilen = Anzahl STABILITY_SCORE Zeilen = Anzahl Begründungen = N";

    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";

    /**
     * Runs the full LLM analysis: loads data, builds prompt, calls API, returns response.
     *
     * @return The LLM's analysis text, or an error message.
     */
    public String analyzeStrategies(java.util.List<Integer> activePasses, String expertName, String symbol) {
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
        if (customPrompt != null && !customPrompt.contains("KEINE Einleitung")) {
            log.info("Migrating old KI prompt to new compact format");
            customPrompt = DEFAULT_PROMPT;
            db.saveSetting(SETTING_PROMPT, DEFAULT_PROMPT);
        }

        // 2. Load sensitivity data from DB
        String sensitivityData = loadSensitivityData(activePasses, expertName, symbol);
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

    private String buildSystemPrompt() {
        return "Du bist ein Quant-Analyst. Antworte IMMER auf Deutsch. " +
               "Deine Antwort besteht NUR aus einer Markdown-Tabelle und STABILITY_SCORE Zeilen. " +
               "KEINE Begrüßung, KEINE Einleitung, KEINE Erklärungen, KEIN Fließtext. " +
               "Starte SOFORT mit der Tabelle. Halte dich extrem kurz.";
    }

    private String buildUserPrompt(String sensitivityData, String customPrompt) {
        return customPrompt + "\n\n" +
               "Hier sind die Ergebnisse aus meiner Datenbank:\n\n" +
               sensitivityData;
    }

    private String loadSensitivityData(java.util.List<Integer> activePasses, String expertName, String symbol) {
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

            // Get overview for active passes
            StringBuilder sb = new StringBuilder();
            sb.append("=== STRATEGIEN-ÜBERSICHT ===\n");

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

            String detailSql = "SELECT pass_number, parameter_name, period, cv, verdict, " +
                    "base_value " +
                    "FROM SENSITIVITY_DETAIL " +
                    "WHERE expert_name = ? AND symbol = ? " +
                    (useTimestamp ? "AND run_timestamp = ? " : "") +
                    "AND pass_number IN (" + inClause + ") " +
                    "ORDER BY pass_number, period, cv DESC";

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
                        sb.append(String.format("Pass %d (%s) - %s %s: avg_cv=%.2f%%, worst_cv=%.2f%%, robust=%d, acceptable=%d, fragile=%d\n",
                                rs.getInt("pass_number"), rs.getString("pass_name"),
                                rs.getString("expert_name"), rs.getString("symbol"),
                                rs.getDouble("avg_cv"), rs.getDouble("worst_cv"),
                                rs.getInt("robust_count"), rs.getInt("acceptable_count"), rs.getInt("fragile_count")));
                    }
                }

                sb.append("\n=== PARAMETER-DETAILS ===\n");

                // Details (compact: only CV and verdict per parameter)
                try (ResultSet rs = pstmtDetail.executeQuery()) {
                    while (rs.next()) {
                        sb.append(String.format(
                                "Pass %d | %s | %s | base=%s | CV=%.2f%% [%s]\n",
                                rs.getInt("pass_number"),
                                rs.getString("parameter_name"),
                                rs.getString("period"),
                                rs.getString("base_value"),
                                rs.getDouble("cv"),
                                rs.getString("verdict")
                        ));
                    }
                }
            }

            String result = sb.toString();
            log.info("Loaded sensitivity data for LLM: {} characters", result.length());
            return result;

        } catch (Exception e) {
            log.error("Failed to load sensitivity data", e);
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
