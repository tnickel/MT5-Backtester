package com.backtester.report;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates a clean Table-based Databank HTML Viewer with sortable column headers,
 * thick rows containing metrics, action buttons for MetaTrader, and wide equity curve charts at the end of each row.
 */
public class DatabankHtmlViewerGenerator {

    private static final Logger log = LoggerFactory.getLogger(DatabankHtmlViewerGenerator.class);
    private static final Gson gson = new GsonBuilder().create();

    public static Path generate(String databankName, List<CombinedPass> passes, Path outputDirectory) throws IOException {
        return generate(databankName, passes, outputDirectory, null, null, null);
    }

    public static Path generate(String databankName, List<CombinedPass> passes, Path outputDirectory,
                                String expert, String symbol, String period) throws IOException {
        return generate(databankName, null, passes, outputDirectory, expert, symbol, period, null);
    }

    public static Path generate(String databankName, String shortTermDatabankName,
                                List<CombinedPass> passes, Path outputDirectory,
                                String expert, String symbol, String period,
                                String accessToken) throws IOException {

        if (outputDirectory == null) {
            outputDirectory = java.nio.file.Paths.get("backtest_reports");
        }
        Files.createDirectories(outputDirectory);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String safeDbName = databankName != null ? databankName.replaceAll("[^a-zA-Z0-9_\\-]", "_") : "databank";
        String fileName = "databank_" + safeDbName + "_" + timestamp + ".html";
        Path reportPath = outputDirectory.resolve(fileName);

        if (passes == null) {
            passes = Collections.emptyList();
        }

        if ((expert == null || expert.isEmpty()) && !passes.isEmpty()) {
            Pass bt = passes.get(0).getBacktestPass();
            if (bt != null && bt.getParameter("Expert") != null) {
                expert = bt.getParameter("Expert");
            }
        }
        if ((symbol == null || symbol.isEmpty()) && !passes.isEmpty()) {
            symbol = passes.get(0).getSymbol();
        }

        String formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"de\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>Databank Viewer: ").append(escapeHtml(databankName)).append(" (").append(passes.size()).append(" Strategien)</title>\n");
        html.append("  <style>\n");
        html.append("    :root {\n");
        html.append("      --bg-dark: #0b0d13;\n");
        html.append("      --card-bg: #141822;\n");
        html.append("      --card-border: #232a3b;\n");
        html.append("      --accent-cyan: #00e5ff;\n");
        html.append("      --accent-green: #10b981;\n");
        html.append("      --accent-gold: #ffd740;\n");
        html.append("      --accent-purple: #a78bfa;\n");
        html.append("      --text-main: #e6e9f0;\n");
        html.append("      --text-muted: #94a3b8;\n");
        html.append("    }\n");
        html.append("    * { box-sizing: border-box; margin: 0; padding: 0; }\n");
        html.append("    body {\n");
        html.append("      background-color: var(--bg-dark);\n");
        html.append("      color: var(--text-main);\n");
        html.append("      font-family: 'Segoe UI', system-ui, -apple-system, sans-serif;\n");
        html.append("      line-height: 1.4;\n");
        html.append("      padding: 20px;\n");
        html.append("    }\n");
        html.append("    .header {\n");
        html.append("      background: var(--card-bg);\n");
        html.append("      border: 1px solid var(--card-border);\n");
        html.append("      border-radius: 12px;\n");
        html.append("      padding: 20px 24px;\n");
        html.append("      margin-bottom: 20px;\n");
        html.append("      box-shadow: 0 4px 20px rgba(0,0,0,0.4);\n");
        html.append("    }\n");
        html.append("    .header-top {\n");
        html.append("      display: flex;\n");
        html.append("      justify-content: space-between;\n");
        html.append("      align-items: center;\n");
        html.append("      flex-wrap: wrap;\n");
        html.append("      gap: 12px;\n");
        html.append("      margin-bottom: 14px;\n");
        html.append("    }\n");
        html.append("    h1 {\n");
        html.append("      color: var(--accent-cyan);\n");
        html.append("      font-size: 22px;\n");
        html.append("      font-weight: 700;\n");
        html.append("    }\n");
        html.append("    .meta-tag {\n");
        html.append("      background: #1e2536;\n");
        html.append("      border: 1px solid #2d374d;\n");
        html.append("      padding: 5px 10px;\n");
        html.append("      border-radius: 6px;\n");
        html.append("      font-size: 13px;\n");
        html.append("      color: var(--text-muted);\n");
        html.append("    }\n");
        html.append("    .meta-tag strong { color: var(--text-main); }\n");
        html.append("    .controls-bar {\n");
        html.append("      display: flex;\n");
        html.append("      gap: 16px;\n");
        html.append("      align-items: center;\n");
        html.append("      background: #10131c;\n");
        html.append("      padding: 12px;\n");
        html.append("      border-radius: 8px;\n");
        html.append("      border: 1px solid var(--card-border);\n");
        html.append("    }\n");
        html.append("    input {\n");
        html.append("      background: #1a202c;\n");
        html.append("      border: 1px solid #2d3748;\n");
        html.append("      color: var(--text-main);\n");
        html.append("      padding: 8px 12px;\n");
        html.append("      border-radius: 6px;\n");
        html.append("      font-size: 14px;\n");
        html.append("      outline: none;\n");
        html.append("      width: 320px;\n");
        html.append("    }\n");
        html.append("    input:focus { border-color: var(--accent-cyan); }\n");

