package com.backtester.report;

import com.backtester.database.DatabaseManager;
import com.backtester.report.OptimizationResult.CombinedPass;
import java.util.Locale;

public class RobustnessScorecardGenerator {
    /**
     * Generates a fully self-contained HTML page containing the Vue 3 Robustness Scorecard.
     * Inserts the calculated stats for the pass in window.INJECTED_STRATEGY and window.INJECTED_STATS.
     */
    public static String generateHtml(CombinedPass cp, String expert, String symbol, String period, String fromDate, String toDate) {
        return generateHtml(cp, expert, symbol, period, fromDate, toDate, -1.0, -1.0);
    }

    /**
     * Generates HTML with all three score circles: Unified, Sensitiv, KI.
     * Pass -1.0 for sensitivScore or kiScore if they are not available.
     */
    public static String generateHtml(CombinedPass cp, String expert, String symbol, String period, String fromDate, String toDate, double sensitivScore, double kiScore) {
        double years = calculateYears(fromDate, toDate);
        PillarScores s = computePillars(cp, years);

        // Single source of truth for weights & defaults (see ScoreWeights.loadFromDatabase)
        OptimizationResult.ScoreWeights w = OptimizationResult.ScoreWeights.loadFromDatabase();

        double overallScore = s.calculateWeighted(w);

        // Build JSON array for pillars
        StringBuilder pillarsJson = new StringBuilder();
        pillarsJson.append("[\n");

        // 1. BT Profit
        double deposit = cp.getBacktestPass().getBalance() - cp.getBtProfit();
        if (deposit <= 0) deposit = 10000.0;
        double btRoi = cp.getBtProfit() / deposit;
        addPillarJson(pillarsJson, "BT-Profitabilität", "bt_profitability", w.wBtProfit, s.btProfit,
            new String[]{"ROI", "PF"},
            new String[]{String.format(Locale.US, "%.1f%%", btRoi * 100), String.format(Locale.US, "%.2f", cp.getBtPf())},
            new String[]{"'roi'", "'pf'"}
        );
        pillarsJson.append(",\n");

        // 2. FW Profit
        double fwRoi = cp.getForwardPass() != null ? (cp.getFwProfit() / deposit) : 0.0;
        String fwRoiStr = cp.getForwardPass() != null ? String.format(Locale.US, "%.1f%%", fwRoi * 100) : "n/a";
        String fwPfStr = cp.getForwardPass() != null ? String.format(Locale.US, "%.2f", cp.getFwPf()) : "n/a";
        addPillarJson(pillarsJson, "FW-Profitabilität", "fw_profitability", w.wFwProfit, s.fwProfit,
            new String[]{"FW-ROI", "FW-PF"},
            new String[]{fwRoiStr, fwPfStr},
            new String[]{"'fwRoi'", "'pf'"}
        );
        pillarsJson.append(",\n");

        // 3. Consistency
        addPillarJson(pillarsJson, "FW/BT Konsistenz", "consistency", w.wConsistency, s.consistency,
            new String[]{"Ratio"},
            new String[]{String.format(Locale.US, "%.2f", cp.getConsistency())},
            new String[]{"'consistency'"}
        );
        pillarsJson.append(",\n");

        // 4. Risk
        double calmar = cp.getBtRecovery() / years;
        addPillarJson(pillarsJson, "Risiko-Verhältnis", "risk", w.wRisk, s.risk,
            new String[]{"Return/DD", "Calmar"},
            new String[]{String.format(Locale.US, "%.2f", cp.getBtRecovery()), String.format(Locale.US, "%.2f", calmar)},
            new String[]{"'rdd'", "'calmar'"}
        );
        pillarsJson.append(",\n");

        // 5. Sharpe Ratio (echte MT5-Kennzahl — ersetzt die frühere synthetische
        //    "Equity-Konsistenz" aus R²/SQN einer zufällig generierten Kurve)
        String btSharpeStr = String.format(Locale.US, "%.2f", cp.getBtSharpe());
        String fwSharpeStr = cp.getForwardPass() != null ? String.format(Locale.US, "%.2f", cp.getFwSharpe()) : "n/a";
        addPillarJson(pillarsJson, "Sharpe Ratio", "equity_consistency", w.wEquityConsist, s.equityConsist,
            new String[]{"BT-Sharpe", "FW-Sharpe"},
            new String[]{btSharpeStr, fwSharpeStr},
            new String[]{"'btSharpe'", "'fwSharpe'"}
        );
        pillarsJson.append(",\n");

        // 6. Sample size
        addPillarJson(pillarsJson, "Stichprobengröße", "sample_size", w.wSampleSize, s.sampleSize,
            new String[]{"Trades", "Jahre"},
            new String[]{String.valueOf(cp.getBtTrades()), String.format(Locale.US, "%.1f", years)},
            new String[]{"'trades'", "'years'"}
        );
        pillarsJson.append(",\n");

        // 7. FW Trades
        String fwTradesStr = cp.getForwardPass() != null ? String.valueOf(cp.getFwTrades()) : "n/a";
        addPillarJson(pillarsJson, "FW Trade Count", "fw_trades", w.wFwTrades, s.fwTrades,
            new String[]{"FW-Trades"},
            new String[]{fwTradesStr},
            new String[]{"'fwTrades'"}
        );
        pillarsJson.append(",\n");

        // 8. Recovery
        String fwRecStr = cp.getForwardPass() != null ? String.format(Locale.US, "%.2f", cp.getFwRecovery()) : "n/a";
        addPillarJson(pillarsJson, "Erholungsfaktor", "recovery", w.wRecovery, s.recovery,
            new String[]{"BT-Rec", "FW-Rec"},
            new String[]{String.format(Locale.US, "%.2f", cp.getBtRecovery()), fwRecStr},
            new String[]{"'btRecovery'", "'fwRecovery'"}
        );

        pillarsJson.append("\n  ]");

        String eaName = expert;
        if (expert != null && expert.contains("\\")) {
            eaName = expert.substring(expert.lastIndexOf("\\") + 1);
        } else if (expert != null && expert.contains("/")) {
            eaName = expert.substring(expert.lastIndexOf("/") + 1);
        }

        String strategyJson = String.format(Locale.US,
            "window.INJECTED_STRATEGY = {\n" +
            "  projectName: '%s',\n" +
            "  databankName: '%s | %s',\n" +
            "  strategyName: 'Pass #%d'\n" +
            "};",
            eaName, symbol, period, cp.getPassNumber()
        );

        String statsJson = String.format(Locale.US,
            "window.INJECTED_STATS = {\n" +
            "  total: %.0f,\n" +
            "  SensitivScore: %.1f,\n" +
            "  KiScore: %.1f,\n" +
            "  pillars: %s\n" +
            "};",
            overallScore, sensitivScore, kiScore, pillarsJson.toString()
        );

        return RobustnessScorecardHtmlSections.buildHtml(strategyJson, statsJson);
    }

