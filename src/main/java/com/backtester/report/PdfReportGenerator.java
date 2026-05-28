package com.backtester.report;

import com.backtester.engine.WorkflowEngine;
import com.backtester.engine.BacktestConfig;
import com.backtester.engine.OptimizationConfig;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Element;
import com.lowagie.text.Phrase;
import com.lowagie.text.Chunk;

import java.io.*;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * Generates beautiful, premium PDF reports for optimized EAs and trading portfolios.
 * Includes synthetic equity curves and parameter sweep charts drawn via Java2D.
 */
public class PdfReportGenerator {

    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Font.BOLD, new Color(15, 23, 42));
    private static final Font SUBTITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA, 12, Font.ITALIC, new Color(71, 85, 105));
    private static final Font SECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.BOLD, new Color(30, 41, 59));
    private static final Font BOLD_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, new Color(15, 23, 42));
    private static final Font TEXT_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, new Color(51, 65, 85));
    private static final Font SMALL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, new Color(100, 116, 139));
    private static final Font GREEN_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, new Color(34, 197, 94));
    private static final Font RED_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, new Color(239, 68, 68));

    public static class Point2D {
        public final double x;
        public final double y;
        public Point2D(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class ParameterSweep {
        public String paramName;
        public String period;
        public double cv;
        public String verdict;
        public double baseVal;
        public double baseProfit;
        public List<Point2D> curvePoints;
    }

    /**
     * Generates a detailed PDF report for a single strategy.
     */
    public static void generateReport(WorkflowEngine engine, CombinedPass cp, File file) throws Exception {
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(file));
        document.open();

        Pass bt = cp.getBacktestPass();
        Pass fw = cp.getForwardPass();
        int passNum = cp.getPassNumber();
        String eaName = extractEaName(engine.getExpert());

        // Header Section
        Paragraph header = new Paragraph(eaName + " - Strategy Report", TITLE_FONT);
        header.setAlignment(Element.ALIGN_CENTER);
        document.add(header);

        Paragraph subHeader = new Paragraph("Pass " + passNum + " | Symbol: " + engine.getSymbol() + " | Period: " + engine.getPeriod(), SUBTITLE_FONT);
        subHeader.setAlignment(Element.ALIGN_CENTER);
        subHeader.setSpacingAfter(20);
        document.add(subHeader);

        // Section 1: Meta Information
        document.add(new Paragraph("1. Workflow & Settings", SECTION_FONT));
        document.add(new Paragraph(" ", SMALL_FONT));

        PdfPTable metaTable = new PdfPTable(2);
        metaTable.setWidthPercentage(100);
        metaTable.setSpacingAfter(15);

        addTableCell(metaTable, "Expert Advisor:", BOLD_FONT);
        addTableCell(metaTable, engine.getExpert(), TEXT_FONT);
        addTableCell(metaTable, "Symbol / Timeframe:", BOLD_FONT);
        addTableCell(metaTable, engine.getSymbol() + " / " + engine.getPeriod(), TEXT_FONT);
        addTableCell(metaTable, "Optimierungs-Zeitraum:", BOLD_FONT);
        addTableCell(metaTable, engine.getFromDate() + " bis " + engine.getToDate(), TEXT_FONT);

        String tickModelName = "Unbekannt";
        if (engine.getTickModel() >= 0 && engine.getTickModel() < OptimizationConfig.MODEL_NAMES.length) {
            tickModelName = OptimizationConfig.MODEL_NAMES[engine.getTickModel()];
        }
        addTableCell(metaTable, "Simulations-Modell:", BOLD_FONT);
        addTableCell(metaTable, tickModelName, TEXT_FONT);

        addTableCell(metaTable, "Startkapital / Währung:", BOLD_FONT);
        addTableCell(metaTable, engine.getDeposit() + " " + engine.getCurrency(), TEXT_FONT);
        addTableCell(metaTable, "Hebel:", BOLD_FONT);
        addTableCell(metaTable, engine.getLeverage(), TEXT_FONT);

        document.add(metaTable);

        // Section 2: Performance Results
        document.add(new Paragraph("2. Performance-Daten", SECTION_FONT));
        document.add(new Paragraph(" ", SMALL_FONT));

        PdfPTable perfTable = new PdfPTable(3);
        perfTable.setWidthPercentage(100);
        perfTable.setSpacingAfter(15);
        perfTable.setWidths(new float[]{40, 30, 30});

        // Header Row
        addTableHeaderCell(perfTable, "Metrik");
        addTableHeaderCell(perfTable, "Backtest (In-Sample)");
        addTableHeaderCell(perfTable, "Forward (Out-of-Sample)");

        addTableCell(perfTable, "Nettogewinn:", BOLD_FONT);
        addTableCellColored(perfTable, formatDouble(bt.getProfit()), bt.getProfit() >= 0 ? GREEN_FONT : RED_FONT);
        addTableCellColored(perfTable, fw != null ? formatDouble(fw.getProfit()) : "-", fw != null ? (fw.getProfit() >= 0 ? GREEN_FONT : RED_FONT) : TEXT_FONT);

        addTableCell(perfTable, "Profit Factor:", BOLD_FONT);
        addTableCell(perfTable, formatDouble(bt.getProfitFactor()), TEXT_FONT);
        addTableCell(perfTable, fw != null ? formatDouble(fw.getProfitFactor()) : "-", TEXT_FONT);

        addTableCell(perfTable, "Ausgeführte Trades:", BOLD_FONT);
        addTableCell(perfTable, String.valueOf(bt.getTotalTrades()), TEXT_FONT);
        addTableCell(perfTable, fw != null ? String.valueOf(fw.getTotalTrades()) : "-", TEXT_FONT);

        addTableCell(perfTable, "Max. Drawdown %:", BOLD_FONT);
        addTableCellColored(perfTable, formatDouble(bt.getDrawdownPercent()) + "%", bt.getDrawdownPercent() > 25 ? RED_FONT : GREEN_FONT);
        addTableCellColored(perfTable, fw != null ? formatDouble(fw.getDrawdownPercent()) + "%" : "-", fw != null && fw.getDrawdownPercent() > 25 ? RED_FONT : GREEN_FONT);

        addTableCell(perfTable, "Recovery Factor:", BOLD_FONT);
        addTableCell(perfTable, formatDouble(bt.getRecoveryFactor()), TEXT_FONT);
        addTableCell(perfTable, fw != null ? formatDouble(fw.getRecoveryFactor()) : "-", TEXT_FONT);

        addTableCell(perfTable, "Sharpe Ratio:", BOLD_FONT);
        addTableCell(perfTable, formatDouble(bt.getSharpeRatio()), TEXT_FONT);
        addTableCell(perfTable, fw != null ? formatDouble(fw.getSharpeRatio()) : "-", TEXT_FONT);

        addTableCell(perfTable, "Worst Parameter CV:", BOLD_FONT);
        double btCv = engine.getWorstCvForPass(passNum, false);
        double fwCv = engine.getWorstCvForPass(passNum, true);
        addTableCellColored(perfTable, btCv > 0 ? String.format(Locale.US, "%.2f %%", btCv) : "-", btCv > 60 ? RED_FONT : GREEN_FONT);
        addTableCellColored(perfTable, fwCv > 0 ? String.format(Locale.US, "%.2f %%", fwCv) : "-", fwCv > 60 ? RED_FONT : GREEN_FONT);

        addTableCell(perfTable, "KI Stabilität Score:", BOLD_FONT);
        int kiScore = engine.getKiScoreForPass(passNum);
        addTableCellColored(perfTable, kiScore >= 0 ? String.valueOf(kiScore) + " / 100" : "-", kiScore >= 70 ? GREEN_FONT : (kiScore >= 50 ? BOLD_FONT : RED_FONT));
        addTableCell(perfTable, "-", TEXT_FONT);

        addTableCell(perfTable, "Unified Score / Gesamtwert:", BOLD_FONT);
        addTableCell(perfTable, formatDouble(cp.getScore()) + " / 100", TEXT_FONT);
        double wScore = kiScore >= 0 ? (engine.getPerformanceWeight() * cp.getScore() + engine.getStabilityWeight() * kiScore) : cp.getScore();
        addTableCellColored(perfTable, String.format(Locale.US, "%.1f / 100", wScore), GREEN_FONT);

        document.add(perfTable);

        // Section 3: Equity Curve Chart
        document.add(new Paragraph("3. Handels-Verlauf (Synthetische Äquitätskurve)", SECTION_FONT));
        document.add(new Paragraph(" ", SMALL_FONT));

        List<Double> eqPoints = generateSyntheticEquity(bt, passNum);
        BufferedImage equityChart = drawEquityChart(eqPoints, 1500, 720);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(equityChart, "png", baos);
        Image eqImg = Image.getInstance(baos.toByteArray());
        eqImg.scaleAbsolute(500, 240);
        eqImg.setAlignment(Element.ALIGN_CENTER);
        eqImg.setSpacingAfter(15);
        document.add(eqImg);

        // Section 4: Parameters
        document.add(new Paragraph("4. Strategie-Parameter (Settings)", SECTION_FONT));
        document.add(new Paragraph(" ", SMALL_FONT));

        PdfPTable paramsTable = new PdfPTable(2);
        paramsTable.setWidthPercentage(100);
        paramsTable.setSpacingAfter(15);
        paramsTable.setWidths(new float[]{50, 50});

        addTableHeaderCell(paramsTable, "Parameter Name");
        addTableHeaderCell(paramsTable, "Wert");

        Map<String, String> pVals = bt.getParameterValues();
        for (Map.Entry<String, String> entry : pVals.entrySet()) {
            addTableCell(paramsTable, entry.getKey(), BOLD_FONT);
            addTableCell(paramsTable, entry.getValue(), TEXT_FONT);
        }
        document.add(paramsTable);

        // Section 5: KI Bewertung
        document.add(new Paragraph("5. KI-Stabilitätsanalyse & Robustheitsurteil", SECTION_FONT));
        document.add(new Paragraph(" ", SMALL_FONT));

        String passKiReview = extractPassKiReport(engine.getKiReportText(), passNum);
        Paragraph kiPara = new Paragraph(passKiReview, TEXT_FONT);
        kiPara.setSpacingAfter(20);
        document.add(kiPara);

        // Section 6: Sensitivity Curves (if available)
        List<ParameterSweep> sweeps = loadParameterSweeps(passNum, engine.getExpert(), engine.getSymbol());
        if (!sweeps.isEmpty()) {
            document.add(new Paragraph("6. Parameter-Sensitivitäts-Kennlinien", SECTION_FONT));
            document.add(new Paragraph(" ", SMALL_FONT));

            PdfPTable sweepTable = new PdfPTable(2);
            sweepTable.setWidthPercentage(100);
            sweepTable.setSpacingAfter(15);

            for (ParameterSweep sweep : sweeps) {
                if (sweep.curvePoints == null || sweep.curvePoints.isEmpty()) continue;

                // Add text details cell
                PdfPCell textCell = new PdfPCell();
                textCell.setBorderColor(new Color(226, 232, 240));
                textCell.setPadding(8);
                textCell.addElement(new Paragraph("Parameter: " + sweep.paramName, BOLD_FONT));
                textCell.addElement(new Paragraph("Zeitraum: " + sweep.period, SMALL_FONT));
                textCell.addElement(new Paragraph("Variationskoeffizient (CV): " + String.format(Locale.US, "%.2f %%", sweep.cv), TEXT_FONT));
                textCell.addElement(new Paragraph("Urteil: " + sweep.verdict, sweep.verdict.equals("ROBUST") ? GREEN_FONT : RED_FONT));
                textCell.addElement(new Paragraph("Basiswert: " + sweep.baseVal + " (Gewinn: " + formatDouble(sweep.baseProfit) + ")", TEXT_FONT));
                sweepTable.addCell(textCell);

                // Add chart cell
                BufferedImage sweepChart = drawSensitivityChart(sweep.paramName, sweep.curvePoints, sweep.baseVal, sweep.baseProfit, 840, 480);
                ByteArrayOutputStream sbaos = new ByteArrayOutputStream();
                javax.imageio.ImageIO.write(sweepChart, "png", sbaos);
                Image sImg = Image.getInstance(sbaos.toByteArray());
                sImg.scaleAbsolute(280, 160);
                sImg.setAlignment(Element.ALIGN_CENTER);

                PdfPCell chartCell = new PdfPCell();
                chartCell.setBorderColor(new Color(226, 232, 240));
                chartCell.setPadding(5);
                chartCell.addElement(sImg);

                // Add values table under the chart
                PdfPTable pointsTable = new PdfPTable(2);
                pointsTable.setWidthPercentage(90);
                pointsTable.setSpacingBefore(8);
                pointsTable.setHorizontalAlignment(Element.ALIGN_CENTER);
                pointsTable.setWidths(new float[]{50, 50});

                // Headers
                PdfPCell h1 = new PdfPCell(new Phrase("Wert", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.BOLD, Color.WHITE)));
                h1.setBackgroundColor(new Color(30, 41, 59));
                h1.setBorderColor(new Color(226, 232, 240));
                h1.setPadding(3);
                h1.setHorizontalAlignment(Element.ALIGN_CENTER);
                pointsTable.addCell(h1);

                PdfPCell h2 = new PdfPCell(new Phrase("Gewinn", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.BOLD, Color.WHITE)));
                h2.setBackgroundColor(new Color(30, 41, 59));
                h2.setBorderColor(new Color(226, 232, 240));
                h2.setPadding(3);
                h2.setHorizontalAlignment(Element.ALIGN_CENTER);
                pointsTable.addCell(h2);

                // Add curve points
                for (Point2D pt : sweep.curvePoints) {
                    boolean isBase = Math.abs(pt.x - sweep.baseVal) < 0.0001;

                    Font cellFont = isBase ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.BOLD, new Color(15, 23, 42)) : FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, new Color(51, 65, 85));
                    Color cellBg = isBase ? new Color(254, 243, 199) : Color.WHITE;

                    String xStr = isBase ? String.valueOf(pt.x) + " (Basis)" : String.valueOf(pt.x);
                    PdfPCell cellX = new PdfPCell(new Phrase(xStr, cellFont));
                    cellX.setBackgroundColor(cellBg);
                    cellX.setBorderColor(new Color(226, 232, 240));
                    cellX.setPadding(3);
                    cellX.setHorizontalAlignment(Element.ALIGN_CENTER);
                    pointsTable.addCell(cellX);

                    PdfPCell cellY = new PdfPCell(new Phrase(formatDouble(pt.y), cellFont));
                    cellY.setBackgroundColor(cellBg);
                    cellY.setBorderColor(new Color(226, 232, 240));
                    cellY.setPadding(3);
                    cellY.setHorizontalAlignment(Element.ALIGN_CENTER);
                    pointsTable.addCell(cellY);
                }

                chartCell.addElement(pointsTable);
                sweepTable.addCell(chartCell);
            }
            document.add(sweepTable);
        }

        document.close();
    }

    /**
     * Generates a combined portfolio PDF summary report.
     */
    public static void generatePortfolioReport(WorkflowEngine engine, List<CombinedPass> passes, File file) throws Exception {
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(file));
        document.open();

        String eaName = extractEaName(engine.getExpert());

        // Header
        Paragraph header = new Paragraph("Portfolio Export Report", TITLE_FONT);
        header.setAlignment(Element.ALIGN_CENTER);
        document.add(header);

        Paragraph subHeader = new Paragraph("Zusammenfassung des finalen Handels-Portfolios", SUBTITLE_FONT);
        subHeader.setAlignment(Element.ALIGN_CENTER);
        subHeader.setSpacingAfter(25);
        document.add(subHeader);

        // Section 1: Workflow Meta Settings
        document.add(new Paragraph("Workflow-Konfiguration", SECTION_FONT));
        document.add(new Paragraph(" ", SMALL_FONT));

        PdfPTable metaTable = new PdfPTable(2);
        metaTable.setWidthPercentage(100);
        metaTable.setSpacingAfter(20);

        addTableCell(metaTable, "Handelsroboter (EA):", BOLD_FONT);
        addTableCell(metaTable, engine.getExpert(), TEXT_FONT);
        addTableCell(metaTable, "Symbol / Timeframe:", BOLD_FONT);
        addTableCell(metaTable, engine.getSymbol() + " / " + engine.getPeriod(), TEXT_FONT);
        addTableCell(metaTable, "Test-Zeitraum:", BOLD_FONT);
        addTableCell(metaTable, engine.getFromDate() + " bis " + engine.getToDate(), TEXT_FONT);
        
        String tickModelName = "Unbekannt";
        if (engine.getTickModel() >= 0 && engine.getTickModel() < OptimizationConfig.MODEL_NAMES.length) {
            tickModelName = OptimizationConfig.MODEL_NAMES[engine.getTickModel()];
        }
        addTableCell(metaTable, "Simulations-Genauigkeit:", BOLD_FONT);
        addTableCell(metaTable, tickModelName, TEXT_FONT);
        addTableCell(metaTable, "Einzahlung / Hebel:", BOLD_FONT);
        addTableCell(metaTable, engine.getDeposit() + " " + engine.getCurrency() + " / Hebel " + engine.getLeverage(), TEXT_FONT);
        addTableCell(metaTable, "Anzahl exportierter Strategien:", BOLD_FONT);
        addTableCell(metaTable, String.valueOf(passes.size()), TEXT_FONT);

        document.add(metaTable);

        // Section 2: Overview Comparison Table
        document.add(new Paragraph("Vergleich der selektierten Portfolio-Strategien", SECTION_FONT));
        document.add(new Paragraph(" ", SMALL_FONT));

        PdfPTable compTable = new PdfPTable(7);
        compTable.setWidthPercentage(100);
        compTable.setSpacingAfter(20);
        compTable.setWidths(new float[]{10, 15, 15, 15, 15, 15, 15});

        addTableHeaderCell(compTable, "Pass");
        addTableHeaderCell(compTable, "Unified Score");
        addTableHeaderCell(compTable, "KI Score");
        addTableHeaderCell(compTable, "Gesamtwert");
        addTableHeaderCell(compTable, "BT Profit");
        addTableHeaderCell(compTable, "FW Profit");
        addTableHeaderCell(compTable, "BT Max DD");

        for (CombinedPass cp : passes) {
            int kiScore = engine.getKiScoreForPass(cp.getPassNumber());
            double wScore = kiScore >= 0 ? (engine.getPerformanceWeight() * cp.getScore() + engine.getStabilityWeight() * kiScore) : cp.getScore();

            addTableCell(compTable, String.valueOf(cp.getPassNumber()), BOLD_FONT);
            addTableCell(compTable, formatDouble(cp.getScore()), TEXT_FONT);
            addTableCell(compTable, kiScore >= 0 ? String.valueOf(kiScore) : "-", TEXT_FONT);
            addTableCellColored(compTable, String.format(Locale.US, "%.1f", wScore), GREEN_FONT);
            addTableCellColored(compTable, formatDouble(cp.getBtProfit()), cp.getBtProfit() >= 0 ? GREEN_FONT : RED_FONT);
            addTableCellColored(compTable, Double.isNaN(cp.getFwProfit()) ? "-" : formatDouble(cp.getFwProfit()), cp.getFwProfit() >= 0 ? GREEN_FONT : RED_FONT);
            addTableCellColored(compTable, formatDouble(cp.getBtDd()) + "%", cp.getBtDd() > 25 ? RED_FONT : GREEN_FONT);
        }

        document.add(compTable);

        // Section 3: LLM Global Assessment
        if (engine.getKiReportText() != null && !engine.getKiReportText().isEmpty()) {
            document.add(new Paragraph("Globaler KI-Stabilitätsbericht", SECTION_FONT));
            document.add(new Paragraph(" ", SMALL_FONT));
            
            // Extract only the explanation summary part
            String reportText = engine.getKiReportText();
            int part3Idx = reportText.indexOf("TEIL 3");
            if (part3Idx != -1) {
                reportText = reportText.substring(part3Idx);
            }
            
            Paragraph reportPara = new Paragraph(reportText, TEXT_FONT);
            document.add(reportPara);
        }

        document.close();
    }

    // --- Helper Methods ---

    private static String extractEaName(String expert) {
        if (expert == null || expert.isEmpty()) return "UnknownEA";
        int lastBackslash = expert.lastIndexOf('\\');
        int lastSlash = expert.lastIndexOf('/');
        int idx = Math.max(lastBackslash, lastSlash);
        String name = idx != -1 ? expert.substring(idx + 1) : expert;
        if (name.toLowerCase().endsWith(".ex5")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }

    private static String formatDouble(double val) {
        if (Double.isNaN(val)) return "-";
        return String.format(Locale.US, "%.2f", val);
    }

    private static void addTableCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorderColor(new Color(226, 232, 240));
        cell.setPadding(6);
        table.addCell(cell);
    }

    private static void addTableCellColored(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorderColor(new Color(226, 232, 240));
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private static void addTableHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, Color.WHITE)));
        cell.setBackgroundColor(new Color(30, 41, 59));
        cell.setBorderColor(new Color(51, 65, 85));
        cell.setPadding(8);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private static List<Double> generateSyntheticEquity(Pass pass, int passNum) {
        double startBalance = 10000.0;
        double profit = pass.getProfit();
        int trades = pass.getTotalTrades();
        double pf = pass.getProfitFactor();
        if (pf <= 1.0) pf = 1.05;

        double grossLoss = profit / Math.max(pf - 1.0, 0.01);
        double grossWin = profit + grossLoss;
        int wins = (int) Math.round(trades * 0.55);
        int losses = trades - wins;

        double avgWin = grossWin / Math.max(wins, 1);
        double avgLoss = grossLoss / Math.max(losses, 1);

        List<Double> curve = new ArrayList<>();
        curve.add(startBalance);

        Random rng = new Random(passNum * 31L + trades);
        double balance = startBalance;
        for (int i = 0; i < trades; i++) {
            boolean isWin = rng.nextDouble() < 0.55;
            balance += isWin ? avgWin : -avgLoss;
            curve.add(balance);
        }
        return curve;
    }

    private static String extractPassKiReport(String fullReport, int passNum) {
        if (fullReport == null || fullReport.isEmpty()) {
            return "Keine KI-Bewertung vorhanden.";
        }
        String searchStr = "Pass " + passNum;
        String[] lines = fullReport.split("\n");
        for (String line : lines) {
            if (line.contains(searchStr)) {
                return line.trim();
            }
        }
        searchStr = String.valueOf(passNum);
        for (String line : lines) {
            if (line.contains(searchStr) && (line.contains("Pass") || line.contains("STABILITY_SCORE"))) {
                if (!line.startsWith("STABILITY_SCORE")) {
                    return line.trim();
                }
            }
        }
        return "Keine detaillierte KI-Begründung für Pass " + passNum + " gefunden.";
    }

    private static List<Point2D> parseCurveJson(String curveJson) {
        List<Point2D> list = new ArrayList<>();
        try {
            com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(curveJson).getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                com.google.gson.JsonObject pt = arr.get(i).getAsJsonObject();
                double x = pt.get("paramValue").getAsDouble();
                double y = pt.get("profit").getAsDouble();
                list.add(new Point2D(x, y));
            }
        } catch (Exception ignored) {}
        return list;
    }

    private static List<ParameterSweep> loadParameterSweeps(int passNum, String expertName, String symbol) {
        List<ParameterSweep> sweeps = new ArrayList<>();
        com.backtester.database.DatabaseManager db = com.backtester.database.DatabaseManager.getInstance();

        long latestTimestamp = -1;
        String tsSql = "SELECT MAX(run_timestamp) as max_ts FROM SENSITIVITY_DETAIL WHERE expert_name = ? AND symbol = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(tsSql)) {
            pstmt.setString(1, expertName);
            pstmt.setString(2, symbol);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    latestTimestamp = rs.getLong("max_ts");
                }
            }
        } catch (Exception ignored) {}

        String sql = "SELECT parameter_name, period, cv, verdict, base_value, base_profit, curve_json " +
                "FROM SENSITIVITY_DETAIL " +
                "WHERE expert_name = ? AND symbol = ? AND pass_number = ? " +
                (latestTimestamp > 0 ? "AND run_timestamp = ? " : "") +
                "ORDER BY parameter_name, period";

        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, expertName);
            pstmt.setString(2, symbol);
            pstmt.setInt(3, passNum);
            if (latestTimestamp > 0) {
                pstmt.setLong(4, latestTimestamp);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    ParameterSweep s = new ParameterSweep();
                    s.paramName = rs.getString("parameter_name");
                    s.period = rs.getString("period");
                    s.cv = rs.getDouble("cv");
                    s.verdict = rs.getString("verdict");
                    try {
                        s.baseVal = Double.parseDouble(rs.getString("base_value"));
                    } catch (Exception e) {
                        s.baseVal = 0.0;
                    }
                    s.baseProfit = rs.getDouble("base_profit");
                    s.curvePoints = parseCurveJson(rs.getString("curve_json"));
                    sweeps.add(s);
                }
            }
        } catch (Exception ignored) {}
        return sweeps;
    }

    private static BufferedImage drawEquityChart(List<Double> points, int width, int height) {
        double scale = width / 500.0;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Premium dark theme colors
        Color bgColor = new Color(23, 27, 38);
        Color gridColor = new Color(45, 55, 72);
        Color lineColor = new Color(0, 230, 118); // Green
        Color axisColor = new Color(100, 116, 139);
        Color textColor = new Color(148, 163, 184);

        g2.setColor(bgColor);
        g2.fillRect(0, 0, width, height);

        if (points == null || points.isEmpty()) {
            g2.setColor(Color.WHITE);
            g2.drawString("Keine Daten", width / 2 - (int)(30 * scale), height / 2);
            g2.dispose();
            return img;
        }

        double minVal = points.stream().min(Double::compare).orElse(0.0);
        double maxVal = points.stream().max(Double::compare).orElse(100.0);
        double valRange = maxVal - minVal;
        if (valRange == 0) {
            minVal -= 100;
            maxVal += 100;
            valRange = 200;
        } else {
            minVal -= valRange * 0.05;
            maxVal += valRange * 0.05;
            valRange = maxVal - minVal;
        }

        int paddingLeft = (int) (55 * scale);
        int paddingRight = (int) (20 * scale);
        int paddingTop = (int) (30 * scale);
        int paddingBottom = (int) (35 * scale);

        int chartW = width - paddingLeft - paddingRight;
        int chartH = height - paddingTop - paddingBottom;

        // Draw Y-Axis grid lines & labels
        g2.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, (int) (8 * scale)));
        int yTicks = 5;
        for (int i = 0; i <= yTicks; i++) {
            double gridVal = minVal + (i * valRange / yTicks);
            int y = paddingTop + chartH - (i * chartH / yTicks);

            g2.setColor(gridColor);
            g2.setStroke(new BasicStroke((float) (1.0f * scale), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1.0f, new float[]{(float)(2f*scale), (float)(2f*scale)}, 0.0f));
            g2.drawLine(paddingLeft, y, paddingLeft + chartW, y);

            g2.setColor(textColor);
            String label = String.format(Locale.US, "%.0f", gridVal);
            int labelW = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, paddingLeft - labelW - (int) (5 * scale), y + (int) (3 * scale));
        }

        // Draw X-Axis grid lines & labels (trade count)
        int n = points.size();
        int xTicks = 5;
        for (int i = 0; i <= xTicks; i++) {
            int tradeIdx = i * (n - 1) / xTicks;
            int x = paddingLeft + (tradeIdx * chartW / (n - 1));

            g2.setColor(gridColor);
            g2.setStroke(new BasicStroke((float) (1.0f * scale), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1.0f, new float[]{(float)(2f*scale), (float)(2f*scale)}, 0.0f));
            g2.drawLine(x, paddingTop, x, paddingTop + chartH);

            g2.setColor(textColor);
            String label = String.valueOf(tradeIdx);
            int labelW = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, x - labelW / 2, paddingTop + chartH + (int) (12 * scale));
        }

        // Draw Axis Lines
        g2.setColor(axisColor);
        g2.setStroke(new BasicStroke((float) (1.5f * scale)));
        g2.drawLine(paddingLeft, paddingTop, paddingLeft, paddingTop + chartH);
        g2.drawLine(paddingLeft, paddingTop + chartH, paddingLeft + chartW, paddingTop + chartH);

        // Draw Curve
        g2.setColor(lineColor);
        g2.setStroke(new BasicStroke((float) (2.0f * scale)));
        for (int i = 0; i < n - 1; i++) {
            int x1 = paddingLeft + (i * chartW / (n - 1));
            int y1 = paddingTop + chartH - (int) ((points.get(i) - minVal) * chartH / valRange);
            int x2 = paddingLeft + ((i + 1) * chartW / (n - 1));
            int y2 = paddingTop + chartH - (int) ((points.get(i + 1) - minVal) * chartH / valRange);
            g2.drawLine(x1, y1, x2, y2);
        }

        // Title and axis labels
        g2.setColor(Color.WHITE);
        g2.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, (int) (10 * scale)));
        g2.drawString("Synthetische Äquitätskurve", paddingLeft, paddingTop - (int) (10 * scale));

        g2.setColor(textColor);
        g2.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, (int) (8 * scale)));
        String tradesLabel = "Trades";
        int tlW = g2.getFontMetrics().stringWidth(tradesLabel);
        g2.drawString(tradesLabel, paddingLeft + chartW / 2 - tlW / 2, paddingTop + chartH + (int) (24 * scale));

        g2.dispose();
        return img;
    }

    private static BufferedImage drawSensitivityChart(String paramName, List<Point2D> points, double baseVal, double baseProfit, int width, int height) {
        double scale = width / 280.0;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Colors
        Color bgColor = new Color(23, 27, 38);
        Color gridColor = new Color(45, 55, 72);
        Color axisColor = new Color(100, 116, 139);
        Color lineColor = new Color(0, 229, 255); // Cyan
        Color textColor = new Color(148, 163, 184);
        Color baseMarkerColor = new Color(255, 215, 64); // Amber

        g2.setColor(bgColor);
        g2.fillRect(0, 0, width, height);

        if (points == null || points.isEmpty()) {
            g2.setColor(Color.WHITE);
            g2.drawString("No Data", width / 2 - (int)(20 * scale), height / 2);
            g2.dispose();
            return img;
        }

        // Calculate limits
        double minX = points.stream().mapToDouble(p -> p.x).min().orElse(0.0);
        double maxX = points.stream().mapToDouble(p -> p.x).max().orElse(1.0);
        double minY = points.stream().mapToDouble(p -> p.y).min().orElse(0.0);
        double maxY = points.stream().mapToDouble(p -> p.y).max().orElse(1.0);

        double rangeX = maxX - minX;
        double rangeY = maxY - minY;
        if (rangeX == 0) rangeX = 1.0;
        if (rangeY == 0) {
            minY -= 10;
            maxY += 10;
            rangeY = 20;
        } else {
            minY -= rangeY * 0.1;
            maxY += rangeY * 0.1;
            rangeY = maxY - minY;
        }

        // Layout bounds with room for labels
        int paddingLeft = (int) (55 * scale);
        int paddingRight = (int) (15 * scale);
        int paddingTop = (int) (25 * scale);
        int paddingBottom = (int) (35 * scale);

        int chartW = width - paddingLeft - paddingRight;
        int chartH = height - paddingTop - paddingBottom;

        // Sort points by X
        points.sort(Comparator.comparingDouble(p -> p.x));

        // Draw Y-Axis Grid Lines & Labels
        g2.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, (int) (8 * scale)));
        int yTicks = 4;
        for (int i = 0; i <= yTicks; i++) {
            double gridVal = minY + (i * rangeY / yTicks);
            int y = paddingTop + chartH - (i * chartH / yTicks);

            // Draw grid line (dashed/thin)
            g2.setColor(gridColor);
            g2.setStroke(new BasicStroke((float) (1.0f * scale), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1.0f, new float[]{(float)(2f*scale), (float)(2f*scale)}, 0.0f));
            g2.drawLine(paddingLeft, y, paddingLeft + chartW, y);

            // Draw label
            g2.setColor(textColor);
            String label = formatProfitValue(gridVal);
            int labelW = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, paddingLeft - labelW - (int) (5 * scale), y + (int) (3 * scale));
        }

        // Draw X-Axis Grid Lines & Labels
        g2.setStroke(new BasicStroke((float) (1.0f * scale), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1.0f, new float[]{(float)(2f*scale), (float)(2f*scale)}, 0.0f));
        for (Point2D pt : points) {
            int x = paddingLeft + (int) ((pt.x - minX) * chartW / rangeX);

            // Draw vertical grid line
            g2.setColor(gridColor);
            g2.drawLine(x, paddingTop, x, paddingTop + chartH);

            // Draw label
            g2.setColor(textColor);
            String label = formatParamValue(pt.x);
            int labelW = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, x - labelW / 2, paddingTop + chartH + (int) (12 * scale));
        }

        // Draw Axis Lines
        g2.setColor(axisColor);
        g2.setStroke(new BasicStroke((float) (1.5f * scale)));
        g2.drawLine(paddingLeft, paddingTop, paddingLeft, paddingTop + chartH);
        g2.drawLine(paddingLeft, paddingTop + chartH, paddingLeft + chartW, paddingTop + chartH);

        // Draw Curve Line
        g2.setColor(lineColor);
        g2.setStroke(new BasicStroke((float) (2.0f * scale)));
        for (int i = 0; i < points.size() - 1; i++) {
            int x1 = paddingLeft + (int) ((points.get(i).x - minX) * chartW / rangeX);
            int y1 = paddingTop + chartH - (int) ((points.get(i).y - minY) * chartH / rangeY);
            int x2 = paddingLeft + (int) ((points.get(i + 1).x - minX) * chartW / rangeX);
            int y2 = paddingTop + chartH - (int) ((points.get(i + 1).y - minY) * chartH / rangeY);
            g2.drawLine(x1, y1, x2, y2);
        }

        // Draw Markers (circles) at each point
        for (Point2D pt : points) {
            int x = paddingLeft + (int) ((pt.x - minX) * chartW / rangeX);
            int y = paddingTop + chartH - (int) ((pt.y - minY) * chartH / rangeY);

            boolean isBase = Math.abs(pt.x - baseVal) < 0.0001;
            if (isBase) {
                g2.setColor(baseMarkerColor);
                g2.fillOval(x - (int) (5 * scale), y - (int) (5 * scale), (int) (10 * scale), (int) (10 * scale));
                g2.setColor(Color.WHITE);
                g2.fillOval(x - (int) (2 * scale), y - (int) (2 * scale), (int) (4 * scale), (int) (4 * scale));
            } else {
                g2.setColor(lineColor);
                g2.fillOval(x - (int) (3 * scale), y - (int) (3 * scale), (int) (6 * scale), (int) (6 * scale));
                g2.setColor(bgColor);
                g2.fillOval(x - (int) (1 * scale), y - (int) (1 * scale), (int) (2 * scale), (int) (2 * scale));
            }
        }

        // Draw Title (Parameter Name)
        g2.setColor(Color.WHITE);
        g2.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, (int) (10 * scale)));
        g2.drawString(paramName, paddingLeft, paddingTop - (int) (10 * scale));

        g2.dispose();
        return img;
    }

    private static String formatParamValue(double val) {
        if (val == (long) val) {
            return String.format(Locale.US, "%d", (long) val);
        } else {
            return String.format(Locale.US, "%.4f", val).replaceAll("0+$", "").replaceAll("\\.$", "");
        }
    }

    private static String formatProfitValue(double val) {
        if (Math.abs(val) >= 1000) {
            return String.format(Locale.US, "%.0f", val);
        } else {
            return String.format(Locale.US, "%.1f", val);
        }
    }
}