        html.append("    /* Sortable Table Styles */\n");
        html.append("    table.databank-table {\n");
        html.append("      width: 100%;\n");
        html.append("      border-collapse: separate;\n");
        html.append("      border-spacing: 0 10px;\n");
        html.append("    }\n");
        html.append("    table.databank-table th {\n");
        html.append("      background: #141822;\n");
        html.append("      color: var(--accent-cyan);\n");
        html.append("      padding: 12px 14px;\n");
        html.append("      font-size: 13px;\n");
        html.append("      font-weight: 700;\n");
        html.append("      text-align: left;\n");
        html.append("      border-bottom: 2px solid var(--card-border);\n");
        html.append("      cursor: pointer;\n");
        html.append("      user-select: none;\n");
        html.append("      white-space: nowrap;\n");
        html.append("    }\n");
        html.append("    table.databank-table th:hover { color: #ffffff; background: #1a202c; }\n");
        html.append("    table.databank-table th.sort-asc::after { content: \" ▲\"; color: var(--accent-gold); }\n");
        html.append("    table.databank-table th.sort-desc::after { content: \" ▼\"; color: var(--accent-gold); }\n");

        html.append("    table.databank-table tr.strat-row {\n");
        html.append("      background: var(--card-bg);\n");
        html.append("      box-shadow: 0 2px 8px rgba(0,0,0,0.3);\n");
        html.append("      transition: background 0.15s ease;\n");
        html.append("    }\n");
        html.append("    table.databank-table tr.strat-row:hover {\n");
        html.append("      background: #1c2230;\n");
        html.append("    }\n");
        html.append("    table.databank-table td {\n");
        html.append("      padding: 12px 14px;\n");
        html.append("      vertical-align: middle;\n");
        html.append("      border-top: 1px solid var(--card-border);\n");
        html.append("      border-bottom: 1px solid var(--card-border);\n");
        html.append("      font-size: 13px;\n");
        html.append("    }\n");
        html.append("    table.databank-table td:first-child {\n");
        html.append("      border-left: 1px solid var(--card-border);\n");
        html.append("      border-top-left-radius: 8px;\n");
        html.append("      border-bottom-left-radius: 8px;\n");
        html.append("    }\n");
        html.append("    table.databank-table td:last-child {\n");
        html.append("      border-right: 1px solid var(--card-border);\n");
        html.append("      border-top-right-radius: 8px;\n");
        html.append("      border-bottom-right-radius: 8px;\n");
        html.append("    }\n");

        html.append("    .strat-title {\n");
        html.append("      font-size: 15px;\n");
        html.append("      font-weight: 700;\n");
        html.append("      color: var(--text-main);\n");
        html.append("      display: block;\n");
        html.append("    }\n");
        html.append("    .pass-num {\n");
        html.append("      font-size: 12px;\n");
        html.append("      color: var(--text-muted);\n");
        html.append("    }\n");
        html.append("    .score-badge {\n");
        html.append("      display: inline-block;\n");
        html.append("      padding: 3px 8px;\n");
        html.append("      border-radius: 12px;\n");
        html.append("      font-weight: 700;\n");
        html.append("      font-size: 12px;\n");
        html.append("      margin-top: 4px;\n");
        html.append("    }\n");
        html.append("    .score-high { background: rgba(16, 185, 129, 0.2); color: #10b981; border: 1px solid #10b981; }\n");
        html.append("    .score-mid  { background: rgba(255, 215, 64, 0.2); color: #ffd740; border: 1px solid #ffd740; }\n");
        html.append("    .score-low  { background: rgba(148, 163, 184, 0.2); color: #94a3b8; border: 1px solid #94a3b8; }\n");

