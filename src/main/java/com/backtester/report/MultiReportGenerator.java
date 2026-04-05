package com.backtester.report;

import com.backtester.engine.MultiBacktestConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

public class MultiReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(MultiReportGenerator.class);

    public static Path generate(MultiBacktestConfig config, List<BacktestResult> results, Path reportsDirectory) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path reportFile = reportsDirectory.resolve("multi_report_" + timestamp + ".html");

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
            writer.write("</style>\n</head>\n<body>\n");

            writer.write("<h1>Multi-Backtest Summary Report</h1>\n");
            writer.write("<p class='gen-time'>Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "</p>\n");

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
                        com.backtester.ui.EquityChartPanel chartPanel = new com.backtester.ui.EquityChartPanel();
                        chartPanel.setSize(700, 350);
                        chartPanel.setTitle(r.getExpert() + " - " + r.getSymbol());
                        chartPanel.setFromResult(r);
                        // Force layout
                        chartPanel.doLayout();
                        // Render to image
                        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(700, 350, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        java.awt.Graphics2D g2 = img.createGraphics();
                        chartPanel.paint(g2);
                        g2.dispose();

                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        javax.imageio.ImageIO.write(img, "png", baos);
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
}