    private static double calculateYears(String fromDate, String toDate) {
        // Shared with the ranking score so both use identical year math
        return OptimizationResult.yearsBetween(fromDate, toDate);
    }

    private static void addPillarJson(StringBuilder sb, String name, String key, double weight, double score, String[] labels, String[] displays, String[] tips) {
        sb.append(String.format(Locale.US, "    {\n" +
            "      name: '%s',\n" +
            "      key: '%s',\n" +
            "      weight: %.0f,\n" +
            "      score: %.0f,\n" +
            "      inputs: [\n", name, key, weight, score));
        for (int i = 0; i < labels.length; i++) {
            boolean missing = "n/a".equals(displays[i]);
            sb.append(String.format(Locale.US, "        { label: '%s', display: '%s', missing: %b, tip: %s }%s\n",
                labels[i], displays[i], missing, tips[i], (i == labels.length - 1 ? "" : ",")));
        }
        sb.append("      ]\n" +
            "    }");
    }

    private static Double normJava(double x, double[][] points) {
        if (x <= points[0][0]) return points[0][1];
        if (x >= points[points.length - 1][0]) return points[points.length - 1][1];
        for (int i = 1; i < points.length; i++) {
            double x0 = points[i - 1][0];
            double y0 = points[i - 1][1];
            double x1 = points[i][0];
            double y1 = points[i][1];
            if (x <= x1) {
                double t = (x - x0) / (x1 - x0);
                return y0 + t * (y1 - y0);
            }
        }
        return points[points.length - 1][1];
    }

    private static Double avgValidJava(Double... scores) {
        double sum = 0;
        int count = 0;
        for (Double s : scores) {
            if (s != null) {
                sum += s;
                count++;
            }
        }
        return count == 0 ? null : sum / count;
    }

    /**
     * Per-pillar scores (0–100). Only pillars backed by real report data —
     * the former synthetic pillars "Symmetrie" and "Tail-Risk" were removed,
     * and "equityConsist" is now the real MT5 Sharpe ratio (see ScoreWeights).
     */
    public static class PillarScores {
        public double btProfit;
        public double fwProfit;
        public double consistency;
        public double risk;
        public double equityConsist;
        public double sampleSize;
        public double fwTrades;
        public double recovery;

        public double calculateWeighted(OptimizationResult.ScoreWeights w) {
            double total = w.total();
            if (total <= 0) total = 1.0;
            return (w.wBtProfit      * btProfit
                  + w.wFwProfit      * fwProfit
                  + w.wConsistency   * consistency
                  + w.wRisk          * risk
                  + w.wEquityConsist * equityConsist
                  + w.wSampleSize    * sampleSize
                  + w.wFwTrades      * fwTrades
                  + w.wRecovery      * recovery) / total;
        }
    }

