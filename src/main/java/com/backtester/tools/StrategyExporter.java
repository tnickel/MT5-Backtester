package com.backtester.tools;

import com.backtester.database.DatabaseManager;
import com.backtester.database.DatabaseManager.StrategyReview;
import com.backtester.database.DatabaseManager.AutomaticReview;
import com.backtester.database.HistoryRun;
import com.backtester.config.AppConfig;
import com.backtester.config.EaParameter;
import com.backtester.config.EaParameterManager;
import com.backtester.report.BacktestResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.backtester.report.SensitivityResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
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

import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;

/**
 * Headless strategy analysis and export utility.
 * Runs optimization and stability checks, ranks candidate strategies,
 * exports settings (.set) files and generates a detailed PDF report.
 */
public class StrategyExporter {

    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Font.BOLD, new Color(15, 23, 42));
    private static final Font SUBTITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA, 11, Font.ITALIC, new Color(71, 85, 105));
    private static final Font SECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.BOLD, new Color(30, 41, 59));
    private static final Font BOLD_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, new Color(15, 23, 42));
    private static final Font TEXT_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, new Color(51, 65, 85));
    private static final Font SMALL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, new Color(100, 116, 139));
    private static final Font GREEN_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, new Color(34, 197, 94));
    private static final Font RED_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, new Color(239, 68, 68));

    public static class StrategyCandidate {
        public String expert;
        public String symbol;
        public String period;
        public LocalDate fromDate;
        public LocalDate toDate;
        public int deposit;
        public String currency;
        public String leverage;
        public int tickModel;
        public List<EaParameter> baseParameters;
        
        public CombinedPass combinedPass;
        public long runTimestamp;
        public int kiScore;
        public double worstCv;
        public String kiReportText;
        public int runDbId;

        public String reviewText = "";
        public String colorRating = "";

        // 2-year backtest review metrics
        public BacktestResult res2y;
        public BacktestResult res1y;

        // Selection details
        public boolean qualified = false;
        public double rankScore = 0.0;
        public String verdictDetail = "";
    }

    private static Gson buildGson() {
        return new GsonBuilder()
                .registerTypeHierarchyAdapter(javafx.beans.property.StringProperty.class,
                        new com.google.gson.TypeAdapter<javafx.beans.property.StringProperty>() {
                            @Override
                            public void write(com.google.gson.stream.JsonWriter out, javafx.beans.property.StringProperty value) throws java.io.IOException {
                                if (value == null) out.nullValue();
                                else out.value(value.get());
                            }
                            @Override
                            public javafx.beans.property.StringProperty read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
                                if (in.peek() == com.google.gson.stream.JsonToken.NULL) { in.nextNull(); return new javafx.beans.property.SimpleStringProperty(""); }
                                return new javafx.beans.property.SimpleStringProperty(in.nextString());
                            }
                        })
                .create();
    }

    public static void main(String[] args) {
        System.out.println("======================================================================");
        System.out.println("  STARTING STRATEGY ANALYSIS & EXPORT SERVICE");
        System.out.println("======================================================================");

        try {
            DatabaseManager dbManager = DatabaseManager.getInstance();
            EaParameterManager eaParamManager = new EaParameterManager();
            AppConfig appConfig = AppConfig.getInstance();
            Gson gson = buildGson();

            // 1. Data-Aggregation
            System.out.println("1. Aggregating data from SQLite database...");
            List<HistoryRun> runs = dbManager.getRunsByType("Workflow");
            System.out.printf("  Found %d workflow run entries in history.%n", runs.size());

            // Load reviews
            List<StrategyReview> reviewsList = dbManager.getAllStrategyReviews();
            Map<String, StrategyReview> reviewMap = new HashMap<>();
            for (StrategyReview r : reviewsList) {
                String key = r.getExpertName() + "|" + r.getSymbol() + "|" + r.getPeriod() + "|" + r.getRunTimestamp() + "|" + r.getPassNumber();
                reviewMap.put(key, r);
            }

            // Load automatic reviews
            List<AutomaticReview> autoReviewsList = dbManager.getAllAutomaticReviews();
            Map<String, AutomaticReview> autoReviewMap = new HashMap<>();
            for (AutomaticReview ar : autoReviewsList) {
                String key = ar.getExpertName() + "|" + ar.getSymbol() + "|" + ar.getPeriod() + "|" + ar.getRunTimestamp() + "|" + ar.getPassNumber();
                autoReviewMap.put(key, ar);
            }

            List<StrategyCandidate> allCandidates = new ArrayList<>();

            for (HistoryRun run : runs) {
                if (run.getResultJson() == null || run.getResultJson().trim().isEmpty()) {
                    continue;
                }
                try {
                    Map<String, Object> stateMap = gson.fromJson(run.getResultJson(), Map.class);
                    if (stateMap == null) continue;

                    String expert = (String) stateMap.get("expert_name");
                    String symbol = (String) stateMap.get("symbol");
                    String period = (String) stateMap.get("period");
                    
                    LocalDate fromDate = stateMap.get("from_date") != null ? LocalDate.parse((String) stateMap.get("from_date")) : null;
                    LocalDate toDate = stateMap.get("to_date") != null ? LocalDate.parse((String) stateMap.get("to_date")) : null;

                    int deposit = stateMap.get("deposit") != null ? ((Number) stateMap.get("deposit")).intValue() : 10000;
                    String currency = (String) stateMap.get("currency");
                    if (currency == null) currency = "USD";
                    String leverage = (String) stateMap.get("leverage");
                    if (leverage == null) leverage = "1:100";
                    int tickModel = stateMap.get("tick_model") != null ? ((Number) stateMap.get("tick_model")).intValue() : 1;

                    // Parse base parameters
                    List<EaParameter> baseParams = new ArrayList<>();
                    String eaParamsJson = (String) stateMap.get("ea_parameters_json");
                    if (eaParamsJson != null && !eaParamsJson.isEmpty()) {
                        java.lang.reflect.Type listType = new TypeToken<List<EaParameter>>(){}.getType();
                        baseParams = gson.fromJson(eaParamsJson, listType);
                    }

                    // Parse sensitivity results
                    List<SensitivityResult> sensResults = new ArrayList<>();
                    String sensJson = (String) stateMap.get("sensitivity_results_json");
                    if (sensJson != null && !sensJson.isEmpty()) {
                        java.lang.reflect.Type listType = new TypeToken<List<SensitivityResult>>(){}.getType();
                        sensResults = gson.fromJson(sensJson, listType);
                    }

                    // Parse final selected passes
                    List<CombinedPass> finalPasses = new ArrayList<>();
                    String finalPassesJson = (String) stateMap.get("final_selected_passes_json");
                    if (finalPassesJson != null && !finalPassesJson.isEmpty()) {
                        java.lang.reflect.Type listType = new TypeToken<List<CombinedPass>>(){}.getType();
                        finalPasses = gson.fromJson(finalPassesJson, listType);
                    }

                    String kiReportText = (String) stateMap.get("ki_report_text");
                    if (kiReportText == null) kiReportText = "";

                    for (CombinedPass cp : finalPasses) {
                        int kiScore = -1;
                        double worstCv = 0.0;
                        for (SensitivityResult sr : sensResults) {
                            if (sr.getOriginalPass() != null && sr.getOriginalPass().getPassNumber() == cp.getPassNumber()) {
                                String kiRes = sr.getKiResult();
                                if (kiRes != null && !kiRes.isEmpty()) {
                                    try {
                                        kiScore = Integer.parseInt(kiRes.trim());
                                    } catch (NumberFormatException ignored) {}
                                }

                                worstCv = sr.getOverallCV();
                                if (sr.hasForwardCV() && sr.getOverallCVFw() > worstCv) {
                                    worstCv = sr.getOverallCVFw();
                                }
                                break;
                            }
                        }

                        StrategyCandidate sc = new StrategyCandidate();
                        sc.expert = expert;
                        sc.symbol = symbol;
                        sc.period = period;
                        sc.fromDate = fromDate;
                        sc.toDate = toDate;
                        sc.deposit = deposit;
                        sc.currency = currency;
                        sc.leverage = leverage;
                        sc.tickModel = tickModel;
                        sc.baseParameters = baseParams;
                        sc.combinedPass = cp;
                        sc.runTimestamp = run.getTimestamp();
                        sc.kiScore = kiScore;
                        sc.worstCv = worstCv;
                        sc.kiReportText = kiReportText;
                        sc.runDbId = run.getId();

                        // Bind manual review
                        String key = sc.expert + "|" + sc.symbol + "|" + sc.period + "|" + sc.runTimestamp + "|" + sc.combinedPass.getPassNumber();
                        StrategyReview rev = reviewMap.get(key);
                        if (rev != null) {
                            sc.reviewText = rev.getReviewText();
                            sc.colorRating = rev.getColorRating();
                        }

                        // Bind automatic reviews
                        AutomaticReview ar = autoReviewMap.get(key);
                        if (ar != null) {
                            if (ar.getResult2yJson() != null && !ar.getResult2yJson().isEmpty()) {
                                sc.res2y = gson.fromJson(ar.getResult2yJson(), BacktestResult.class);
                            }
                            if (ar.getResult1yJson() != null && !ar.getResult1yJson().isEmpty()) {
                                sc.res1y = gson.fromJson(ar.getResult1yJson(), BacktestResult.class);
                            }
                        }

                        allCandidates.add(sc);
                    }

                } catch (Exception e) {
                    System.err.printf("  Error parsing workflow run ID %d: %s%n", run.getId(), e.getMessage());
                }
            }

            System.out.printf("  Loaded %d strategy candidates in total from all runs.%n", allCandidates.size());

            // 2. Group by Symbol and analyze
            System.out.println("2. Analyzing and comparing strategies grouped by Symbol...");
            Map<String, List<StrategyCandidate>> groupedCandidates = new TreeMap<>();
            for (StrategyCandidate sc : allCandidates) {
                groupedCandidates.computeIfAbsent(sc.symbol, k -> new ArrayList<>()).add(sc);
            }

            Map<String, StrategyCandidate> bestStrategies = new TreeMap<>();

            for (Map.Entry<String, List<StrategyCandidate>> entry : groupedCandidates.entrySet()) {
                String symbol = entry.getKey();
                List<StrategyCandidate> candidates = entry.getValue();

                System.out.printf("  Symbol: %s (%d candidates)%n", symbol, candidates.size());

                for (StrategyCandidate sc : candidates) {
                    // Evaluate qualification
                    if (sc.res2y == null) {
                        sc.qualified = false;
                        sc.verdictDetail = "Verworfen, da kein 2-Jahres-Nachtest vorliegt.";
                        continue;
                    }

                    double drawdown = sc.res2y.getMaxDrawdown();
                    double profit = sc.res2y.getTotalProfit();
                    int trades = sc.res2y.getTotalTrades();

                    if (drawdown > 25.0) {
                        sc.qualified = false;
                        sc.verdictDetail = String.format("Verworfen, da der maximale Drawdown im 2-Jahres-Nachtest mit %.2f%% den Grenzwert von 25%% überschreitet.", drawdown);
                    } else if (profit <= 0.0) {
                        sc.qualified = false;
                        sc.verdictDetail = String.format("Verworfen, da die Strategie im 2-Jahres-Nachtest keinen Gewinn erzielt hat (Nettogewinn: %.2f %s).", profit, sc.currency);
                    } else if (trades < 10) {
                        sc.qualified = false;
                        sc.verdictDetail = String.format("Verworfen, da die Trades-Anzahl im 2-Jahres-Nachtest (%d) statistisch nicht signifikant ist (weniger als 10 Trades).", trades);
                    } else {
                        sc.qualified = true;
                        
                        // Calculate RankScore
                        double baseScore = sc.combinedPass.getScore();
                        double stabilityBonus = 0.0;
                        double riskPenalty = 0.0;
                        double performanceBonus = 0.0;

                        if (sc.kiScore >= 70) stabilityBonus += 15.0;
                        if (sc.worstCv > 0 && sc.worstCv < 30.0) stabilityBonus += 15.0;

                        if (sc.kiScore >= 0 && sc.kiScore < 50) riskPenalty += 15.0;
                        if (sc.worstCv > 60.0) riskPenalty += 15.0;

                        performanceBonus += sc.res2y.getProfitFactor() * 10.0;
                        performanceBonus += sc.res2y.getRecoveryFactor() * 5.0;
                        performanceBonus -= sc.res2y.getMaxDrawdown() * 0.5;

                        sc.rankScore = baseScore + stabilityBonus - riskPenalty + performanceBonus;
                        sc.verdictDetail = String.format("Qualifiziert! RankScore: %.2f (Base: %.1f, Stab-Bonus: +%.0f, Risiko-Malus: -%.0f, Perf-Faktoren: +%.1f)",
                                sc.rankScore, baseScore, stabilityBonus, riskPenalty, performanceBonus);
                    }
                }

                // Pick the best qualified
                StrategyCandidate best = null;
                for (StrategyCandidate sc : candidates) {
                    if (sc.qualified) {
                        if (best == null || sc.rankScore > best.rankScore) {
                            best = sc;
                        }
                    }
                }

                if (best != null) {
                    bestStrategies.put(symbol, best);
                    System.out.printf("    -> Selected Pass %d with RankScore %.2f%n", best.combinedPass.getPassNumber(), best.rankScore);
                    
                    // Mark others as rejected due to better candidate
                    for (StrategyCandidate sc : candidates) {
                        if (sc != best && sc.qualified) {
                            sc.verdictDetail = String.format("Verworfen, da Pass %d für dieses Symbol einen besseren RankScore erzielt hat (%.2f vs %.2f).",
                                    best.combinedPass.getPassNumber(), best.rankScore, sc.rankScore);
                        }
                    }
                } else {
                    System.out.println("    -> No strategy qualified.");
                }
            }

            // 3. Export parameters (.set files)
            Path exportDir = appConfig.getExportDirectory();
            Files.createDirectories(exportDir);
            System.out.printf("3. Exporting parameter files (.set) to: %s%n", exportDir.toAbsolutePath());

            for (Map.Entry<String, StrategyCandidate> entry : bestStrategies.entrySet()) {
                String symbol = entry.getKey();
                StrategyCandidate sc = entry.getValue();

                String eaName = EaParameterManager.extractEaBaseName(sc.expert);
                String tf = sc.period.replaceAll("[^a-zA-Z0-9_.-]", "_");
                String sym = sc.symbol.replaceAll("[^a-zA-Z0-9_.-]", "_");
                double ddVal = (sc.res2y != null) ? sc.res2y.getMaxDrawdown() : sc.combinedPass.getBtDd();
                int ddPct = Double.isNaN(ddVal) ? 0 : (int) Math.round(ddVal);
                String filename = String.format("%s_%s_%s_%dproz_Pass%d.set", eaName, sym, tf, ddPct, sc.combinedPass.getPassNumber());
                Path destPath = exportDir.resolve(filename);

                // Reconstruct full parameters
                List<EaParameter> finalParams = new ArrayList<>();
                for (EaParameter base : sc.baseParameters) {
                    EaParameter p = new EaParameter();
                    p.setName(base.getName());
                    p.setStringType(base.isStringType());
                    p.setSection(base.getSection());
                    
                    String passVal = sc.combinedPass.getBacktestPass().getParameter(base.getName());
                    if (passVal != null && !passVal.isEmpty()) {
                        p.setValue(passVal);
                    } else {
                        // An optimized parameter without a report column was held constant
                        // at its optimize start; MT5 never read the value field for it.
                        p.setValue(com.backtester.report.PassPresetResolver.effectiveBaseValue(base));
                    }
                    if (isMagicNumberParameter(p.getName())) {
                        p.setValue(String.valueOf(sc.combinedPass.getPassNumber()));
                    }
                    if (isOrderCommentParameter(p.getName())) {
                        p.setValue(String.format("%dproz_Pass%d", ddPct, sc.combinedPass.getPassNumber()));
                    }
                    p.setOptimizeEnabled(false);
                    finalParams.add(p);
                }

                eaParamManager.writeSetFile(destPath, finalParams, eaName);
                System.out.printf("  Exported %s parameter settings successfully.%n", symbol);
            }

            // 4. Create the professional PDF Report
            System.out.println("4. Generating professional PDF report...");
            File pdfFile = exportDir.resolve("Controlling_Analysis_Report.pdf").toFile();
            generatePdfReport(allCandidates, bestStrategies, pdfFile);
            System.out.printf("  Report PDF successfully saved to: %s%n", pdfFile.getAbsolutePath());

            // 5. Final summary
            System.out.println("======================================================================");
            System.out.println("  ANALYSIS & EXPORT COMPLETED SUCCESSFULLY!");
            System.out.println("======================================================================");
            System.out.println("Decisions Summary:");
            for (String sym : groupedCandidates.keySet()) {
                StrategyCandidate best = bestStrategies.get(sym);
                if (best != null) {
                    System.out.printf("  %-8s: Pass %-5d | RankScore: %-6.2f | 2y Profit: %-8.2f | 2y DD: %.2f%%%n",
                            sym, best.combinedPass.getPassNumber(), best.rankScore, best.res2y.getTotalProfit(), best.res2y.getMaxDrawdown());
                } else {
                    System.out.printf("  %-8s: Keine qualifizierte Strategie%n", sym);
                }
            }
            System.out.println("Export path: " + exportDir.toAbsolutePath().toString());
            System.out.println("======================================================================");

        } catch (Exception e) {
            System.err.println("Fatal error executing strategy export service:");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static boolean isMagicNumberParameter(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.equals("magic") || lower.equals("inpmagicnumber") || lower.equals("magicnumber");
    }

    private static boolean isOrderCommentParameter(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.equals("comment") || lower.equals("inp_order_comment") || lower.equals("ordercomment") || lower.equals("order_comment");
    }

    private static void generatePdfReport(List<StrategyCandidate> allCandidates, Map<String, StrategyCandidate> bestStrategies, File file) throws Exception {
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(file));
        document.open();

        // Title Page
        Paragraph title = new Paragraph("Controlling-System Strategie-Analyse", TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(15);
        document.add(title);

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        Paragraph subtitle = new Paragraph("System-Report generiert am " + sdf.format(new Date()) + "\nBeste Strategien pro Währungspaar (Symbol)", SUBTITLE_FONT);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(25);
        document.add(subtitle);

        // Section 1: Overview Table
        document.add(new Paragraph("1. Übersicht der Entscheidungen pro Symbol", SECTION_FONT));
        document.add(new Paragraph(" ", SMALL_FONT));

        PdfPTable overviewTable = new PdfPTable(8);
        overviewTable.setWidthPercentage(100);
        overviewTable.setSpacingAfter(25);
        overviewTable.setWidths(new float[]{12, 12, 14, 15, 12, 12, 11, 12});

        addTableHeaderCell(overviewTable, "Symbol");
        addTableHeaderCell(overviewTable, "Gewinner");
        addTableHeaderCell(overviewTable, "RankScore");
        addTableHeaderCell(overviewTable, "2y Gewinn");
        addTableHeaderCell(overviewTable, "2y DD %");
        addTableHeaderCell(overviewTable, "2y Trades");
        addTableHeaderCell(overviewTable, "KI Score");
        addTableHeaderCell(overviewTable, "worst CV");

        Set<String> allSymbols = new TreeSet<>();
        for (StrategyCandidate sc : allCandidates) {
            allSymbols.add(sc.symbol);
        }

        for (String sym : allSymbols) {
            StrategyCandidate best = bestStrategies.get(sym);
            if (best != null) {
                addTableCell(overviewTable, sym, BOLD_FONT);
                addTableCell(overviewTable, "Pass " + best.combinedPass.getPassNumber(), TEXT_FONT);
                addTableCell(overviewTable, String.format(Locale.US, "%.1f", best.rankScore), TEXT_FONT);
                addTableCellColored(overviewTable, String.format(Locale.US, "%.2f %s", best.res2y.getTotalProfit(), best.currency), best.res2y.getTotalProfit() >= 0 ? GREEN_FONT : RED_FONT);
                addTableCellColored(overviewTable, String.format(Locale.US, "%.2f%%", best.res2y.getMaxDrawdown()), best.res2y.getMaxDrawdown() > 25 ? RED_FONT : GREEN_FONT);
                addTableCell(overviewTable, String.valueOf(best.res2y.getTotalTrades()), TEXT_FONT);
                addTableCellColored(overviewTable, best.kiScore >= 0 ? String.valueOf(best.kiScore) : "-", best.kiScore >= 70 ? GREEN_FONT : (best.kiScore >= 50 ? BOLD_FONT : RED_FONT));
                addTableCellColored(overviewTable, best.worstCv > 0 ? String.format(Locale.US, "%.1f%%", best.worstCv) : "-", best.worstCv > 60 ? RED_FONT : GREEN_FONT);
            } else {
                addTableCell(overviewTable, sym, BOLD_FONT);
                PdfPCell cell = new PdfPCell(new Phrase("Keine qualifizierte Strategie", RED_FONT));
                cell.setColspan(7);
                cell.setBorderColor(new Color(226, 232, 240));
                cell.setPadding(6);
                overviewTable.addCell(cell);
            }
        }
        document.add(overviewTable);

        // Section 2: Detailed Justifications grouped by Symbol
        document.add(new Paragraph("2. Detaillierte Begründungen & Kennzahlen", SECTION_FONT));
        document.add(new Paragraph(" ", SMALL_FONT));

        Map<String, List<StrategyCandidate>> grouped = new TreeMap<>();
        for (StrategyCandidate sc : allCandidates) {
            grouped.computeIfAbsent(sc.symbol, k -> new ArrayList<>()).add(sc);
        }

        int count = 1;
        for (Map.Entry<String, List<StrategyCandidate>> entry : grouped.entrySet()) {
            String symbol = entry.getKey();
            List<StrategyCandidate> candidates = entry.getValue();

            Paragraph symHead = new Paragraph(String.format("2.%d Währungspaar %s", count++, symbol), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.BOLD, new Color(15, 23, 42)));
            symHead.setSpacingBefore(15);
            symHead.setSpacingAfter(8);
            document.add(symHead);

            // Add candidates details
            for (StrategyCandidate sc : candidates) {
                String statusText = sc.qualified ? "AUSGEWÄHLT (Gewinner)" : "ABGELEHNT";
                if (!sc.qualified && bestStrategies.containsKey(symbol) && bestStrategies.get(symbol).combinedPass.getPassNumber() == sc.combinedPass.getPassNumber()) {
                    // Selected winner is marked
                } else if (sc.qualified && bestStrategies.get(symbol) != sc) {
                    statusText = "ABGELEHNT (Bessere Alternative vorhanden)";
                }

                Paragraph cardTitle = new Paragraph(String.format("  • Pass %d (%s) - Status: %s", sc.combinedPass.getPassNumber(), EaParameterManager.extractEaBaseName(sc.expert), statusText), BOLD_FONT);
                cardTitle.setSpacingAfter(4);
                document.add(cardTitle);

                Paragraph desc = new Paragraph("    Begründung: " + sc.verdictDetail + (sc.reviewText != null && !sc.reviewText.isEmpty() ? "\n    Manuelles Review: " + sc.reviewText : ""), TEXT_FONT);
                desc.setSpacingAfter(6);
                document.add(desc);

                if (sc.res2y != null) {
                    // Performance Metrics Table
                    PdfPTable perfTable = new PdfPTable(3);
                    perfTable.setWidthPercentage(90);
                    perfTable.setSpacingAfter(10);
                    perfTable.setHorizontalAlignment(Element.ALIGN_CENTER);
                    perfTable.setWidths(new float[]{40, 30, 30});

                    addTableHeaderCell(perfTable, "Metrik");
                    addTableHeaderCell(perfTable, "Original-Lauf (Optimierung)");
                    addTableHeaderCell(perfTable, "2-Jahres Nachtest");

                    addTableCell(perfTable, "Nettogewinn:", BOLD_FONT);
                    addTableCellColored(perfTable, String.format(Locale.US, "%.2f", sc.combinedPass.getBtProfit()), sc.combinedPass.getBtProfit() >= 0 ? GREEN_FONT : RED_FONT);
                    addTableCellColored(perfTable, String.format(Locale.US, "%.2f", sc.res2y.getTotalProfit()), sc.res2y.getTotalProfit() >= 0 ? GREEN_FONT : RED_FONT);

                    addTableCell(perfTable, "Max. Drawdown %:", BOLD_FONT);
                    addTableCellColored(perfTable, String.format(Locale.US, "%.2f%%", sc.combinedPass.getBtDd()), sc.combinedPass.getBtDd() > 25 ? RED_FONT : GREEN_FONT);
                    addTableCellColored(perfTable, String.format(Locale.US, "%.2f%%", sc.res2y.getMaxDrawdown()), sc.res2y.getMaxDrawdown() > 25 ? RED_FONT : GREEN_FONT);

                    addTableCell(perfTable, "Trades:", BOLD_FONT);
                    addTableCell(perfTable, String.valueOf(sc.combinedPass.getBtTrades()), TEXT_FONT);
                    addTableCell(perfTable, String.valueOf(sc.res2y.getTotalTrades()), TEXT_FONT);

                    addTableCell(perfTable, "Profit Factor:", BOLD_FONT);
                    addTableCell(perfTable, String.format(Locale.US, "%.2f", sc.combinedPass.getBtPf()), TEXT_FONT);
                    addTableCell(perfTable, String.format(Locale.US, "%.2f", sc.res2y.getProfitFactor()), TEXT_FONT);

                    addTableCell(perfTable, "Recovery Factor:", BOLD_FONT);
                    addTableCell(perfTable, String.format(Locale.US, "%.2f", sc.combinedPass.getBtRecovery()), TEXT_FONT);
                    addTableCell(perfTable, String.format(Locale.US, "%.2f", sc.res2y.getRecoveryFactor()), TEXT_FONT);

                    document.add(perfTable);

                    // Add equity chart if it is the selected winner
                    if (bestStrategies.get(symbol) == sc && sc.res2y.getEquityHistory() != null && !sc.res2y.getEquityHistory().isEmpty()) {
                        List<Double> eqPoints = new ArrayList<>();
                        for (double[] pt : sc.res2y.getEquityHistory()) {
                            double val = pt.length > 2 ? pt[2] : pt[1];
                            eqPoints.add(val);
                        }

                        BufferedImage equityChart = drawEquityChart(eqPoints, 1200, 480);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        javax.imageio.ImageIO.write(equityChart, "png", baos);
                        Image eqImg = Image.getInstance(baos.toByteArray());
                        eqImg.scaleAbsolute(420, 168);
                        eqImg.setAlignment(Element.ALIGN_CENTER);
                        eqImg.setSpacingAfter(15);
                        document.add(eqImg);
                    }
                }
            }
        }

        document.close();
    }

    private static void addTableCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorderColor(new Color(226, 232, 240));
        cell.setPadding(5);
        table.addCell(cell);
    }

    private static void addTableCellColored(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorderColor(new Color(226, 232, 240));
        cell.setPadding(5);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private static void addTableHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, Color.WHITE)));
        cell.setBackgroundColor(new Color(30, 41, 59));
        cell.setBorderColor(new Color(51, 65, 85));
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
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

        if (points == null || points.size() <= 1) {
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
        g2.drawString("2-Jahre Nachtest Equitykurve", paddingLeft, paddingTop - (int) (10 * scale));

        g2.setColor(textColor);
        g2.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, (int) (8 * scale)));
        String tradesLabel = "Trades";
        int tlW = g2.getFontMetrics().stringWidth(tradesLabel);
        g2.drawString(tradesLabel, paddingLeft + chartW / 2 - tlW / 2, paddingTop + chartH + (int) (24 * scale));

        g2.dispose();
        return img;
    }
}