        html.append("    .btn-run-backtest {\n");
        html.append("      background: #10b981;\n");
        html.append("      color: #ffffff;\n");
        html.append("      font-weight: 700;\n");
        html.append("      border: none;\n");
        html.append("      padding: 8px 12px;\n");
        html.append("      border-radius: 6px;\n");
        html.append("      cursor: pointer;\n");
        html.append("      font-size: 12px;\n");
        html.append("      white-space: nowrap;\n");
        html.append("      transition: background 0.15s ease;\n");
        html.append("    }\n");
        html.append("    .btn-run-backtest:hover { background: #059669; }\n");
        html.append("    .btn-run-backtest:disabled { background: #4b5563; cursor: not-allowed; }\n");

        html.append("    .metric-val {\n");
        html.append("      font-weight: 700;\n");
        html.append("      font-size: 14px;\n");
        html.append("    }\n");
        html.append("    .metric-sub {\n");
        html.append("      font-size: 11px;\n");
        html.append("      color: var(--text-muted);\n");
        html.append("    }\n");

        html.append("    .chart-cell-container {\n");
        html.append("      min-height: 112px;\n");
        html.append("      width: 420px;\n");
        html.append("      background: #0f121a;\n");
        html.append("      border-radius: 6px;\n");
        html.append("      padding: 4px;\n");
        html.append("    }\n");
        html.append("    .chart-meta { display: block; margin-top: 3px; color: var(--text-muted); font-size: 10px; text-align: center; }\n");

        html.append("    details summary {\n");
        html.append("      cursor: pointer;\n");
        html.append("      color: var(--accent-gold);\n");
        html.append("      font-size: 12px;\n");
        html.append("      font-weight: 600;\n");
        html.append("    }\n");
        html.append("    .params-grid {\n");
        html.append("      display: grid;\n");
        html.append("      grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));\n");
        html.append("      gap: 4px;\n");
        html.append("      margin-top: 6px;\n");
        html.append("      background: #0f121a;\n");
        html.append("      padding: 6px;\n");
        html.append("      border-radius: 4px;\n");
        html.append("    }\n");
        html.append("    .param-item { font-family: monospace; font-size: 11px; color: var(--text-muted); }\n");
        html.append("    .param-val { color: var(--accent-cyan); font-weight: 700; }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");

        html.append("<div class=\"header\">\n");
        html.append("  <div class=\"header-top\">\n");
        html.append("    <h1>🌐 Databank Report: ").append(escapeHtml(databankName)).append("</h1>\n");
        html.append("    <div>\n");
        if (expert != null && !expert.isEmpty()) {
            html.append("      <span class=\"meta-tag\">EA: <strong>").append(escapeHtml(expert)).append("</strong></span>\n");
        }
        if (symbol != null && !symbol.isEmpty()) {
            html.append("      <span class=\"meta-tag\">Symbol: <strong>").append(escapeHtml(symbol)).append("</strong></span>\n");
        }
        if (shortTermDatabankName != null && !shortTermDatabankName.isBlank()) {
            html.append("      <span class=\"meta-tag\">Kurzzeitdaten: <strong>")
                    .append(escapeHtml(shortTermDatabankName)).append("</strong></span>\n");
        }
        html.append("      <span class=\"meta-tag\">Strategien: <strong>").append(passes.size()).append("</strong></span>\n");
        html.append("      <span class=\"meta-tag\">Erstellt: <strong>").append(formattedDate).append("</strong></span>\n");
        html.append("    </div>\n");
        html.append("  </div>\n");

        html.append("  <div class=\"controls-bar\">\n");
        html.append("    <input type=\"text\" id=\"searchInput\" placeholder=\"🔍 Suche nach Pass #, Name oder Parameter...\" onkeyup=\"filterTable()\">\n");
        html.append("    <span style=\"color: var(--text-muted); font-size: 13px;\">Tipp: Klicke auf die Spaltenköpfe, um die Tabelle zu sortieren!</span>\n");
        html.append("  </div>\n");
        html.append("</div>\n");

        // Table Start
        html.append("<table class=\"databank-table\" id=\"databankTable\">\n");
        html.append("  <thead>\n");
        html.append("    <tr>\n");
        html.append("      <th onclick=\"sortTable(0, 'number')\"># Pass & Score</th>\n");
        html.append("      <th>MT5 Aktion</th>\n");
        html.append("      <th onclick=\"sortTable(2, 'number')\">Backtest Profit (IS)</th>\n");
        html.append("      <th onclick=\"sortTable(3, 'number')\">Forward Profit (OOS)</th>\n");
        html.append("      <th onclick=\"sortTable(4, 'number')\">Retest Profit (LT)</th>\n");
        html.append("      <th onclick=\"sortTable(5, 'number')\">Trades & PF</th>\n");
        html.append("      <th onclick=\"sortTable(6, 'number')\">Max Drawdown %</th>\n");
        html.append("      <th>EA Parameter</th>\n");
        html.append("      <th style=\"width: 430px;\">MT5 Equity & Balance Grafik</th>\n");
        html.append("    </tr>\n");
        html.append("  </thead>\n");
        html.append("  <tbody id=\"tableBody\">\n");