    /**
     * Computes the 8 pillar scores for a single pass. Uses the same
     * normalisation curves as {@code OptimizationResult.computeScore} so the
     * scorecard display and the Step-3 ranking cannot diverge.
     */
    public static PillarScores computePillars(CombinedPass cp, double years) {
        PillarScores s = new PillarScores();

        double profit = cp.getBtProfit();
        int trades = cp.getBtTrades();
        double pf = cp.getBtPf();
        if (Double.isNaN(pf) || pf <= 1.0) pf = 1.05;
        double rdd = cp.getBtRecovery();
        if (Double.isNaN(rdd) || rdd <= 0) rdd = 0.1;
        if (years <= 0) years = 3.0;

        // 1. BT Profitability
        double deposit = cp.getBacktestPass().getBalance() - profit;
        if (deposit <= 0) deposit = 10000.0;
        double btRoi = profit / deposit;
        double nBtRoi = normJava(btRoi, new double[][]{{0, 0}, {0.10, 50}, {0.30, 100}});
        double nBtPf = normJava(pf, new double[][]{{1.0, 0}, {1.3, 50}, {2.0, 100}});
        s.btProfit = avgValidJava(nBtRoi, nBtPf);

        // 2. FW Profitability
        double fwRoi = cp.getForwardPass() != null ? (cp.getFwProfit() / deposit) : 0.0;
        double nFwRoi = normJava(fwRoi, new double[][]{{0, 0}, {0.03, 50}, {0.10, 100}});
        double nFwPf = cp.getForwardPass() != null ? normJava(cp.getFwPf(), new double[][]{{1.0, 0}, {1.3, 50}, {2.0, 100}}) : 0.0;
        s.fwProfit = avgValidJava(nFwRoi, nFwPf);

        // 3. FW/BT Consistency
        s.consistency = Math.max(0.0, Math.min(1.0, (cp.getConsistency() - 0.2) / 0.8)) * 100.0;

        // 4. Risiko-Verhältnis
        double nRdd = normJava(rdd, new double[][]{{1.0, 0}, {3.0, 50}, {8.0, 100}});
        double calmar = rdd / years;
        double nCalmar = normJava(calmar, new double[][]{{0, 0}, {1.0, 50}, {3.0, 100}});
        s.risk = avgValidJava(nRdd, nCalmar);

        // 5. Sharpe Ratio (real MT5 metric; same PWL as the ranking score)
        double btSharpe = Double.isNaN(cp.getBtSharpe()) ? 0.0 : cp.getBtSharpe();
        double nBtSharpe = normJava(btSharpe, OptimizationResult.SHARPE_PWL);
        if (cp.getForwardPass() != null) {
            double fwSharpe = Double.isNaN(cp.getFwSharpe()) ? 0.0 : cp.getFwSharpe();
            double nFwSharpe = normJava(fwSharpe, OptimizationResult.SHARPE_PWL);
            s.equityConsist = avgValidJava(nBtSharpe, nFwSharpe);
        } else {
            s.equityConsist = nBtSharpe;
        }

        // 6. Stichprobengröße
        double nTrades = normJava(trades, new double[][]{{100, 0}, {300, 50}, {1000, 100}});
        double nYears = normJava(years, new double[][]{{1, 0}, {3, 50}, {7, 100}});
        s.sampleSize = avgValidJava(nTrades, nYears);
        if (trades < 100) {
            s.sampleSize = Math.min(s.sampleSize, 30.0);
        }

        // 7. FW Trade Count (same PWL as the ranking score)
        s.fwTrades = cp.getForwardPass() != null
                ? normJava(cp.getFwTrades(), new double[][]{{100, 0}, {300, 50}, {1000, 100}})
                : 0.0;

        // 8. Erholungsfaktor
        DatabaseManager db = DatabaseManager.getInstance();
        double recMin = Double.parseDouble(db.getSetting("opt.weight.recovery.min", "1.0"));
        double recMax = Double.parseDouble(db.getSetting("opt.weight.recovery.max", "5.0"));
        double recRange = recMax - recMin;
        if (recRange <= 0.0) recRange = 1.0;

        double nBtRec = Math.max(0.0, Math.min(1.0, (cp.getBtRecovery() - recMin) / recRange));
        double nFwRec = cp.getForwardPass() != null ? Math.max(0.0, Math.min(1.0, (cp.getFwRecovery() - recMin) / recRange)) : 0.0;
        s.recovery = ((nBtRec + nFwRec) / 2.0) * 100.0;

        return s;
    }

    public static double calculateOverallScore(CombinedPass cp, String fromDate, String toDate) {
        if (cp == null || cp.getBacktestPass() == null) return 0.0;
        double years = calculateYears(fromDate, toDate);
        PillarScores s = computePillars(cp, years);

        // Single source of truth for weights & defaults (see ScoreWeights.loadFromDatabase)
        OptimizationResult.ScoreWeights w = OptimizationResult.ScoreWeights.loadFromDatabase();

        return s.calculateWeighted(w);
    }
}
