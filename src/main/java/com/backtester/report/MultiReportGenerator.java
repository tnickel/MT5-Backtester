package com.backtester.report;

import com.backtester.engine.MultiBacktestConfig;
import com.backtester.ui.EquityChartPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

public class MultiReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(MultiReportGenerator.class);

    private static class Trade {
        double timestamp;
        double profit;

        public Trade(double timestamp, double profit) {
            this.timestamp = timestamp;
            this.profit = profit;
        }
    }

    public static Path generate(MultiBacktestConfig config, List<BacktestResult> results, Path reportsDirectory) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path reportFile = reportsDirectory.resolve("multi_report_" + timestamp + ".html");

        // Calculate global metrics
        double totalNetProfit = 0;
        int totalTradesAll = 0;
        for (BacktestResult r : results) {
            if (r.isSuccess()) {
                totalNetProfit += r.getTotalProfit();
                totalTradesAll += r.getTotalTrades();
            }
        }

        String dateRangeStr = "N/A";
        if (config.getFromDate() != null && config.getToDate() != null) {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            dateRangeStr = config.getFromDate().format(dtf) + " to " + config.getToDate().format(dtf);
        }

        // Gather all trades chronologically
        List<Trade> allTrades = new ArrayList<>();
        for (BacktestResult r : results) {
            if (!r.isSuccess() || r.getEquityHistory() == null) continue;
            List<double[]> hist = r.getEquityHistory();
            for (int i = 1; i < hist.size(); i++) {
                double[] prev = hist.get(i - 1);
                double[] curr = hist.get(i);
                double profit = curr[1] - prev[1];
                double ts = curr.length > 3 ? curr[3] : 0;
                allTrades.add(new Trade(ts, profit));
            }
        }
        allTrades.sort(Comparator.comparingDouble(t -> t.timestamp));

        // Generate combined portfolio equity history
        List<double[]> combinedEquity = new ArrayList<>();
        double initialDeposit = results.stream()
                .filter(BacktestResult::isSuccess)
                .mapToDouble(BacktestResult::getInitialDeposit)
                .findFirst()
                .orElse(config.getDeposit() > 0 ? config.getDeposit() : 10000.0);

        combinedEquity.add(new double[]{0, initialDeposit, initialDeposit, 0});
        double runningBalance = initialDeposit;
        int tradeIdx = 0;
        for (Trade t : allTrades) {
            tradeIdx++;
            runningBalance += t.profit;
            combinedEquity.add(new double[]{tradeIdx, runningBalance, runningBalance, t.timestamp});
        }

        // Generate monthly profit data
        Map<YearMonth, Double> monthlyProfits = new TreeMap<>();
        Map<YearMonth, Double> monthlyStartBalances = new HashMap<>();
        double tempBalance = initialDeposit;
        for (Trade t : allTrades) {
            if (t.timestamp <= 0) continue;
            LocalDate ld = Instant.ofEpochMilli((long) t.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            YearMonth ym = YearMonth.from(ld);

            if (!monthlyStartBalances.containsKey(ym)) {
                monthlyStartBalances.put(ym, tempBalance);
            }
            monthlyProfits.put(ym, monthlyProfits.getOrDefault(ym, 0.0) + t.profit);
            tempBalance += t.profit;
        }

        // Calculate combined portfolio max drawdown
        double portfolioMaxDdAbs = 0;
        double portfolioMaxDdPct = 0;
        double portfolioPeak = initialDeposit;
        if (combinedEquity.size() > 1) {
            for (double[] point : combinedEquity) {
                double eq = point[2]; // equity
                if (eq > portfolioPeak) {
                    portfolioPeak = eq;
                }
                double ddAbs = portfolioPeak - eq;
                double ddPct = portfolioPeak > 0 ? (ddAbs / portfolioPeak) * 100.0 : 0.0;
                
                if (ddAbs > portfolioMaxDdAbs) {
                    portfolioMaxDdAbs = ddAbs;
                }
                if (ddPct > portfolioMaxDdPct) {
                    portfolioMaxDdPct = ddPct;
                }
            }
        }
        double portfolioRecoveryFactor = portfolioMaxDdAbs > 0 ? totalNetProfit / portfolioMaxDdAbs : 0.0;
        if (portfolioRecoveryFactor < 0) {
            portfolioRecoveryFactor = 0.0;
        }

        // Group trades by month to calculate monthly drawdowns and recovery factors
        Map<YearMonth, List<Trade>> tradesByMonth = new TreeMap<>();
        for (Trade t : allTrades) {
            if (t.timestamp <= 0) continue;
            LocalDate ld = Instant.ofEpochMilli((long) t.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            YearMonth ym = YearMonth.from(ld);
            tradesByMonth.computeIfAbsent(ym, k -> new ArrayList<>()).add(t);
        }

        Map<YearMonth, Double> monthlyDrawdowns = new TreeMap<>();
        Map<YearMonth, Double> monthlyRecoveryFactors = new TreeMap<>();
        for (Map.Entry<YearMonth, List<Trade>> entry : tradesByMonth.entrySet()) {
            YearMonth ym = entry.getKey();
            List<Trade> monthTrades = entry.getValue();
            double startBal = monthlyStartBalances.getOrDefault(ym, initialDeposit);
            
            double peak = startBal;
            double maxDd = 0;
            double tempBal = startBal;
            
            for (Trade t : monthTrades) {
                tempBal += t.profit;
                if (tempBal > peak) {
                    peak = tempBal;
                }
                double dd = peak - tempBal;
                if (dd > maxDd) {
                    maxDd = dd;
                }
            }
            
            monthlyDrawdowns.put(ym, maxDd);
            
            double mProfit = monthlyProfits.getOrDefault(ym, 0.0);
            double mRf = maxDd > 0 && mProfit > 0 ? mProfit / maxDd : 0.0;
            monthlyRecoveryFactors.put(ym, mRf);
        }

        // Render combined charts
        String combinedEquityB64 = "";
        if (combinedEquity.size() > 1) {
            try {
                EquityChartPanel combinedChart = new EquityChartPanel();
                combinedChart.setSize(800, 380);
                combinedChart.setTitle("Accumulated Portfolio Equity Curve (All Trades)");
                combinedChart.setInitialDeposit(initialDeposit);
                combinedChart.setEquityData(combinedEquity);
                combinedChart.doLayout();

                BufferedImage img = new BufferedImage(800, 380, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = img.createGraphics();
                combinedChart.paint(g2);
                g2.dispose();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "png", baos);
                combinedEquityB64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            } catch (Exception e) {
                log.warn("Failed to generate combined equity chart image", e);
            }
        }

        String monthlyBarChartB64 = drawMonthlyProfitBarChart(monthlyProfits, monthlyStartBalances);
        String monthlyDrawdownBarChartB64 = drawMonthlyDrawdownBarChart(monthlyDrawdowns, monthlyStartBalances);
        String monthlyRecoveryFactorBarChartB64 = drawMonthlyRecoveryFactorBarChart(monthlyRecoveryFactors);

        try (Writer writer = Files.newBufferedWriter(reportFile)) {
            writer.write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
            writer.write("<meta charset=\"UTF-8\">\n<title>Multi-Backtest Summary</title>\n");
            writer.write("<style>\n");
            writer.write("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #1e2128; color: #d0d0d0; padding: 10px; margin: 0 auto; max-width: 1000px; }\n");
            writer.write("h1 { margin-top: 5px; margin-bottom: 5px; font-size: 1.4em; color: #4e9af1; }\n");
            writer.write("h2 { margin-top: 15px; margin-bottom: 8px; font-size: 1.2em; color: #4e9af1; }\n");
            writer.write("h3, h4 { color: #4e9af1; margin: 0; }\n");
            writer.write("p.gen-time { margin-top: 0; margin-bottom: 15px; font-size: 0.9em; color: #8c91a0; }\n");
            writer.write("table { border-collapse: collapse; width: 100%; margin-bottom: 15px; font-size: 0.9em; }\n");
            writer.write("th, td { border: 1px solid #3c414b; padding: 4px 8px; text-align: left; }\n");
            writer.write("th { background-color: #2a2e38; color: #fff; }\n");
            writer.write("tr:nth-child(even) { background-color: #232730; }\n");
            writer.write(".status-success { color: #4caf50; font-weight: bold; }\n");
            writer.write(".status-fail { color: #f44336; font-weight: bold; }\n");
            writer.write(".test-container { background: #2a2e38; border: 1px solid #3c414b; padding: 10px; margin-bottom: 10px; border-radius: 5px; }\n");
            writer.write(".test-header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #3c414b; padding-bottom: 5px; margin-bottom: 8px; }\n");
            writer.write(".test-header h3 { font-size: 1.05em; }\n");
            writer.write(".test-stats { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 8px; }\n");
            writer.write(".stat-box { background: #1e2128; padding: 6px 10px; border-radius: 4px; border-left: 3px solid #4e9af1; min-width: 100px; }\n");
            writer.write(".stat-label { font-size: 0.75em; color: #8c91a0; margin-bottom: 2px; text-transform: uppercase; }\n");
            writer.write(".stat-value { font-size: 1.1em; font-weight: bold; color: #fff; }\n");
            writer.write(".img-container h4 { margin-bottom: 5px; font-size: 0.95em; }\n");
            writer.write(".summary-cards { display: flex; flex-wrap: wrap; gap: 15px; margin-bottom: 20px; }\n");
            writer.write(".summary-card { background: #2a2e38; border: 1px solid #3c414b; padding: 12px 16px; border-radius: 6px; border-left: 4px solid #4e9af1; min-width: 180px; flex: 1; }\n");
            writer.write(".summary-label { font-size: 0.8em; color: #8c91a0; margin-bottom: 4px; text-transform: uppercase; letter-spacing: 0.5px; }\n");
            writer.write(".summary-value { font-size: 1.3em; font-weight: bold; color: #fff; }\n");
            writer.write(".combined-charts-container { display: flex; flex-direction: column; gap: 20px; margin-bottom: 25px; }\n");
            writer.write(".combined-chart-box { background: #2a2e38; border: 1px solid #3c414b; padding: 15px; border-radius: 6px; }\n");
            writer.write("</style>\n</head>\n<body>\n");

            writer.write("<h1>Multi-Backtest Summary Report</h1>\n");
            writer.write("<p class='gen-time'>Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "</p>\n");

            // Sum overview cards
            writer.write("<div class='summary-cards'>\n");
            String profitColor = totalNetProfit >= 0 ? "#12b886" : "#fa5252";
            String profitSign = totalNetProfit >= 0 ? "+" : "";
            writer.write("  <div class='summary-card' style='border-left-color: " + profitColor + ";'>\n");
            writer.write("    <div class='summary-label'>Total Net Profit</div>\n");
            writer.write("    <div class='summary-value' style='color: " + profitColor + ";'>" + String.format("%s%.2f", profitSign, totalNetProfit) + "</div>\n");
            writer.write("  </div>\n");
            writer.write("  <div class='summary-card'>\n");
            writer.write("    <div class='summary-label'>Total Trades</div>\n");
            writer.write("    <div class='summary-value'>" + totalTradesAll + "</div>\n");
            writer.write("  </div>\n");
            writer.write("  <div class='summary-card'>\n");
            writer.write("    <div class='summary-label'>Backtest Period</div>\n");
            writer.write("    <div class='summary-value' style='font-size: 1.1em; line-height: 1.5;'>" + dateRangeStr + "</div>\n");
            writer.write("  </div>\n");
            writer.write("  <div class='summary-card' style='border-left-color: #fa5252;'>\n");
            writer.write("    <div class='summary-label'>Portfolio Max Drawdown</div>\n");
            writer.write("    <div class='summary-value' style='color: #fa5252;'>" + String.format("%.2f%% (%.2f)", portfolioMaxDdPct, portfolioMaxDdAbs) + "</div>\n");
            writer.write("  </div>\n");
            writer.write("  <div class='summary-card' style='border-left-color: #f0c83c;'>\n");
            writer.write("    <div class='summary-label'>Portfolio Recovery Factor</div>\n");
            writer.write("    <div class='summary-value' style='color: #f0c83c;'>" + String.format("%.2f", portfolioRecoveryFactor) + "</div>\n");
            writer.write("  </div>\n");
            writer.write("</div>\n");

            // Overview Table
            writer.write("<h2>Overview</h2>\n");
            writer.write("<table>\n");
            writer.write("<tr><th>#</th><th>Robot</th><th>Symbol</th><th>Period</th><th>Trades</th><th>Profit Factor</th><th>Net Profit</th><th>Drawdown</th><th>Status</th></tr>\n");

            int idx = 1;
            for (BacktestResult r : results) {
                String statusObj = r.isSuccess() ? "<span class='status-success'>OK</span>" : "<span class='status-fail'>FAIL</span>";
                writer.write(String.format("<tr><td>%d</td><td>%s</td><td>%s</td><td>%s</td><td>%d</td><td>%.2f</td><td>%.2f</td><td>%.2f%%</td><td>%s</td></tr>\n",
                        idx++, r.getExpert(), r.getSymbol(), r.getPeriod(),
                        r.getTotalTrades(), r.getProfitFactor(), r.getTotalProfit(), r.getMaxDrawdown(), statusObj));
            }
            writer.write("</table>\n");

            // Combined portfolio analysis charts
            if (!combinedEquityB64.isEmpty() || !monthlyBarChartB64.isEmpty() || !monthlyDrawdownBarChartB64.isEmpty() || !monthlyRecoveryFactorBarChartB64.isEmpty()) {
                writer.write("<h2>Portfolio Summary Analysis</h2>\n");
                writer.write("<div class='combined-charts-container'>\n");

                if (!combinedEquityB64.isEmpty()) {
                    writer.write("  <div class='combined-chart-box'>\n");
                    writer.write("    <h3 style='margin-bottom: 10px;'>Combined Equity Curve</h3>\n");
                    writer.write("    <img style='max-width: 100%; border-radius: 4px; border: 1px solid #3c414b;' src='data:image/png;base64," + combinedEquityB64 + "' alt='Combined Equity Curve' />\n");
                    writer.write("  </div>\n");
                }

                if (!monthlyBarChartB64.isEmpty()) {
                    writer.write("  <div class='combined-chart-box'>\n");
                    writer.write("    <h3 style='margin-bottom: 10px;'>Monthly Net Profit (Euros & %)</h3>\n");
                    writer.write("    <img style='max-width: 100%; border-radius: 4px; border: 1px solid #3c414b;' src='data:image/png;base64," + monthlyBarChartB64 + "' alt='Monthly Net Profit' />\n");
                    writer.write("  </div>\n");
                }

                if (!monthlyDrawdownBarChartB64.isEmpty()) {
                    writer.write("  <div class='combined-chart-box'>\n");
                    writer.write("    <h3 style='margin-bottom: 10px;'>Monthly Max Drawdown (Euros & %)</h3>\n");
                    writer.write("    <img style='max-width: 100%; border-radius: 4px; border: 1px solid #3c414b;' src='data:image/png;base64," + monthlyDrawdownBarChartB64 + "' alt='Monthly Max Drawdown' />\n");
                    writer.write("  </div>\n");
                }

                if (!monthlyRecoveryFactorBarChartB64.isEmpty()) {
                    writer.write("  <div class='combined-chart-box'>\n");
                    writer.write("    <h3 style='margin-bottom: 10px;'>Monthly Recovery Factor</h3>\n");
                    writer.write("    <img style='max-width: 100%; border-radius: 4px; border: 1px solid #3c414b;' src='data:image/png;base64," + monthlyRecoveryFactorBarChartB64 + "' alt='Monthly Recovery Factor' />\n");
                    writer.write("  </div>\n");
                }

                writer.write("</div>\n");
            }

            // Individual Test Details
            writer.write("<h2>Detailed Runs</h2>\n");
            idx = 1;
            for (BacktestResult r : results) {
                writer.write("<div class='test-container'>\n");

                writer.write("<div class='test-header'>\n");
                writer.write("<h3>Run #" + idx++ + ": " + r.getExpert() + " on " + r.getSymbol() + " (" + r.getPeriod() + ")</h3>\n");
                if (r.isSuccess()) {
                    writer.write("<span class='status-success'>SUCCESS</span>\n");
                } else {
                    writer.write("<span class='status-fail'>FAILED: " + r.getMessage() + "</span>\n");
                }
                writer.write("</div>\n");

                if (r.isSuccess()) {
                    writer.write("<div class='test-stats'>\n");
                    writer.write("<div class='stat-box'><div class='stat-label'>Net Profit</div><div class='stat-value'>" + String.format("%.2f", r.getTotalProfit()) + "</div></div>\n");
                    writer.write("<div class='stat-box'><div class='stat-label'>Total Trades</div><div class='stat-value'>" + r.getTotalTrades() + "</div></div>\n");
                    writer.write("<div class='stat-box'><div class='stat-label'>Profit Factor</div><div class='stat-value'>" + String.format("%.2f", r.getProfitFactor()) + "</div></div>\n");
                    writer.write("<div class='stat-box'><div class='stat-label'>Drawdown</div><div class='stat-value'>" + String.format("%.2f%%", r.getMaxDrawdown()) + "</div></div>\n");
                    writer.write("<div class='stat-box'><div class='stat-label'>Win Rate</div><div class='stat-value'>" + String.format("%.1f%%", r.getWinRate()) + "</div></div>\n");
                    writer.write("</div>\n");

                    // Render Base64 image using our beautiful EquityChartPanel
                    try {
                        EquityChartPanel chartPanel = new EquityChartPanel();
                        chartPanel.setSize(700, 350);
                        chartPanel.setTitle(r.getExpert() + " - " + r.getSymbol());
                        chartPanel.setFromResult(r);
                        chartPanel.doLayout();

                        BufferedImage img = new BufferedImage(700, 350, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g2 = img.createGraphics();
                        chartPanel.paint(g2);
                        g2.dispose();

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(img, "png", baos);
                        String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());

                        writer.write("<div class='img-container'>\n");
                        writer.write("<h4>Equity Curve</h4>\n");
                        writer.write("<img style='max-width: 480px; width: 100%; border-radius: 4px; border: 1px solid #3c414b;' src='data:image/png;base64," + b64 + "' alt='Equity Graph' />\n");
                        writer.write("</div>\n");
                    } catch (Exception e) {
                        log.warn("Failed to generate and embed equity chart image for test", e);
                    }
                } else {
                    writer.write("<p>No data available due to test failure.</p>\n");
                }

                writer.write("</div>\n");
            }

            writer.write("</body>\n</html>\n");
        } catch (IOException e) {
            log.error("Failed to generate multi report HTML", e);
            return null;
        }

        return reportFile;
    }

    private static String drawMonthlyProfitBarChart(Map<YearMonth, Double> monthlyProfits, Map<YearMonth, Double> monthlyStartBalances) {
        int w = 800;
        int h = 350;
        int MARGIN_LEFT = 80;
        int MARGIN_RIGHT = 30;
        int MARGIN_TOP = 45;
        int MARGIN_BOTTOM = 40;

        int chartW = w - MARGIN_LEFT - MARGIN_RIGHT;
        int chartH = h - MARGIN_TOP - MARGIN_BOTTOM;

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        // Background
        g2.setColor(new Color(22, 25, 33));
        g2.fillRect(0, 0, w, h);

        // Title
        g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g2.setColor(new Color(180, 185, 200));
        g2.drawString("Monthly Profit Summary (Combined)", MARGIN_LEFT, 25);

        if (monthlyProfits.isEmpty()) {
            g2.setFont(new Font("Segoe UI", Font.ITALIC, 14));
            g2.setColor(new Color(100, 108, 125));
            String msg = "No trade data for monthly overview";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
            g2.dispose();
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "png", baos);
                return Base64.getEncoder().encodeToString(baos.toByteArray());
            } catch (Exception e) {
                return "";
            }
        }

        double minProfit = 0;
        double maxProfit = 0;
        for (double p : monthlyProfits.values()) {
            if (p < minProfit) minProfit = p;
            if (p > maxProfit) maxProfit = p;
        }

        double valRange = maxProfit - minProfit;
        if (valRange == 0) {
            maxProfit = 100;
            minProfit = -100;
            valRange = 200;
        } else {
            maxProfit += valRange * 0.15;
            minProfit -= valRange * 0.15;
            valRange = maxProfit - minProfit;
        }

        // Draw grid
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        int gridLines = 5;
        for (int i = 0; i <= gridLines; i++) {
            double val = maxProfit - (double) i / gridLines * valRange;
            int y = MARGIN_TOP + (int) ((double) i / gridLines * chartH);

            g2.setColor(new Color(45, 50, 62));
            g2.drawLine(MARGIN_LEFT, y, MARGIN_LEFT + chartW, y);

            g2.setColor(new Color(100, 108, 125));
            String label = String.format("%.0f", val);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, MARGIN_LEFT - fm.stringWidth(label) - 8, y + 4);
        }

        // Draw zero line if visible
        int yZero = MARGIN_TOP + (int) ((maxProfit / valRange) * chartH);
        if (yZero > MARGIN_TOP && yZero < MARGIN_TOP + chartH) {
            g2.setColor(new Color(200, 200, 200, 80));
            g2.drawLine(MARGIN_LEFT, yZero, MARGIN_LEFT + chartW, yZero);
        }

        int count = monthlyProfits.size();
        int slotWidth = chartW / count;
        int barWidth = (int) (slotWidth * 0.6);
        if (barWidth < 4) barWidth = 4;

        int i = 0;
        for (Map.Entry<YearMonth, Double> entry : monthlyProfits.entrySet()) {
            YearMonth ym = entry.getKey();
            double profit = entry.getValue();
            double startBal = monthlyStartBalances.getOrDefault(ym, 10000.0);
            double pct = startBal > 0 ? (profit / startBal) * 100.0 : 0.0;

            int xCenter = MARGIN_LEFT + i * slotWidth + slotWidth / 2;
            int xBar = xCenter - barWidth / 2;

            int yProfit = MARGIN_TOP + (int) (((maxProfit - profit) / valRange) * chartH);
            int yTop = Math.min(yZero, yProfit);
            int yBottom = Math.max(yZero, yProfit);
            int barHeight = Math.max(2, yBottom - yTop);

            Color barColor = profit >= 0 ? new Color(18, 184, 134) : new Color(250, 82, 82);
            g2.setColor(barColor);
            g2.fillRect(xBar, yTop, barWidth, barHeight);

            g2.setColor(barColor.darker());
            g2.drawRect(xBar, yTop, barWidth, barHeight);

            // Month Label at bottom
            g2.setColor(new Color(180, 185, 200));
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            String monthLabel = ym.toString();
            FontMetrics fm = g2.getFontMetrics();

            if (count > 12) {
                // Diagonal rotated text
                AffineTransform old = g2.getTransform();
                g2.translate(xCenter, MARGIN_TOP + chartH + 12);
                g2.rotate(Math.toRadians(-30));
                g2.drawString(monthLabel, -fm.stringWidth(monthLabel) / 2, 0);
                g2.setTransform(old);
            } else {
                g2.drawString(monthLabel, xCenter - fm.stringWidth(monthLabel) / 2, MARGIN_TOP + chartH + 16);
            }

            // Value text above/below bar
            String valLabel = String.format("%s%.1f%% (%s%.1f)",
                    profit >= 0 ? "+" : "", pct, profit >= 0 ? "+" : "", profit);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 9));
            int labelY = profit >= 0 ? yTop - 5 : yBottom + 12;
            g2.drawString(valLabel, xCenter - fm.stringWidth(valLabel) / 2, labelY);

            i++;
        }

        // Draw border
        g2.setColor(new Color(45, 50, 62));
        g2.drawRect(MARGIN_LEFT, MARGIN_TOP, chartW, chartH);

        g2.dispose();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.error("Failed to write monthly bar chart", e);
            return "";
        }
    }

    private static String drawMonthlyDrawdownBarChart(Map<YearMonth, Double> monthlyDrawdowns, Map<YearMonth, Double> monthlyStartBalances) {
        int w = 800;
        int h = 350;
        int MARGIN_LEFT = 80;
        int MARGIN_RIGHT = 30;
        int MARGIN_TOP = 45;
        int MARGIN_BOTTOM = 40;

        int chartW = w - MARGIN_LEFT - MARGIN_RIGHT;
        int chartH = h - MARGIN_TOP - MARGIN_BOTTOM;

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        // Background
        g2.setColor(new Color(22, 25, 33));
        g2.fillRect(0, 0, w, h);

        // Title
        g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g2.setColor(new Color(180, 185, 200));
        g2.drawString("Monthly Max Drawdown (Combined Portfolio)", MARGIN_LEFT, 25);

        if (monthlyDrawdowns.isEmpty()) {
            g2.setFont(new Font("Segoe UI", Font.ITALIC, 14));
            g2.setColor(new Color(100, 108, 125));
            String msg = "No drawdown data for monthly overview";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
            g2.dispose();
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "png", baos);
                return Base64.getEncoder().encodeToString(baos.toByteArray());
            } catch (Exception e) {
                return "";
            }
        }

        double maxDd = 0;
        for (double dd : monthlyDrawdowns.values()) {
            if (dd > maxDd) maxDd = dd;
        }

        double valRange = maxDd;
        if (valRange == 0) {
            maxDd = 100;
            valRange = 100;
        } else {
            maxDd += valRange * 0.15;
            valRange = maxDd;
        }

        // Draw grid
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        int gridLines = 5;
        for (int i = 0; i <= gridLines; i++) {
            double val = maxDd - (double) i / gridLines * valRange;
            int y = MARGIN_TOP + (int) ((double) i / gridLines * chartH);

            g2.setColor(new Color(45, 50, 62));
            g2.drawLine(MARGIN_LEFT, y, MARGIN_LEFT + chartW, y);

            g2.setColor(new Color(100, 108, 125));
            String label = String.format("%.0f", val);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, MARGIN_LEFT - fm.stringWidth(label) - 8, y + 4);
        }

        int count = monthlyDrawdowns.size();
        int slotWidth = chartW / count;
        int barWidth = (int) (slotWidth * 0.6);
        if (barWidth < 4) barWidth = 4;

        int i = 0;
        int yZero = MARGIN_TOP + chartH;
        for (Map.Entry<YearMonth, Double> entry : monthlyDrawdowns.entrySet()) {
            YearMonth ym = entry.getKey();
            double dd = entry.getValue();
            double startBal = monthlyStartBalances.getOrDefault(ym, 10000.0);
            double pct = startBal > 0 ? (dd / startBal) * 100.0 : 0.0;

            int xCenter = MARGIN_LEFT + i * slotWidth + slotWidth / 2;
            int xBar = xCenter - barWidth / 2;

            int yDd = MARGIN_TOP + (int) (((maxDd - dd) / valRange) * chartH);
            int barHeight = Math.max(2, yZero - yDd);

            Color barColor = new Color(250, 82, 82); // Coral red for drawdown
            g2.setColor(barColor);
            g2.fillRect(xBar, yDd, barWidth, barHeight);

            g2.setColor(barColor.darker());
            g2.drawRect(xBar, yDd, barWidth, barHeight);

            // Month Label at bottom
            g2.setColor(new Color(180, 185, 200));
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            String monthLabel = ym.toString();
            FontMetrics fm = g2.getFontMetrics();

            if (count > 12) {
                AffineTransform old = g2.getTransform();
                g2.translate(xCenter, MARGIN_TOP + chartH + 12);
                g2.rotate(Math.toRadians(-30));
                g2.drawString(monthLabel, -fm.stringWidth(monthLabel) / 2, 0);
                g2.setTransform(old);
            } else {
                g2.drawString(monthLabel, xCenter - fm.stringWidth(monthLabel) / 2, MARGIN_TOP + chartH + 16);
            }

            // Value text above bar
            String valLabel = String.format("%.1f%% (%.0f)", pct, dd);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 9));
            g2.drawString(valLabel, xCenter - fm.stringWidth(valLabel) / 2, yDd - 5);

            i++;
        }

        // Draw border
        g2.setColor(new Color(45, 50, 62));
        g2.drawRect(MARGIN_LEFT, MARGIN_TOP, chartW, chartH);

        g2.dispose();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.error("Failed to write monthly drawdown bar chart", e);
            return "";
        }
    }

    private static String drawMonthlyRecoveryFactorBarChart(Map<YearMonth, Double> monthlyRecoveryFactors) {
        int w = 800;
        int h = 350;
        int MARGIN_LEFT = 80;
        int MARGIN_RIGHT = 30;
        int MARGIN_TOP = 45;
        int MARGIN_BOTTOM = 40;

        int chartW = w - MARGIN_LEFT - MARGIN_RIGHT;
        int chartH = h - MARGIN_TOP - MARGIN_BOTTOM;

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        // Background
        g2.setColor(new Color(22, 25, 33));
        g2.fillRect(0, 0, w, h);

        // Title
        g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g2.setColor(new Color(180, 185, 200));
        g2.drawString("Monthly Recovery Factor (Combined Portfolio)", MARGIN_LEFT, 25);

        if (monthlyRecoveryFactors.isEmpty()) {
            g2.setFont(new Font("Segoe UI", Font.ITALIC, 14));
            g2.setColor(new Color(100, 108, 125));
            String msg = "No recovery factor data for monthly overview";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
            g2.dispose();
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "png", baos);
                return Base64.getEncoder().encodeToString(baos.toByteArray());
            } catch (Exception e) {
                return "";
            }
        }

        double maxRf = 0;
        for (double rf : monthlyRecoveryFactors.values()) {
            if (rf > maxRf) maxRf = rf;
        }

        double valRange = maxRf;
        if (valRange == 0) {
            maxRf = 5;
            valRange = 5;
        } else {
            maxRf += valRange * 0.15;
            valRange = maxRf;
        }

        // Draw grid
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        int gridLines = 5;
        for (int i = 0; i <= gridLines; i++) {
            double val = maxRf - (double) i / gridLines * valRange;
            int y = MARGIN_TOP + (int) ((double) i / gridLines * chartH);

            g2.setColor(new Color(45, 50, 62));
            g2.drawLine(MARGIN_LEFT, y, MARGIN_LEFT + chartW, y);

            g2.setColor(new Color(100, 108, 125));
            String label = String.format("%.2f", val);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, MARGIN_LEFT - fm.stringWidth(label) - 8, y + 4);
        }

        int count = monthlyRecoveryFactors.size();
        int slotWidth = chartW / count;
        int barWidth = (int) (slotWidth * 0.6);
        if (barWidth < 4) barWidth = 4;

        int i = 0;
        int yZero = MARGIN_TOP + chartH;
        for (Map.Entry<YearMonth, Double> entry : monthlyRecoveryFactors.entrySet()) {
            YearMonth ym = entry.getKey();
            double rf = entry.getValue();

            int xCenter = MARGIN_LEFT + i * slotWidth + slotWidth / 2;
            int xBar = xCenter - barWidth / 2;

            int yRf = MARGIN_TOP + (int) (((maxRf - rf) / valRange) * chartH);
            int barHeight = Math.max(2, yZero - yRf);

            Color barColor = new Color(240, 200, 60); // Gold/Yellow for Recovery Factor
            g2.setColor(barColor);
            g2.fillRect(xBar, yRf, barWidth, barHeight);

            g2.setColor(barColor.darker());
            g2.drawRect(xBar, yRf, barWidth, barHeight);

            // Month Label at bottom
            g2.setColor(new Color(180, 185, 200));
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            String monthLabel = ym.toString();
            FontMetrics fm = g2.getFontMetrics();

            if (count > 12) {
                AffineTransform old = g2.getTransform();
                g2.translate(xCenter, MARGIN_TOP + chartH + 12);
                g2.rotate(Math.toRadians(-30));
                g2.drawString(monthLabel, -fm.stringWidth(monthLabel) / 2, 0);
                g2.setTransform(old);
            } else {
                g2.drawString(monthLabel, xCenter - fm.stringWidth(monthLabel) / 2, MARGIN_TOP + chartH + 16);
            }

            // Value text above bar
            String valLabel = String.format("%.2f", rf);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 9));
            g2.drawString(valLabel, xCenter - fm.stringWidth(valLabel) / 2, yRf - 5);

            i++;
        }

        // Draw border
        g2.setColor(new Color(45, 50, 62));
        g2.drawRect(MARGIN_LEFT, MARGIN_TOP, chartW, chartH);

        g2.dispose();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.error("Failed to write monthly recovery factor bar chart", e);
            return "";
        }
    }
}