        // Resolve the real MT5 artifacts before rendering; plausible-looking
        // synthetic curves must never be presented as actual backtest output.
        Map<Integer, ImageCandidate> passImageMap = scanAllImages(outputDirectory, passes, expert, symbol, period);

        for (CombinedPass pass : passes) {
            double score = pass.getScore();
            String scoreClass = score >= 70 ? "score-high" : (score >= 40 ? "score-mid" : "score-low");
            String stratName = pass.getStrategyName() != null ? pass.getStrategyName() : ("Strat " + pass.getPassNumber());

            Pass btPass = pass.getBacktestPass();
            ImageCandidate imageCandidate = passImageMap.get(pass.getPassNumber());
            double displayedLtDd = pass.getLtDd();
            if (imageCandidate != null && imageCandidate.matchesLongtermRange()
                    && imageCandidate.maxDrawdownPercent() != null) {
                displayedLtDd = imageCandidate.maxDrawdownPercent();
            }

            html.append("    <tr class=\"strat-row\" ")
                    .append("data-pass=\"").append(pass.getPassNumber()).append("\" ")
                    .append("data-score=\"").append(score).append("\" ")
                    .append("data-profit=\"").append(pass.getBtProfit()).append("\" ")
                    .append("data-fwprofit=\"").append(!Double.isNaN(pass.getFwProfit()) ? pass.getFwProfit() : -999999).append("\" ")
                    .append("data-ltprofit=\"").append(!Double.isNaN(pass.getLtProfit()) ? pass.getLtProfit() : -999999).append("\" ")
                    .append("data-trades=\"").append(pass.getBtTrades()).append("\" ")
                    .append("data-dd=\"").append(pass.getBtDd()).append("\">\n");

            // Col 0: Pass & Score
            html.append("      <td>\n");
            html.append("        <span class=\"strat-title\">").append(escapeHtml(stratName)).append("</span>\n");
            html.append("        <span class=\"pass-num\">Pass #").append(pass.getPassNumber()).append("</span><br>\n");
            html.append("        <span class=\"score-badge ").append(scoreClass).append("\">Score: ").append(String.format(Locale.US, "%.1f", score)).append("</span>\n");
            html.append("      </td>\n");

            // Col 1: Action Button
            html.append("      <td>\n");
            html.append("        <button class=\"btn-run-backtest\" onclick=\"runSingleBacktest(event)\" data-pass=\"")
                    .append(pass.getPassNumber()).append("\" data-db=\"")
                    .append(escapeHtml(databankName)).append("\" data-name=\"")
                    .append(escapeHtml(stratName)).append("\" data-artifact=\"")
                    .append(imageCandidate != null
                            ? escapeHtml(imageCandidate.path().getParent().getFileName().toString()) : "")
                    .append("\">")
                    .append("▶ MT5 Backtest</button>\n");
            html.append("      </td>\n");

            // Col 2: BT Profit (IS = Blau)
            html.append("      <td>\n");
            html.append("        <span class=\"metric-val\" style=\"color: #3b82f6;\">$").append(String.format(Locale.US, "%.2f", pass.getBtProfit())).append("</span><br>\n");
            html.append("        <span class=\"metric-sub\">").append(escapeHtml(pass.getBtDateRange())).append("</span>\n");
            html.append("      </td>\n");

            // Col 3: FW Profit (OOS = Gelb)
            html.append("      <td>\n");
            if (!Double.isNaN(pass.getFwProfit())) {
                html.append("        <span class=\"metric-val\" style=\"color: #ffd740;\">$").append(String.format(Locale.US, "%.2f", pass.getFwProfit())).append("</span><br>\n");
                html.append("        <span class=\"metric-sub\">").append(escapeHtml(pass.getFwDateRange())).append("</span>\n");
            } else {
                html.append("        <span class=\"metric-sub\">-</span>\n");
            }
            html.append("      </td>\n");

            // Col 4: Retest Profit (LT = Lila)
            html.append("      <td>\n");
            if (!Double.isNaN(pass.getLtProfit())) {
                html.append("        <span class=\"metric-val\" style=\"color: #a78bfa;\">$").append(String.format(Locale.US, "%.2f", pass.getLtProfit())).append("</span><br>\n");
                html.append("        <span class=\"metric-sub\">").append(escapeHtml(pass.getLtDateRange())).append("</span>\n");
            } else {
                html.append("        <span class=\"metric-sub\">-</span>\n");
            }
            html.append("      </td>\n");

            // Col 5: Trades & PF
            html.append("      <td>\n");
            html.append("        <span class=\"metric-val\">").append(pass.getBtTrades()).append(" Trades</span><br>\n");
            html.append("        <span class=\"metric-sub\">PF: ").append(String.format(Locale.US, "%.2f", pass.getBtPf())).append("</span>\n");
            html.append("      </td>\n");

            // Col 6: Max DD % (3 Drawdowns: BT, FW, LT)
            html.append("      <td>\n");
            html.append("        <span class=\"metric-val\" style=\"color: #3b82f6;\">BT: ").append(String.format(Locale.US, "%.2f%%", pass.getBtDd())).append("</span><br>\n");
            if (!Double.isNaN(pass.getFwDd())) {
                html.append("        <span class=\"metric-sub\" style=\"color: #ffd740; font-weight:600;\">FW: ").append(String.format(Locale.US, "%.2f%%", pass.getFwDd())).append("</span><br>\n");
            }
            if (!Double.isNaN(displayedLtDd)) {
                html.append("        <span class=\"metric-sub\" style=\"color: #a78bfa; font-weight:600;\">LT: ").append(String.format(Locale.US, "%.2f%%", displayedLtDd)).append("</span>\n");
            }
            html.append("      </td>\n");

            // Col 7: EA Parameters
            html.append("      <td>\n");
            if (btPass != null && btPass.getParameterValues() != null && !btPass.getParameterValues().isEmpty()) {
                Map<String, String> pVals = btPass.getParameterValues();
                html.append("        <details>\n");
                html.append("          <summary>⚙️ ").append(pVals.size()).append(" Params</summary>\n");
                html.append("          <div class=\"params-grid\">\n");
                for (Map.Entry<String, String> entry : pVals.entrySet()) {
                    html.append("            <div class=\"param-item\">")
                            .append(escapeHtml(entry.getKey())).append(": <span class=\"param-val\">")
                            .append(escapeHtml(entry.getValue())).append("</span></div>\n");
                }
                html.append("          </div>\n");
                html.append("        </details>\n");
            } else {
                html.append("        <span class=\"metric-sub\">-</span>\n");
            }
            html.append("      </td>\n");

            // Col 8: verified MT5 image; never substitute a synthetic curve.
            Path imgPath = imageCandidate != null ? imageCandidate.path() : null;
            html.append("      <td class=\"chart-cell\">\n");
            html.append("        <div class=\"chart-cell-container\">\n");
            if (imgPath != null) {
                String imgSrc = getRelativeOrUriPath(reportPath, imgPath);
                html.append("          <a href=\"").append(escapeHtml(imgSrc))
                        .append("\" target=\"_blank\" rel=\"noopener noreferrer\"><img src=\"")
                        .append(escapeHtml(imgSrc)).append("\" ")
                        .append("alt=\"MT5 Equity Chart\" ")
                        .append("style=\"max-height: 88px; max-width: 410px; width: auto; height: auto; border-radius: 4px; cursor: pointer; object-fit: contain;\" ")
                        .append("title=\"Klicken für Vollbild\" /></a>\n");
                html.append("          <small class=\"chart-meta\">")
                        .append(escapeHtml(imageCandidate.testKind()))
                        .append(" · ").append(escapeHtml(formatArtifactTimestamp(imageCandidate.modifiedMillis())));
                if (imageCandidate.maxDrawdownPercent() != null) {
                    html.append(" · DD ").append(String.format(Locale.US, "%.2f%%", imageCandidate.maxDrawdownPercent()));
                }
                html.append("</small>\n");
            } else {
                html.append("          <div class=\"no-chart-placeholder\">\n");
                html.append("            <span>Kein eindeutig passendes MT5-Bild gefunden</span>\n");
                html.append("            <small>Passnummer, EA, Symbol, Zeitraum und Testart werden geprüft.</small>\n");
                html.append("          </div>\n");
            }
            html.append("        </div>\n");
            html.append("      </td>\n");

            html.append("    </tr>\n");
        }

