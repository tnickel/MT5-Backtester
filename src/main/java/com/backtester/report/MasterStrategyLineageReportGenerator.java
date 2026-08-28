package com.backtester.report;

import com.backtester.engine.BacktestConfig;
import com.backtester.workflow.CustomProject;
import com.backtester.workflow.MasterStrategyEntry;
import com.backtester.workflow.MasterStrategyLineageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Generates a comprehensive HTML report documenting the complete lineage and progression
 * of a Master Strategy. The resulting file includes SVG trend charts, SVG equity curves for
 * every intermediate pick test, parameter comparison diffs, key metrics with deltas,
 * embedded MT5 equity graphics, and print CSS (@media print) for pixel-perfect PDF export.
 */
public class MasterStrategyLineageReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(MasterStrategyLineageReportGenerator.class);

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault());

    /**
     * Generates a complete lineage report for the given project.
     *
     * @param project the project containing lineage history
     * @return Path to the created HTML report file
     * @throws IOException if report file cannot be written
     */
    public static Path generateReport(CustomProject project) throws IOException {
        Path reportsDir = Paths.get("reports");
        if (!Files.exists(reportsDir)) {
            Files.createDirectories(reportsDir);
        }

        String projectName = (project != null && project.getName() != null && !project.getName().isBlank())
                ? sanitizeFilename(project.getName()) : "Unbenannt";
        String fileTime = FILE_TIMESTAMP.format(Instant.now());
        String filename = "Master_Strategie_Abschlussbericht_" + projectName + "_" + fileTime + ".html";
        Path reportFile = reportsDir.resolve(filename);

        List<MasterStrategyEntry> lineage = (project != null && project.getMasterStrategyLineage() != null)
                ? project.getMasterStrategyLineage() : List.of();

        String htmlContent = buildHtml(project, lineage);
        Files.writeString(reportFile, htmlContent, StandardCharsets.UTF_8);

        log.info("Master-Strategie Abschlussbericht generiert: {}", reportFile.toAbsolutePath());
        return reportFile.toAbsolutePath();
    }

    /**
     * Opens the report in the user's default web browser.
     */
    public static void openInBrowser(Path reportFile) {
        if (reportFile == null || !Files.exists(reportFile)) return;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(reportFile.toUri());
            } else {
                String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
                if (os.contains("win")) {
                    new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", reportFile.toString()).start();
                } else if (os.contains("mac")) {
                    new ProcessBuilder("open", reportFile.toString()).start();
                } else {
                    new ProcessBuilder("xdg-open", reportFile.toString()).start();
                }
            }
        } catch (Exception e) {
            log.error("Fehler beim Öffnen des Berichts im Browser: {}", e.getMessage(), e);
        }
    }

    private static String buildHtml(CustomProject project, List<MasterStrategyEntry> lineage) {
        StringBuilder sb = new StringBuilder(65536);

        String projName = project != null && project.getName() != null ? project.getName() : "Unbenanntes Projekt";
        String eaName = project != null && project.getExpert() != null ? project.getExpert() : "—";
        String symbol = project != null && project.getSymbol() != null ? project.getSymbol() : "—";
        String period = project != null && project.getPeriod() != null ? project.getPeriod() : "—";
        BacktestConfig reference = MasterStrategyLineageService.buildReferenceConfig(project, "");

        MasterStrategyEntry latest = lineage.isEmpty() ? null : lineage.get(lineage.size() - 1);
        MasterStrategyEntry confirmedMaster = project != null
                ? MasterStrategyLineageService.confirmedMasterEntry(project).orElse(null) : null;
        String displayContext = confirmedMaster != null
                ? confirmedMaster.contextKey() : (latest != null ? latest.contextKey() : "");
        MasterStrategyEntry best = latest != null
                ? MasterStrategyLineageService.bestEntry(lineage, displayContext).orElse(null) : null;
        int confirmedMasterSequence = confirmedMaster != null ? confirmedMaster.getSequence() : -1;

        double initialProfit = lineage.isEmpty() ? 0.0 : lineage.get(0).getProfit();
        double finalProfit = confirmedMaster != null ? confirmedMaster.getProfit() : Double.NaN;
        double profitDelta = confirmedMaster != null ? finalProfit - initialProfit : Double.NaN;

        double bestReturnToDd = best != null ? best.getReturnToDrawdown() : 0.0;

        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"de\">\n<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("  <title>Master-Strategie Abschlussbericht — ").append(escapeHtml(projName)).append("</title>\n");
        sb.append("  <style>\n").append(getCssStyle()).append("  </style>\n");
        sb.append("</head>\n<body>\n");

        // Floating Action Bar (hidden in print)
        sb.append("<div class=\"action-bar no-print\">\n");
        sb.append("  <div class=\"action-bar-title\">Master-Strategie Abschlussbericht</div>\n");
        sb.append("  <button class=\"btn btn-primary\" onclick=\"window.print()\">🖨️ Als PDF drucken / speichern</button>\n");
        sb.append("</div>\n");

        sb.append("<div class=\"container\">\n");

        // Header Section
        sb.append("  <header class=\"report-header\">\n");
        sb.append("    <div class=\"header-badge\">Abschlussbericht & Progress-Protokoll</div>\n");
        sb.append("    <h1>Master-Strategie Verlauf: ").append(escapeHtml(projName)).append("</h1>\n");
        sb.append("    <div class=\"meta-grid\">\n");
        sb.append("      <div class=\"meta-item\"><span class=\"meta-label\">EA / Strategie:</span> ").append(escapeHtml(eaName)).append("</div>\n");
        sb.append("      <div class=\"meta-item\"><span class=\"meta-label\">Symbol / Timeframe:</span> ").append(escapeHtml(symbol)).append(" / ").append(escapeHtml(period)).append("</div>\n");
        sb.append("      <div class=\"meta-item\"><span class=\"meta-label\">Referenzzeitraum:</span> ")
                .append(reference.getFromDate()).append(" bis ")
                .append(reference.getToDate()).append("</div>\n");
        sb.append("      <div class=\"meta-item\"><span class=\"meta-label\">Modellierung:</span> ")
                .append(escapeHtml(reference.getModelName())).append("</div>\n");
        sb.append("      <div class=\"meta-item\"><span class=\"meta-label\">Erstellt am:</span> ").append(TIMESTAMP_FORMATTER.format(Instant.now())).append("</div>\n");
        sb.append("      <div class=\"meta-item\"><span class=\"meta-label\">Messpunkte Gesamt:</span> ").append(lineage.size()).append(" Picks</div>\n");
        sb.append("    </div>\n");
        sb.append("  </header>\n");

        // Summary KPI Cards
        sb.append("  <section class=\"summary-cards\">\n");
        sb.append("    <div class=\"card kpi-card\">\n");
        sb.append("      <div class=\"kpi-title\">Anzahl Messpunkte</div>\n");
        sb.append("      <div class=\"kpi-value\">").append(lineage.size()).append("</div>\n");
        sb.append("      <div class=\"kpi-sub\">Re-Backtests nach Picks</div>\n");
        sb.append("    </div>\n");

        sb.append("    <div class=\"card kpi-card\">\n");
        sb.append("      <div class=\"kpi-title\">Bestätigter Master</div>\n");
        sb.append("      <div class=\"kpi-value text-accent\">").append(confirmedMaster != null ? "#" + confirmedMasterSequence : "—").append("</div>\n");
        sb.append("      <div class=\"kpi-sub\">").append(confirmedMaster != null
                ? escapeHtml(confirmedMaster.getStageTaskName()) : "Noch kein Master bestätigt").append("</div>\n");
        sb.append("    </div>\n");

        sb.append("    <div class=\"card kpi-card\">\n");
        sb.append("      <div class=\"kpi-title\">Höchster gemessener Profit / DD</div>\n");
        sb.append("      <div class=\"kpi-value text-success\">").append(num(bestReturnToDd)).append("</div>\n");
        sb.append("      <div class=\"kpi-sub\">Profit zu Max. Drawdown Ratio</div>\n");
        sb.append("    </div>\n");

        sb.append("    <div class=\"card kpi-card\">\n");
        sb.append("      <div class=\"kpi-title\">Gesamt Profit Entwicklung</div>\n");
        sb.append("      <div class=\"kpi-value ").append(!Double.isFinite(profitDelta)
                ? "text-accent" : (profitDelta >= 0 ? "text-success" : "text-danger")).append("\">")
                .append(Double.isFinite(profitDelta) && profitDelta >= 0 ? "+" : "")
                .append(num(profitDelta)).append("</div>\n");
        sb.append("      <div class=\"kpi-sub\">Initial ").append(num(initialProfit))
                .append(" → Bestätigter Master ").append(num(finalProfit)).append("</div>\n");
        sb.append("    </div>\n");
        sb.append("  </section>\n");

        // Progression Trend Section
        sb.append("  <section class=\"card section-card\">\n");
        sb.append("    <h2>📈 Entwicklung der Master-Strategie (Profit / Drawdown über Messpunkte)</h2>\n");
        sb.append("    <p class=\"section-desc\">Jeder Hand-Pick wurde unter identischen Referenzbedingungen nachgetestet. Das Diagramm zeigt die Entwicklung des Profit/Drawdown-Quotienten.</p>\n");
        sb.append("    <div class=\"chart-container\">\n");
        sb.append(generateTrendSvg(lineage));
        sb.append("    </div>\n");
        sb.append("  </section>\n");

        // Overview Table
        sb.append("  <section class=\"card section-card\">\n");
        sb.append("    <h2>📋 Übersicht aller Stufen & Messpunkte</h2>\n");
        sb.append("    <div class=\"table-responsive\">\n");
        sb.append("      <table class=\"data-table\">\n");
        sb.append("        <thead>\n");
        sb.append("          <tr>\n");
        sb.append("            <th>#</th>\n");
        sb.append("            <th>Pick / Stage Task</th>\n");
        sb.append("            <th>Databank</th>\n");
        sb.append("            <th>Profit ($)</th>\n");
        sb.append("            <th>Profit / DD</th>\n");
        sb.append("            <th>Max. DD %</th>\n");
        sb.append("            <th>Trades</th>\n");
        sb.append("            <th>Sharpe</th>\n");
        sb.append("            <th>Bewertung</th>\n");
        sb.append("          </tr>\n");
        sb.append("        </thead>\n");
        sb.append("        <tbody>\n");

        for (MasterStrategyEntry entry : lineage) {
            String verdictClass = switch (entry.getVerdict()) {
                case BESSER -> "badge-success";
                case SCHLECHTER -> "badge-danger";
                case NEUTRAL -> "badge-warning";
                case UNBEKANNT -> "badge-secondary";
            };
            String verdictText = switch (entry.getVerdict()) {
                case BESSER -> "besser";
                case SCHLECHTER -> "schlechter";
                case NEUTRAL -> "unverändert";
                case UNBEKANNT -> "Referenz";
            };

            boolean isConfirmedMaster = entry.getSequence() == confirmedMasterSequence;
            sb.append("          <tr").append(isConfirmedMaster ? " class=\"confirmed-master-row\"" : "").append(">\n");
            sb.append("            <td><strong>#").append(entry.getSequence()).append("</strong></td>\n");
            sb.append("            <td>").append(escapeHtml(entry.getStageTaskName())).append("</td>\n");
            sb.append("            <td><code>").append(escapeHtml(entry.getSourceDatabank())).append("</code></td>\n");
            sb.append("            <td>").append(num(entry.getProfit())).append("</td>\n");
            sb.append("            <td><strong>").append(num(entry.getReturnToDrawdown())).append("</strong></td>\n");
            sb.append("            <td>").append(num(entry.getMaxDrawdownPercent())).append("%</td>\n");
            sb.append("            <td>").append(entry.getTotalTrades()).append("</td>\n");
            sb.append("            <td>").append(num(entry.getSharpeRatio())).append("</td>\n");
            sb.append("            <td><span class=\"badge ").append(verdictClass).append("\">").append(verdictText).append("</span>");
            if (isConfirmedMaster) {
                sb.append(" <span class=\"badge master-badge\">BESTÄTIGTER MASTER</span>");
            }
            sb.append("</td>\n");
            sb.append("          </tr>\n");
        }

        sb.append("        </tbody>\n");
        sb.append("      </table>\n");
        sb.append("    </div>\n");
        sb.append("  </section>\n");

        // Detailed Pick Breakdown
        sb.append("  <section class=\"section-title-bar\">\n");
        sb.append("    <h2>🔍 Detaillierter Verlauf & Equitykurven der Einzeltests</h2>\n");
        sb.append("  </section>\n");

        for (MasterStrategyEntry entry : lineage) {
            appendPickDetail(sb, entry, entry.getSequence() == confirmedMasterSequence);
        }

        // Footer
        sb.append("  <footer class=\"report-footer\">\n");
        sb.append("    <p>Master-Strategie Abschlussbericht · Generiert durch Backtester Engine</p>\n");
        sb.append("  </footer>\n");

        sb.append("</div>\n"); // container
        sb.append("</body>\n</html>");

        return sb.toString();
    }

    private static void appendPickDetail(StringBuilder sb, MasterStrategyEntry entry, boolean confirmedMaster) {
        String verdictClass = switch (entry.getVerdict()) {
            case BESSER -> "badge-success";
            case SCHLECHTER -> "badge-danger";
            case NEUTRAL -> "badge-warning";
            case UNBEKANNT -> "badge-secondary";
        };
        String verdictText = switch (entry.getVerdict()) {
            case BESSER -> "besser";
            case SCHLECHTER -> "schlechter";
            case NEUTRAL -> "unverändert";
            case UNBEKANNT -> "Referenz-Messpunkt";
        };

        sb.append("  <article class=\"card pick-card")
                .append(confirmedMaster ? " confirmed-master-card" : "")
                .append("\" id=\"pick-").append(entry.getSequence()).append("\">\n");
        sb.append("    <div class=\"pick-header\">\n");
        sb.append("      <div>\n");
        sb.append("        <span class=\"pick-seq\">#").append(String.format("%02d", entry.getSequence())).append("</span>\n");
        sb.append("        <h3 class=\"pick-title\">").append(escapeHtml(entry.getShortLabel())).append("</h3>\n");
        sb.append("      </div>\n");
        sb.append("      <div><span class=\"badge ").append(verdictClass).append("\">").append(verdictText).append("</span>");
        if (confirmedMaster) {
            sb.append(" <span class=\"badge master-badge\">BESTÄTIGTER MASTER</span>");
        }
        sb.append("</div>\n");
        sb.append("    </div>\n");

        sb.append("    <div class=\"pick-body-grid\">\n");

        // Left Column: Equity Chart & MT5 Graphic
        sb.append("      <div class=\"pick-chart-col\">\n");
        sb.append("        <h4>Equitykurve (Referenz-Backtest)</h4>\n");

        if (!entry.getEquityCurve().isEmpty()) {
            sb.append("        <div class=\"equity-svg-wrapper\">\n");
            sb.append(generateEquitySvg(entry));
            sb.append("        </div>\n");
        } else {
            sb.append("        <div class=\"placeholder-box\">").append(entry.isBacktestSucceeded()
                    ? "Keine Equity-Daten vorhanden."
                    : "Backtest fehlgeschlagen: " + escapeHtml(entry.getFailureMessage())).append("</div>\n");
        }

        // Check for MT5 Image
        Path imgPath = entry.getEquityImagePath().isBlank() ? null : Paths.get(entry.getEquityImagePath());
        if (imgPath != null && Files.isRegularFile(imgPath)) {
            try {
                byte[] bytes = Files.readAllBytes(imgPath);
                String b64 = Base64.getEncoder().encodeToString(bytes);
                sb.append("        <h4 style=\"margin-top:16px;\">MetaTrader 5 Original-Grafik</h4>\n");
                sb.append("        <div class=\"mt5-img-wrapper\">\n");
                sb.append("          <img src=\"data:image/png;base64,").append(b64).append("\" alt=\"MT5 Equity Graphic\">\n");
                sb.append("        </div>\n");
            } catch (Exception e) {
                log.warn("MT5 Grafik konnte nicht gelesen werden: {}", imgPath, e);
            }
        }

        sb.append("      </div>\n"); // pick-chart-col

        // Right Column: Key Metrics & Origin
        sb.append("      <div class=\"pick-info-col\">\n");
        sb.append("        <h4>Kennzahlen & Deltas</h4>\n");
        sb.append("        <table class=\"metrics-table\">\n");

        appendMetricRow(sb, "Netto Profit", num(entry.getProfit()) + " " + entry.getCurrency(), entry.getDeltaProfit(), true);
        appendMetricRow(sb, "Profit / Drawdown", num(entry.getReturnToDrawdown()), entry.getDeltaReturnToDrawdown(), true);
        appendMetricRow(sb, "Profit-Faktor", num(entry.getProfitFactor()), Double.NaN, true);
        appendMetricRow(sb, "Max. Drawdown %", num(entry.getMaxDrawdownPercent()) + "%", entry.getDeltaMaxDrawdownPercent(), false);
        appendMetricRow(sb, "Max. Drawdown abs.", num(entry.getMaxDrawdownAbsolute()) + " " + entry.getCurrency(), Double.NaN, false);
        appendMetricRow(sb, "Trades Gesamt", String.valueOf(entry.getTotalTrades()), Double.NaN, true);
        appendMetricRow(sb, "Recovery-Faktor", num(entry.getRecoveryFactor()), Double.NaN, true);
        appendMetricRow(sb, "Sharpe Ratio", num(entry.getSharpeRatio()), Double.NaN, true);
        appendMetricRow(sb, "Erwarteter Gewinn", num(entry.getExpectedPayoff()), Double.NaN, true);
        appendMetricRow(sb, "Endkapital", num(entry.getFinalBalance()) + " " + entry.getCurrency(), Double.NaN, true);

        sb.append("        </table>\n");

        sb.append("        <h4 style=\"margin-top:16px;\">Herkunft & Setup</h4>\n");
        sb.append("        <table class=\"info-table\">\n");
        sb.append("          <tr><td>Zeitpunkt:</td><td>").append(TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(entry.getCreatedAt()))).append("</td></tr>\n");
        sb.append("          <tr><td>Stage:</td><td>").append(escapeHtml(entry.getStageTaskName())).append("</td></tr>\n");
        sb.append("          <tr><td>Databank:</td><td><code>").append(escapeHtml(entry.getSourceDatabank())).append("</code></td></tr>\n");
        sb.append("          <tr><td>Pass:</td><td>").append(entry.sourcePassLabel())
                .append("</td></tr>\n");
        sb.append("          <tr><td>Markt:</td><td>").append(escapeHtml(entry.getSymbol())).append(" ").append(escapeHtml(entry.getPeriod())).append("</td></tr>\n");
        sb.append("          <tr><td>Vergleich:</td><td>").append(entry.getComparedToSequence() > 0
                ? "gegen bestätigten Master #" + entry.getComparedToSequence()
                : "ohne bestätigten Vergleichsanker").append("</td></tr>\n");
        sb.append("        </table>\n");

        sb.append("      </div>\n"); // pick-info-col

        sb.append("    </div>\n"); // pick-body-grid

        // Parameter Changes Table below
        sb.append("    <div class=\"pick-params-section\">\n");
        sb.append("      <h4>Optimierte Parameter (Stufe ").append(escapeHtml(entry.getOptimizedStageName().isBlank() ? entry.getStageTaskName() : entry.getOptimizedStageName())).append(")</h4>\n");

        List<MasterStrategyEntry.ParameterChange> optimized = entry.getOptimizedParameters();
        if (!optimized.isEmpty()) {
            sb.append("      <table class=\"param-table\">\n");
            sb.append("        <thead><tr><th>Parameter</th><th>Vorheriger Wert</th><th>Neuer Wert (Übernommen)</th><th>Status</th></tr></thead>\n");
            sb.append("        <tbody>\n");
            for (MasterStrategyEntry.ParameterChange ch : optimized) {
                if (ch == null) continue;
                boolean changed = ch.isChanged();
                sb.append("          <tr class=\"").append(changed ? "param-changed" : "param-unchanged").append("\">\n");
                sb.append("            <td><strong>").append(escapeHtml(ch.getName())).append("</strong></td>\n");
                sb.append("            <td>").append(escapeHtml(blankToDash(ch.getOldValue()))).append("</td>\n");
                sb.append("            <td><strong>").append(escapeHtml(blankToDash(ch.getNewValue()))).append("</strong></td>\n");
                sb.append("            <td>").append(changed ? "<span class=\"txt-success\">Geändert</span>" : "<span class=\"txt-muted\">Beibehalten</span>").append("</td>\n");
                sb.append("          </tr>\n");
            }
            sb.append("        </tbody>\n");
            sb.append("      </table>\n");
        } else if (!entry.getAdoptedChanges().isEmpty()) {
            sb.append("      <ul class=\"adopted-list\">\n");
            for (String line : entry.getAdoptedChanges()) {
                sb.append("        <li>").append(escapeHtml(line)).append("</li>\n");
            }
            sb.append("      </ul>\n");
        } else {
            sb.append("      <p class=\"txt-muted\">Keine spezifischen Parameteränderungen protokolliert.</p>\n");
        }

        // Additional changes if any
        if (!entry.getAdditionalChanges().isEmpty()) {
            sb.append("      <h4 style=\"margin-top:12px;\">Weitere übernommene Lauf-Presets</h4>\n");
            sb.append("      <table class=\"param-table\">\n");
            sb.append("        <thead><tr><th>Parameter</th><th>Vorher</th><th>Nachher</th></tr></thead>\n");
            sb.append("        <tbody>\n");
            for (MasterStrategyEntry.ParameterChange ch : entry.getAdditionalChanges()) {
                if (ch == null) continue;
                sb.append("          <tr><td>").append(escapeHtml(ch.getName())).append("</td><td>")
                        .append(escapeHtml(blankToDash(ch.getOldValue()))).append("</td><td>")
                        .append(escapeHtml(blankToDash(ch.getNewValue()))).append("</td></tr>\n");
            }
            sb.append("        </tbody>\n");
            sb.append("      </table>\n");
        }

        sb.append("    </div>\n"); // pick-params-section

        sb.append("  </article>\n");
    }

    private static void appendMetricRow(StringBuilder sb, String name, String val, double delta, boolean higherIsBetter) {
        sb.append("          <tr><td>").append(name).append("</td><td><strong>").append(val).append("</strong></td><td>");
        if (Double.isFinite(delta) && Math.abs(delta) > 1e-9) {
            boolean good = higherIsBetter ? delta > 0 : delta < 0;
            String cls = good ? "txt-success" : "txt-danger";
            String sign = delta > 0 ? "▲ +" : "▼ ";
            sb.append("<span class=\"").append(cls).append("\">").append(sign).append(String.format(Locale.US, "%.2f", delta)).append("</span>");
        } else {
            sb.append("—");
        }
        sb.append("</td></tr>\n");
    }

    private static String generateTrendSvg(List<MasterStrategyEntry> lineage) {
        if (lineage == null || lineage.isEmpty()) {
            return "<div class=\"placeholder-box\">Keine Messpunkte vorhanden</div>";
        }

        int width = 900;
        int height = 260;
        int padLeft = 60;
        int padRight = 30;
        int padTop = 30;
        int padBottom = 40;

        int plotWidth = width - padLeft - padRight;
        int plotHeight = height - padTop - padBottom;

        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        List<MasterStrategyEntry> validEntries = new ArrayList<>();

        for (MasterStrategyEntry e : lineage) {
            if (e != null && e.isBacktestSucceeded() && Double.isFinite(e.getReturnToDrawdown())) {
                validEntries.add(e);
                minY = Math.min(minY, e.getReturnToDrawdown());
                maxY = Math.max(maxY, e.getReturnToDrawdown());
            }
        }

        if (validEntries.isEmpty()) {
            return "<div class=\"placeholder-box\">Keine auswertbaren Messpunkte für Trendgrafik</div>";
        }

        if (Math.abs(maxY - minY) < 1e-6) {
            minY = Math.max(0, minY - 1.0);
            maxY = maxY + 1.0;
        } else {
            double margin = (maxY - minY) * 0.15;
            minY = Math.max(0, minY - margin);
            maxY = maxY + margin;
        }

        StringBuilder svg = new StringBuilder();
        svg.append(String.format(Locale.US, "<svg viewBox=\"0 0 %d %d\" class=\"trend-svg\">\n", width, height));

        // Grid lines
        int gridSteps = 4;
        for (int i = 0; i <= gridSteps; i++) {
            double val = minY + i * (maxY - minY) / gridSteps;
            double y = height - padBottom - (i * (double) plotHeight / gridSteps);
            svg.append(String.format(Locale.US, "  <line x1=\"%d\" y1=\"%.1f\" x2=\"%d\" y2=\"%.1f\" stroke=\"#334155\" stroke-dasharray=\"4\" />\n",
                    padLeft, y, width - padRight, y));
            svg.append(String.format(Locale.US, "  <text x=\"%d\" y=\"%.1f\" fill=\"#94a3b8\" font-size=\"11\" text-anchor=\"end\" dominant-baseline=\"middle\">%.2f</text>\n",
                    padLeft - 8, y, val));
        }

        // Plot line
        StringBuilder points = new StringBuilder();
        int maxSeq = lineage.get(lineage.size() - 1).getSequence();
        int minSeq = lineage.get(0).getSequence();
        int seqRange = Math.max(1, maxSeq - minSeq);

        for (int i = 0; i < validEntries.size(); i++) {
            MasterStrategyEntry e = validEntries.get(i);
            double x = padLeft + ((double) (e.getSequence() - minSeq) / seqRange) * plotWidth;
            double y = height - padBottom - ((e.getReturnToDrawdown() - minY) / (maxY - minY)) * plotHeight;
            if (i > 0) points.append(" ");
            points.append(String.format(Locale.US, "%.1f,%.1f", x, y));
        }

        svg.append(String.format(Locale.US, "  <polyline points=\"%s\" fill=\"none\" stroke=\"#00e5ff\" stroke-width=\"3\" stroke-linecap=\"round\" stroke-linejoin=\"round\" />\n", points));

        // Draw points with colors based on verdict
        for (MasterStrategyEntry e : validEntries) {
            double x = padLeft + ((double) (e.getSequence() - minSeq) / seqRange) * plotWidth;
            double y = height - padBottom - ((e.getReturnToDrawdown() - minY) / (maxY - minY)) * plotHeight;
            String color = switch (e.getVerdict()) {
                case BESSER -> "#00e676";
                case SCHLECHTER -> "#ff5252";
                case NEUTRAL -> "#ffd740";
                case UNBEKANNT -> "#00e5ff";
            };

            svg.append(String.format(Locale.US, "  <circle cx=\"%.1f\" cy=\"%.1f\" r=\"6\" fill=\"%s\" stroke=\"#0f172a\" stroke-width=\"2\" />\n", x, y, color));
            svg.append(String.format(Locale.US, "  <text x=\"%.1f\" y=\"%.1f\" fill=\"#f8fafc\" font-size=\"11\" font-weight=\"bold\" text-anchor=\"middle\">#%d</text>\n",
                    x, y - 10, e.getSequence()));
        }

        // X Axis labels
        svg.append(String.format(Locale.US, "  <text x=\"%d\" y=\"%d\" fill=\"#94a3b8\" font-size=\"12\" text-anchor=\"middle\">Pick / Messpunkt (#%d bis #%d)</text>\n",
                padLeft + plotWidth / 2, height - 10, minSeq, maxSeq));

        svg.append("</svg>\n");
        return svg.toString();
    }

    private static String generateEquitySvg(MasterStrategyEntry entry) {
        List<double[]> curve = entry.getEquityCurve();
        if (curve == null || curve.isEmpty()) return "";

        int width = 720;
        int height = 240;
        int padLeft = 60;
        int padRight = 20;
        int padTop = 20;
        int padBottom = 35;

        int plotWidth = width - padLeft - padRight;
        int plotHeight = height - padTop - padBottom;

        double minEquity = Double.MAX_VALUE;
        double maxEquity = -Double.MAX_VALUE;
        double maxTrade = 1.0;

        for (double[] pt : curve) {
            if (pt != null && pt.length >= 2) {
                maxTrade = Math.max(maxTrade, pt[0]);
                minEquity = Math.min(minEquity, pt[1]);
                maxEquity = Math.max(maxEquity, pt[1]);
            }
        }

        double startDeposit = entry.getDeposit() > 0 ? entry.getDeposit() : curve.get(0)[1];
        minEquity = Math.min(minEquity, startDeposit * 0.95);
        maxEquity = Math.max(maxEquity, startDeposit * 1.05);

        if (Math.abs(maxEquity - minEquity) < 1e-6) {
            maxEquity += 100.0;
            minEquity = Math.max(0, minEquity - 100.0);
        } else {
            double m = (maxEquity - minEquity) * 0.1;
            minEquity = Math.max(0, minEquity - m);
            maxEquity += m;
        }

        StringBuilder svg = new StringBuilder();
        svg.append(String.format(Locale.US, "<svg viewBox=\"0 0 %d %d\" class=\"equity-svg\">\n", width, height));

        // Grid lines & labels
        int steps = 4;
        for (int i = 0; i <= steps; i++) {
            double val = minEquity + i * (maxEquity - minEquity) / steps;
            double y = height - padBottom - (i * (double) plotHeight / steps);
            svg.append(String.format(Locale.US, "  <line x1=\"%d\" y1=\"%.1f\" x2=\"%d\" y2=\"%.1f\" stroke=\"#334155\" stroke-dasharray=\"3\" />\n",
                    padLeft, y, width - padRight, y));
            svg.append(String.format(Locale.US, "  <text x=\"%d\" y=\"%.1f\" fill=\"#94a3b8\" font-size=\"10\" text-anchor=\"end\" dominant-baseline=\"middle\">%.0f</text>\n",
                    padLeft - 6, y, val));
        }

        // Deposit reference line
        double startY = height - padBottom - ((startDeposit - minEquity) / (maxEquity - minEquity)) * plotHeight;
        svg.append(String.format(Locale.US, "  <line x1=\"%d\" y1=\"%.1f\" x2=\"%d\" y2=\"%.1f\" stroke=\"#64748b\" stroke-dasharray=\"5\" stroke-width=\"1.5\" />\n",
                padLeft, startY, width - padRight, startY));

        // Polyline equity curve
        StringBuilder pts = new StringBuilder();
        for (int i = 0; i < curve.size(); i++) {
            double[] pt = curve.get(i);
            if (pt == null || pt.length < 2) continue;
            double x = padLeft + (pt[0] / maxTrade) * plotWidth;
            double y = height - padBottom - ((pt[1] - minEquity) / (maxEquity - minEquity)) * plotHeight;
            if (i > 0) pts.append(" ");
            pts.append(String.format(Locale.US, "%.1f,%.1f", x, y));
        }

        // Fill area under curve
        if (curve.size() > 1) {
            double lastX = padLeft + (curve.get(curve.size() - 1)[0] / maxTrade) * plotWidth;
            double firstX = padLeft + (curve.get(0)[0] / maxTrade) * plotWidth;
            double bottomY = height - padBottom;
            String areaPts = firstX + "," + bottomY + " " + pts + " " + lastX + "," + bottomY;
            svg.append(String.format(Locale.US, "  <polygon points=\"%s\" fill=\"rgba(0, 229, 255, 0.12)\" />\n", areaPts));
        }

        svg.append(String.format(Locale.US, "  <polyline points=\"%s\" fill=\"none\" stroke=\"#ffab00\" stroke-width=\"2\" stroke-linejoin=\"round\" />\n", pts));

        // X Axis labels
        svg.append(String.format(Locale.US, "  <text x=\"%d\" y=\"%d\" fill=\"#94a3b8\" font-size=\"10\" text-anchor=\"middle\">Trades (0 bis %.0f)</text>\n",
                padLeft + plotWidth / 2, height - 8, maxTrade));

        svg.append("</svg>\n");
        return svg.toString();
    }

    private static String num(double val) {
        if (!Double.isFinite(val)) return "—";
        return String.format(Locale.US, "%.2f", val);
    }

    private static String blankToDash(String str) {
        return str == null || str.isBlank() ? "—" : str;
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String sanitizeFilename(String input) {
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String getCssStyle() {
        return """
            :root {
              --bg-color: #0b0d13;
              --card-bg: #141822;
              --card-border: #232a3b;
              --text-main: #e6e9f0;
              --text-muted: #9aa4b5;
              --primary: #00e5ff;
              --success: #00e676;
              --danger: #ff5252;
              --warning: #ffd740;
            }

            * { box-sizing: border-box; margin: 0; padding: 0; }
            body {
              font-family: 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, sans-serif;
              background-color: var(--bg-color);
              color: var(--text-main);
              line-height: 1.5;
              padding-bottom: 40px;
            }

            .container {
              max-width: 1200px;
              margin: 0 auto;
              padding: 20px;
            }

            .action-bar {
              position: sticky;
              top: 0;
              z-index: 1000;
              background-color: #161b26;
              border-bottom: 1px solid #2d374d;
              padding: 12px 24px;
              display: flex;
              justify-content: space-between;
              align-items: center;
              box-shadow: 0 4px 12px rgba(0,0,0,0.4);
            }
            .action-bar-title {
              font-weight: bold;
              font-size: 16px;
              color: var(--primary);
            }
            .btn {
              padding: 8px 18px;
              border-radius: 6px;
              font-weight: bold;
              font-size: 14px;
              cursor: pointer;
              border: none;
              transition: all 0.2s ease;
            }
            .btn-primary {
              background-color: var(--primary);
              color: #0b0d13;
            }
            .btn-primary:hover {
              background-color: #33ebff;
              box-shadow: 0 0 10px rgba(0,229,255,0.4);
            }

            .report-header {
              margin-top: 20px;
              margin-bottom: 24px;
            }
            .header-badge {
              display: inline-block;
              padding: 4px 10px;
              background: rgba(0, 229, 255, 0.15);
              color: var(--primary);
              border-radius: 4px;
              font-size: 12px;
              font-weight: bold;
              text-transform: uppercase;
              letter-spacing: 0.5px;
              margin-bottom: 8px;
            }
            .report-header h1 {
              font-size: 28px;
              color: #ffffff;
              margin-bottom: 12px;
            }
            .meta-grid {
              display: grid;
              grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
              gap: 8px 16px;
              background: var(--card-bg);
              padding: 14px 18px;
              border-radius: 8px;
              border: 1px solid var(--card-border);
              font-size: 13px;
            }
            .meta-label { color: var(--text-muted); font-weight: 500; }

            .summary-cards {
              display: grid;
              grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
              gap: 16px;
              margin-bottom: 24px;
            }
            .card {
              background: var(--card-bg);
              border: 1px solid var(--card-border);
              border-radius: 10px;
              padding: 18px;
            }
            .kpi-title { font-size: 12px; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.5px; }
            .kpi-value { font-size: 26px; font-weight: bold; margin: 6px 0; color: #ffffff; }
            .kpi-sub { font-size: 12px; color: var(--text-muted); }

            .section-card { margin-bottom: 24px; }
            .section-card h2 { font-size: 18px; color: var(--primary); margin-bottom: 8px; }
            .section-desc { font-size: 13px; color: var(--text-muted); margin-bottom: 16px; }

            .chart-container {
              background: #0f121a;
              border-radius: 8px;
              padding: 12px;
              border: 1px solid #1a202c;
            }
            .trend-svg, .equity-svg { width: 100%; height: auto; display: block; }

            .data-table {
              width: 100%;
              border-collapse: collapse;
              font-size: 13px;
              text-align: left;
            }
            .data-table th, .data-table td {
              padding: 10px 14px;
              border-bottom: 1px solid var(--card-border);
            }
            .data-table th {
              background: #19202e;
              color: var(--text-muted);
              font-weight: 600;
            }
            .data-table tr:hover { background: rgba(255,255,255,0.02); }
            .data-table tr.confirmed-master-row { background: rgba(0, 229, 255, 0.10); }

            .badge {
              display: inline-block;
              padding: 3px 8px;
              border-radius: 4px;
              font-size: 11px;
              font-weight: bold;
              text-transform: uppercase;
            }
            .badge-success { background: rgba(0, 230, 118, 0.2); color: var(--success); }
            .badge-danger { background: rgba(255, 82, 82, 0.2); color: var(--danger); }
            .badge-warning { background: rgba(255, 215, 64, 0.2); color: var(--warning); }
            .badge-secondary { background: rgba(154, 164, 181, 0.2); color: var(--text-muted); }
            .master-badge { background: rgba(0, 229, 255, 0.18); color: var(--primary); }

            .section-title-bar { margin: 32px 0 16px 0; }
            .section-title-bar h2 { font-size: 20px; color: #ffffff; border-left: 4px solid var(--primary); padding-left: 12px; }

            .pick-card { margin-bottom: 24px; }
            .confirmed-master-card { border: 2px solid var(--primary); }
            .pick-header {
              display: flex;
              justify-content: space-between;
              align-items: center;
              padding-bottom: 12px;
              margin-bottom: 16px;
              border-bottom: 1px solid var(--card-border);
            }
            .pick-seq {
              font-size: 12px;
              color: var(--primary);
              font-weight: bold;
              background: rgba(0, 229, 255, 0.1);
              padding: 2px 6px;
              border-radius: 3px;
              margin-right: 8px;
            }
            .pick-title { display: inline; font-size: 17px; color: #ffffff; }

            .pick-body-grid {
              display: grid;
              grid-template-columns: 1fr 1fr;
              gap: 20px;
            }
            @media (max-width: 900px) {
              .pick-body-grid { grid-template-columns: 1fr; }
            }

            .pick-chart-col h4, .pick-info-col h4, .pick-params-section h4 {
              font-size: 14px;
              color: var(--primary);
              margin-bottom: 10px;
            }

            .equity-svg-wrapper, .mt5-img-wrapper {
              background: #0f121a;
              border: 1px solid #1a202c;
              border-radius: 6px;
              padding: 10px;
            }
            .mt5-img-wrapper img {
              width: 100%;
              height: auto;
              border-radius: 4px;
              display: block;
            }

            .metrics-table, .info-table, .param-table {
              width: 100%;
              border-collapse: collapse;
              font-size: 12px;
            }
            .metrics-table td, .info-table td, .param-table td, .param-table th {
              padding: 6px 10px;
              border-bottom: 1px solid #1c2333;
            }
            .metrics-table td:first-child, .info-table td:first-child {
              color: var(--text-muted);
              width: 50%;
            }

            .pick-params-section {
              margin-top: 18px;
              padding-top: 14px;
              border-top: 1px solid var(--card-border);
            }
            .param-table th {
              background: #181f2c;
              color: var(--text-muted);
              font-weight: 600;
              text-align: left;
            }
            .param-changed { background: rgba(0, 230, 118, 0.08); }

            .txt-success { color: var(--success); font-weight: bold; }
            .txt-danger { color: var(--danger); font-weight: bold; }
            .txt-muted { color: var(--text-muted); }
            .text-accent { color: var(--primary); }
            .text-success { color: var(--success); }
            .text-danger { color: var(--danger); }
            .placeholder-box {
              background: #0f121a;
              padding: 30px;
              text-align: center;
              color: var(--text-muted);
              border-radius: 6px;
              border: 1px dashed var(--card-border);
              font-size: 13px;
            }
            .adopted-list { padding-left: 20px; font-size: 12px; color: var(--text-main); }

            .report-footer {
              text-align: center;
              font-size: 12px;
              color: var(--text-muted);
              margin-top: 40px;
              padding-top: 20px;
              border-top: 1px solid var(--card-border);
            }

            @media print {
              .no-print { display: none !important; }
              body { background-color: #ffffff !important; color: #0f172a !important; }
              .container { max-width: 100% !important; padding: 0 !important; }
              .card, .meta-grid, .equity-svg-wrapper, .mt5-img-wrapper, .chart-container {
                background: #ffffff !important;
                border: 1px solid #cbd5e1 !important;
                color: #0f172a !important;
                box-shadow: none !important;
                break-inside: avoid;
              }
              .report-header h1, .pick-title, .kpi-value, .section-title-bar h2 { color: #0f172a !important; }
              .meta-label, .kpi-title, .kpi-sub, .section-desc, .metrics-table td:first-child, .info-table td:first-child { color: #475569 !important; }
              .data-table th, .param-table th { background: #f1f5f9 !important; color: #334155 !important; }
              .data-table td, .metrics-table td, .info-table td, .param-table td { border-bottom-color: #e2e8f0 !important; color: #0f172a !important; }
              .param-changed { background: #f0fdf4 !important; }
              .badge-success { background: #dcfce7 !important; color: #15803d !important; }
              .badge-danger { background: #fee2e2 !important; color: #b91c1c !important; }
              .badge-warning { background: #fef9c3 !important; color: #a16207 !important; }
              .badge-secondary { background: #f1f5f9 !important; color: #475569 !important; }
            }
            """;
    }
}