        html.append("  </tbody>\n");
        html.append("</table>\n");

        // JavaScript for filtering, sorting and authenticated local actions.
        html.append("<script>\n");
        html.append("function filterTable() {\n");
        html.append("  const query = document.getElementById('searchInput').value.toLowerCase();\n");
        html.append("  const rows = document.querySelectorAll('#tableBody tr');\n");
        html.append("  rows.forEach(row => {\n");
        html.append("    const text = row.innerText.toLowerCase();\n");
        html.append("    row.style.display = text.includes(query) ? '' : 'none';\n");
        html.append("  });\n");
        html.append("}\n");

        html.append("let sortDirections = {};\n");
        html.append("function sortTable(colIdx, type) {\n");
        html.append("  const tbody = document.getElementById('tableBody');\n");
        html.append("  const rows = Array.from(tbody.querySelectorAll('tr'));\n");
        html.append("  const dir = sortDirections[colIdx] === 'asc' ? 'desc' : 'asc';\n");
        html.append("  sortDirections[colIdx] = dir;\n");

        html.append("  document.querySelectorAll('th').forEach(th => th.classList.remove('sort-asc', 'sort-desc'));\n");
        html.append("  const targetTh = document.querySelectorAll('th')[colIdx];\n");
        html.append("  if (targetTh) targetTh.classList.add(dir === 'asc' ? 'sort-asc' : 'sort-desc');\n");

        html.append("  const attrKeys = ['score', '', 'profit', 'fwprofit', 'ltprofit', 'trades', 'dd'];\n");
        html.append("  const attrKey = attrKeys[colIdx] || '';\n");

        html.append("  rows.sort((a, b) => {\n");
        html.append("    let valA = attrKey ? parseFloat(a.dataset[attrKey]) : a.children[colIdx].innerText.trim();\n");
        html.append("    let valB = attrKey ? parseFloat(b.dataset[attrKey]) : b.children[colIdx].innerText.trim();\n");
        html.append("    if (isNaN(valA)) valA = 0;\n");
        html.append("    if (isNaN(valB)) valB = 0;\n");
        html.append("    if (dir === 'asc') return valA > valB ? 1 : (valA < valB ? -1 : 0);\n");
        html.append("    return valA < valB ? 1 : (valA > valB ? -1 : 0);\n");
        html.append("  });\n");

        html.append("  rows.forEach(r => tbody.appendChild(r));\n");
        html.append("}\n");

        html.append("const galleryToken = ").append(gson.toJson(accessToken != null ? accessToken : ""))
                .append(";\n");
        html.append("function runSingleBacktest(evt) {\n");
        html.append("  const btn = evt.target;\n");
        html.append("  if (!btn.dataset.artifact) { alert('Für diese Strategie ist kein eindeutig passender MT5-Report vorhanden.'); return; }\n");
        html.append("  const params = new URLSearchParams({ pass: btn.dataset.pass, db: btn.dataset.db, name: btn.dataset.name, artifact: btn.dataset.artifact, token: galleryToken });\n");
        html.append("  const origText = btn.innerText;\n");
        html.append("  btn.innerText = '⏳ Starte MetaTrader...';\n");
        html.append("  btn.disabled = true;\n");
        html.append("  fetch('http://127.0.0.1:28987/run-backtest?' + params.toString())\n");
        html.append("    .then(res => res.json())\n");
        html.append("    .then(data => {\n");
        html.append("      if (data.status === 'ok') {\n");
        html.append("        btn.innerText = '✅ MetaTrader gestartet!';\n");
        html.append("        setTimeout(() => { btn.innerText = origText; btn.disabled = false; }, 4000);\n");
        html.append("      } else {\n");
        html.append("        alert('Fehler: ' + (data.message || 'Backtest konnte nicht gestartet werden.'));\n");
        html.append("        btn.innerText = origText;\n");
        html.append("        btn.disabled = false;\n");
        html.append("      }\n");
        html.append("    })\n");
        html.append("    .catch(err => {\n");
        html.append("      alert('Konnte den MT5-Backtester auf localhost:28987 nicht erreichen.\\nBitte stelle sicher, dass der Backtester geöffnet ist!');\n");
        html.append("      btn.innerText = origText;\n");
        html.append("      btn.disabled = false;\n");
        html.append("    });\n");
        html.append("}\n");
        html.append("</script>\n");

        html.append("</body>\n");
        html.append("</html>\n");

        Files.writeString(reportPath, html.toString(), StandardCharsets.UTF_8);
        log.info("Successfully generated Databank HTML Table Viewer: {}", reportPath.toAbsolutePath());
        return reportPath;
    }

    private static Map<Integer, ImageCandidate> scanAllImages(Path outputDirectory,
                                                               List<CombinedPass> passes,
                                                               String expert,
                                                               String symbol,
                                                               String period) {
        if (outputDirectory == null || !Files.isDirectory(outputDirectory) || passes == null) {
            return Map.of();
        }

        Map<Integer, Integer> passCounts = new HashMap<>();
        Map<Integer, CombinedPass> requested = new HashMap<>();
        for (CombinedPass pass : passes) {
            if (pass == null) continue;
            passCounts.merge(pass.getPassNumber(), 1, Integer::sum);
            requested.putIfAbsent(pass.getPassNumber(), pass);
        }
        Set<Integer> ambiguous = new HashSet<>();
        passCounts.forEach((number, count) -> {
            if (count > 1) ambiguous.add(number);
        });

        Map<Integer, ImageCandidate> best = new HashMap<>();
        try (java.util.stream.Stream<Path> stream = Files.walk(outputDirectory, 2)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase("tester.ini"))
                    .forEach(ini -> inspectReportDirectory(ini, requested, ambiguous,
                            expert, symbol, period).ifPresent(candidate ->
                            best.merge(candidate.passNumber(), candidate,
                                    DatabankHtmlViewerGenerator::preferImageCandidate)));
        } catch (IOException ex) {
            log.warn("Could not scan MT5 report directories for gallery images", ex);
        }

        log.info("HTML gallery resolved {} of {} MT5 images ({} ambiguous pass numbers omitted).",
                best.size(), requested.size(), ambiguous.size());
        return best;
    }

    private static Optional<ImageCandidate> inspectReportDirectory(Path ini,
                                                                    Map<Integer, CombinedPass> requested,
                                                                    Set<Integer> ambiguous,
                                                                    String expert,
                                                                    String symbol,
                                                                    String period) {
        try {
            Map<String, String> values = readIni(ini);
            String preset = values.getOrDefault("expertparameters", "");
            int passNumber = extractPassNumber(preset);
            CombinedPass pass = requested.get(passNumber);
            if (pass == null || ambiguous.contains(passNumber)) return Optional.empty();
            if (!matchesExpected(values.get("symbol"), symbol)
                    || !matchesExpected(values.get("period"), period)
                    || !matchesExpert(values.get("expert"), expert)) {
                return Optional.empty();
            }

            Path image = findPrimaryReportImage(ini.getParent());
            if (image == null) return Optional.empty();

            ensureStructuredStatistics(ini.getParent(), values);

            Pass expectedPass = pass.getLongtermPass() != null ? pass.getLongtermPass() : pass.getBacktestPass();
            String expectedFrom = expectedPass != null ? expectedPass.getFromDate() : "";
            String expectedTo = expectedPass != null ? expectedPass.getToDate() : "";
            boolean exactExpectedRange = dateMatches(values.get("fromdate"), expectedFrom)
                    && dateMatches(values.get("todate"), expectedTo);
            boolean expectedRangeConfigured = (expectedFrom != null && !expectedFrom.isBlank())
                    || (expectedTo != null && !expectedTo.isBlank());
            if (expectedRangeConfigured && !exactExpectedRange) {
                return Optional.empty();
            }

            boolean matchesLongtermRange = pass.getLongtermPass() != null && exactExpectedRange;
            return Optional.of(new ImageCandidate(passNumber, image, testKind(preset),
                    lastModified(image), readMaxDrawdown(ini.getParent()), matchesLongtermRange));
        } catch (Exception ex) {
            log.debug("Ignoring unreadable MT5 report metadata at {}", ini, ex);
            return Optional.empty();
        }
    }

    private static ImageCandidate preferImageCandidate(ImageCandidate left, ImageCandidate right) {
        if (left.modifiedMillis() != right.modifiedMillis()) {
            return left.modifiedMillis() > right.modifiedMillis() ? left : right;
        }
        return left.path().toString().compareToIgnoreCase(right.path().toString()) >= 0 ? left : right;
    }

    private static String testKind(String preset) {
        String normalized = preset != null ? preset.toLowerCase(Locale.ROOT) : "";
        if (normalized.contains("longterm")) return "Langzeittest";
        if (normalized.contains("verify")) return "Verifikation";
        return "MT5-Test";
    }

    private static Double readMaxDrawdown(Path directory) {
        if (directory == null) return null;
        Path statistics = directory.resolve(BacktestStatisticsArtifact.FILE_NAME);
        if (Files.isRegularFile(statistics)) {
            try {
                com.google.gson.JsonObject json = gson.fromJson(
                        Files.readString(statistics, StandardCharsets.UTF_8),
                        com.google.gson.JsonObject.class);
                if (json != null && json.has("relativeEquityDrawdownPercent")
                        && !json.get("relativeEquityDrawdownPercent").isJsonNull()) {
                    double value = json.get("relativeEquityDrawdownPercent").getAsDouble();
                    if (Double.isFinite(value)) return value;
                }
            } catch (Exception ex) {
                log.debug("Could not read structured statistics from {}", statistics, ex);
            }
        }
        Path summary = directory.resolve("summary.txt");
        if (!Files.isRegularFile(summary)) return null;
        Pattern pattern = Pattern.compile("(?i)^\\s*Max(?:imal)?\\s+Drawdown\\s*:\\s*([-+]?\\d+(?:[.,]\\d+)?)\\s*%?.*$");
        try {
            for (String line : Files.readAllLines(summary, StandardCharsets.UTF_8)) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    double value = Double.parseDouble(matcher.group(1).replace(',', '.'));
                    return Double.isFinite(value) ? value : null;
                }
            }
        } catch (IOException | NumberFormatException ex) {
            log.debug("Could not read drawdown from {}", summary, ex);
        }
        return null;
    }

    /** Lazily upgrades legacy report directories without rerunning MT5. */
    private static void ensureStructuredStatistics(Path directory, Map<String, String> iniValues) {
        if (directory == null || Files.isRegularFile(directory.resolve(BacktestStatisticsArtifact.FILE_NAME))) {
            return;
        }
        Path report = directory.resolve("report.htm");
        if (!Files.isRegularFile(report)) return;
        try {
            BacktestResult result = new ReportParser().parse(report);
            String expert = iniValues.getOrDefault("expert", "");
            String symbol = iniValues.getOrDefault("symbol", "");
            String period = iniValues.getOrDefault("period", "");
            result.setExpert(expert);
            result.setSymbol(symbol);
            result.setPeriod(period);
            result.setOutputDirectory(directory.toAbsolutePath().normalize().toString());
            result.setSuccess(true);

            com.backtester.engine.BacktestConfig config = new com.backtester.engine.BacktestConfig();
            config.setExpert(expert);
            config.setExpertParameters(iniValues.getOrDefault("expertparameters", ""));
            config.setSymbol(symbol);
            config.setPeriod(period);
            config.setModel(Integer.parseInt(iniValues.getOrDefault("model", "0")));
            config.setExecutionMode(Integer.parseInt(iniValues.getOrDefault("executionmode", "0")));
            config.setFromDate(LocalDate.parse(iniValues.get("fromdate").replace('.', '-')));
            config.setToDate(LocalDate.parse(iniValues.get("todate").replace('.', '-')));
            config.setDeposit(Integer.parseInt(iniValues.getOrDefault("deposit", "10000")));
            config.setCurrency(iniValues.getOrDefault("currency", "USD"));
            config.setLeverage(iniValues.getOrDefault("leverage", "1:100"));
            result.setTickModel(config.getModelName());

            BacktestStatisticsArtifact.write(directory, result, config);
            log.info("Backfilled structured statistics for legacy MT5 report {}", directory);
        } catch (Exception ex) {
            log.debug("Could not backfill structured statistics for {}", directory, ex);
        }
    }

    private static String formatArtifactTimestamp(long modifiedMillis) {
        if (modifiedMillis <= 0) return "Zeit unbekannt";
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(modifiedMillis), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }

    private static Map<String, String> readIni(Path ini) throws IOException {
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readAllLines(ini, StandardCharsets.UTF_8)) {
            int separator = line.indexOf('=');
            if (separator <= 0) continue;
            values.put(line.substring(0, separator).trim().toLowerCase(Locale.ROOT),
                    line.substring(separator + 1).trim());
        }
        return values;
    }

    private static Path findPrimaryReportImage(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) return null;
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase("BacktestReport.png"))
                    .findFirst().orElse(null);
        }
    }

    private static boolean matchesExpected(String actual, String expected) {
        return expected == null || expected.isBlank()
                || actual != null && actual.trim().equalsIgnoreCase(expected.trim());
    }

    private static boolean matchesExpert(String actual, String expected) {
        if (expected == null || expected.isBlank()) return true;
        return normalizeExpert(actual).equalsIgnoreCase(normalizeExpert(expected));
    }

    private static String normalizeExpert(String value) {
        if (value == null) return "";
        String normalized = value.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) normalized = normalized.substring(slash + 1);
        return normalized.replaceFirst("(?i)[.]ex[45]$", "").trim();
    }

    private static boolean dateMatches(String actual, String expected) {
        if (expected == null || expected.isBlank()) return true;
        return normalizeDate(actual).equals(normalizeDate(expected));
    }

    private static String normalizeDate(String value) {
        return value != null ? value.replaceAll("[^0-9]", "") : "";
    }

    private static int extractPassNumber(String value) {
        if (value == null) return -1;
        Matcher matcher = Pattern.compile("pass[_#\\s-]*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(value);
        if (!matcher.find()) return -1;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            return 0L;
        }
    }

    private record ImageCandidate(int passNumber, Path path, String testKind,
                                  long modifiedMillis, Double maxDrawdownPercent,
                                  boolean matchesLongtermRange) {
    }

    private static String getRelativeOrUriPath(Path reportPath, Path imgPath) {
        if (reportPath != null && reportPath.getParent() != null && imgPath != null) {
            try {
                Path relative = reportPath.getParent().relativize(imgPath);
                return relative.toString().replace('\\', '/');
            } catch (Exception ignored) {}
        }
        return imgPath != null ? imgPath.toUri().toString() : "";
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
