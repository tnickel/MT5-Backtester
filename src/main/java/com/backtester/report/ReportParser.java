package com.backtester.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Parses MT5 Strategy Tester HTML reports.
 *
 * MT5 generates HTML reports (not XML), even when the file has .xml extension.
 * The reports can be in different languages (English, German, etc.).
 * Files are typically UTF-16LE encoded.
 *
 * This parser uses regex-based extraction from the HTML table structure:
 *   <td ...>Label:</td>
 *   <td ...><b>Value</b></td>
 */
public class ReportParser {

    private static final Logger log = LoggerFactory.getLogger(ReportParser.class);

    // Multilingual label mappings: German → field name
    // The parser tries both English and German labels.
    private static final Map<String, String> LABEL_MAP = new LinkedHashMap<>();

    static {
        // Net Profit
        LABEL_MAP.put("Nettogewinn gesamt", "netProfit");
        LABEL_MAP.put("Total Net Profit", "netProfit");
        LABEL_MAP.put("Total net profit", "netProfit");

        // Gross Profit
        LABEL_MAP.put("Bruttogewinn", "grossProfit");
        LABEL_MAP.put("Gross Profit", "grossProfit");
        LABEL_MAP.put("Gross profit", "grossProfit");

        // Gross Loss
        LABEL_MAP.put("Bruttoverlust", "grossLoss");
        LABEL_MAP.put("Gross Loss", "grossLoss");
        LABEL_MAP.put("Gross loss", "grossLoss");

        // Profit Factor
        LABEL_MAP.put("Profitfaktor", "profitFactor");
        LABEL_MAP.put("Profit Factor", "profitFactor");
        LABEL_MAP.put("Profit factor", "profitFactor");

        // Expected Payoff
        LABEL_MAP.put("Erwartetes Ergebnis", "expectedPayoff");
        LABEL_MAP.put("Expected Payoff", "expectedPayoff");
        LABEL_MAP.put("Expected payoff", "expectedPayoff");

        // Recovery Factor
        LABEL_MAP.put("Erholungsfaktor", "recoveryFactor");
        LABEL_MAP.put("Recovery Factor", "recoveryFactor");
        LABEL_MAP.put("Recovery factor", "recoveryFactor");

        // Sharpe Ratio
        LABEL_MAP.put("Sharpe-Ratio", "sharpeRatio");
        LABEL_MAP.put("Sharpe Ratio", "sharpeRatio");

        // Total Trades
        LABEL_MAP.put("Gesamtanzahl Trades", "totalTrades");
        LABEL_MAP.put("Total Trades", "totalTrades");
        LABEL_MAP.put("Total trades", "totalTrades");

        // Won Trades
        LABEL_MAP.put("Gewonnene Trades", "wonTrades"); // value: "241 (77.00%)"
        LABEL_MAP.put("Profit Trades", "wonTrades");
        LABEL_MAP.put("Won trades", "wonTrades");
        LABEL_MAP.put("Short Trades Won", "wonTrades");

        // Lost Trades
        LABEL_MAP.put("Verlorene Trades", "lostTrades"); // value: "72 (23.00%)"
        LABEL_MAP.put("Loss Trades", "lostTrades");
        LABEL_MAP.put("Lost trades", "lostTrades");
        LABEL_MAP.put("Loss trades", "lostTrades");

        // Initial Deposit
        LABEL_MAP.put("Ersteinlage", "initialDeposit");
        LABEL_MAP.put("Initial Deposit", "initialDeposit");
        LABEL_MAP.put("Initial deposit", "initialDeposit");

        // Max Drawdown (equity)
        LABEL_MAP.put("Rückgang Equity maximal", "maxDrawdown");         // "978.80 (9.76%)"
        LABEL_MAP.put("R\u00FCckgang Equity maximal", "maxDrawdown");
        LABEL_MAP.put("Maximal Equity Drawdown", "maxDrawdown");
        LABEL_MAP.put("Equity Drawdown Maximal", "maxDrawdown");
        LABEL_MAP.put("Maximal equity drawdown", "maxDrawdown");

        // Max Drawdown (balance)
        LABEL_MAP.put("Rückgang Kontostand maximal", "maxBalanceDrawdown");
        LABEL_MAP.put("R\u00FCckgang Kontostand maximal", "maxBalanceDrawdown");
        LABEL_MAP.put("Balance Drawdown Maximal", "maxBalanceDrawdown");
        LABEL_MAP.put("Maximal balance drawdown", "maxBalanceDrawdown");

        // Short/Buy positions
        LABEL_MAP.put("Sell-Positionen", "shortPositions");    // "169 (75.15%)"
        LABEL_MAP.put("Short Positions", "shortPositions");
        LABEL_MAP.put("Short positions", "shortPositions");
        LABEL_MAP.put("Buy-Positionen", "longPositions");      // "144 (79.17%)"
        LABEL_MAP.put("Long Positions", "longPositions");
        LABEL_MAP.put("Long positions", "longPositions");

        // Largest win/loss
        LABEL_MAP.put("Größter Gewinntrade", "largestWin");
        LABEL_MAP.put("Gr\u00F6\u00DFter Gewinntrade", "largestWin");
        LABEL_MAP.put("Largest profit trade", "largestWin");
        LABEL_MAP.put("Größter Verlusttrade", "largestLoss");
        LABEL_MAP.put("Gr\u00F6\u00DFter Verlusttrade", "largestLoss");
        LABEL_MAP.put("Largest loss trade", "largestLoss");

        // Average win/loss
        LABEL_MAP.put("Durchschnitt Gewinntrade", "avgWin");
        LABEL_MAP.put("Average profit trade", "avgWin");
        LABEL_MAP.put("Durchschnitt Verlusttrade", "avgLoss");
        LABEL_MAP.put("Average loss trade", "avgLoss");
    }

    /**
     * Parse an MT5 Strategy Tester HTML report.
     * Handles UTF-16LE and UTF-8 encoding automatically.
     */
    public BacktestResult parse(Path reportFile) {
        log.info("Parsing report: {}", reportFile);
        BacktestResult result = new BacktestResult();

        try {
            String content = readReportFile(reportFile);

            if (content == null || content.isEmpty()) {
                log.error("Report file is empty");
                return result;
            }

            // Strip all HTML tags to get clean label-value pairs
            // But first, extract values from the structured HTML
            Map<String, String> extractedValues = extractValuesFromHtml(content);

            log.info("Extracted {} values from report", extractedValues.size());
            for (Map.Entry<String, String> entry : extractedValues.entrySet()) {
                log.debug("  {} = {}", entry.getKey(), entry.getValue());
            }

            // Map extracted values to BacktestResult fields
            result.setInitialDeposit(parseNumber(extractedValues.getOrDefault("initialDeposit", "0")));
            result.setTotalProfit(parseNumber(extractedValues.getOrDefault("netProfit", "0")));
            result.setGrossProfit(parseNumber(extractedValues.getOrDefault("grossProfit", "0")));
            result.setGrossLoss(parseNumber(extractedValues.getOrDefault("grossLoss", "0")));
            result.setProfitFactor(parseNumber(extractedValues.getOrDefault("profitFactor", "0")));
            result.setExpectedPayoff(parseNumber(extractedValues.getOrDefault("expectedPayoff", "0")));
            result.setRecoveryFactor(parseNumber(extractedValues.getOrDefault("recoveryFactor", "0")));
            result.setSharpeRatio(parseNumber(extractedValues.getOrDefault("sharpeRatio", "0")));
            result.setTotalTrades((int) parseNumber(extractedValues.getOrDefault("totalTrades", "0")));

            // Won/Lost trades: format is "241 (77.00%)"
            String wonStr = extractedValues.getOrDefault("wonTrades", "0");
            result.setProfitTrades(parseFirstNumber(wonStr));
            result.setWinRate(parsePercentageValue(wonStr));

            String lostStr = extractedValues.getOrDefault("lostTrades", "0");
            result.setLossTrades(parseFirstNumber(lostStr));

            // Drawdown: format is "978.80 (9.76%)"
            String ddStr = extractedValues.getOrDefault("maxDrawdown", "0");
            result.setMaxDrawdown(parsePercentageValue(ddStr));
            result.setMaxDrawdownAbsolute(parseNumber(ddStr));

            // If no equity drawdown, try balance drawdown
            if (result.getMaxDrawdown() == 0) {
                String balDd = extractedValues.getOrDefault("maxBalanceDrawdown", "0");
                result.setMaxDrawdown(parsePercentageValue(balDd));
                result.setMaxDrawdownAbsolute(parseNumber(balDd));
            }

            // Short/Long positions: format is "169 (75.15%)"
            result.setShortPositions(parseFirstNumber(
                    extractedValues.getOrDefault("shortPositions", "0")));
            result.setLongPositions(parseFirstNumber(
                    extractedValues.getOrDefault("longPositions", "0")));

            // Largest/Average win/loss
            result.setLargestWin(parseNumber(extractedValues.getOrDefault("largestWin", "0")));
            result.setLargestLoss(parseNumber(extractedValues.getOrDefault("largestLoss", "0")));
            result.setAverageWin(parseNumber(extractedValues.getOrDefault("avgWin", "0")));
            result.setAverageLoss(parseNumber(extractedValues.getOrDefault("avgLoss", "0")));

            // Parse trade history for equity curve
            List<double[]> equityHistory = parseTradeHistory(content, result.getInitialDeposit());
            result.setEquityHistory(equityHistory);

            log.info("Report parsed: Profit={}, Trades={}, WinRate={}%, Drawdown={}%",
                    result.getTotalProfit(), result.getTotalTrades(),
                    result.getWinRate(), result.getMaxDrawdown());

            return result;

        } catch (Exception e) {
            log.error("Failed to parse report: {}", e.getMessage(), e);
            return result;
        }
    }

    /**
     * Read the report file, detecting UTF-16LE or UTF-8 encoding.
     */
    private String readReportFile(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);

        // Check for UTF-16LE BOM (FF FE)
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            log.info("Detected UTF-16LE encoding");
            return new String(bytes, StandardCharsets.UTF_16LE);
        }

        // Check for UTF-16BE BOM (FE FF)
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, StandardCharsets.UTF_16BE);
        }

        // Try UTF-16LE without BOM (common for MT5)
        // Heuristic: if second byte is often 0x00, it's likely UTF-16LE
        int zeroCount = 0;
        for (int i = 1; i < Math.min(bytes.length, 100); i += 2) {
            if (bytes[i] == 0) zeroCount++;
        }
        if (zeroCount > 30) {
            log.info("Detected UTF-16LE encoding (no BOM)");
            return new String(bytes, StandardCharsets.UTF_16LE);
        }

        // Default to UTF-8
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Extract label-value pairs from the MT5 HTML report.
     * Uses the table structure:
     *   <td ...>Label:</td>  (or Label without colon)
     *   <td ...><b>Value</b></td>
     */
    private Map<String, String> extractValuesFromHtml(String html) {
        Map<String, String> values = new HashMap<>();

        // Pattern: <td...>Label:</td> followed by <td...><b>Value</b></td>
        // The value might be on the same line or the next line
        // MT5 format: label in one <td>, value in next <td><b>...</b></td>

        // Strategy: find each known label, then extract the <b>...</b> value after it
        for (Map.Entry<String, String> labelEntry : LABEL_MAP.entrySet()) {
            String label = labelEntry.getKey();
            String fieldName = labelEntry.getValue();

            // Skip if already found (first match wins)
            if (values.containsKey(fieldName)) continue;

            // Find label in HTML (case-insensitive, with or without colon)
            int labelIdx = indexOfIgnoringEncoding(html, label);
            if (labelIdx < 0) continue;

            // Find the next <b>...</b> after the label's </td>
            int afterLabel = html.indexOf("</td>", labelIdx);
            if (afterLabel < 0) continue;

            // Find the next bold value
            int boldStart = html.indexOf("<b>", afterLabel);
            if (boldStart < 0) continue;

            // Make sure it's reasonably close (within 200 chars)
            if (boldStart - afterLabel > 200) continue;

            int boldEnd = html.indexOf("</b>", boldStart);
            if (boldEnd < 0) continue;

            String value = html.substring(boldStart + 3, boldEnd).trim();

            // Clean HTML entities
            value = value.replace("&nbsp;", " ")
                         .replace("&amp;", "&")
                         .replaceAll("<[^>]+>", "")
                         .trim();

            if (!value.isEmpty()) {
                values.put(fieldName, value);
                log.debug("Found: {} ({}) = {}", label, fieldName, value);
            }
        }

        return values;
    }

    /**
     * Find label index, trying various encoding-related variants.
     */
    private int indexOfIgnoringEncoding(String html, String label) {
        // Try exact match
        int idx = html.indexOf(label);
        if (idx >= 0) return idx;

        // Try with colon variations
        idx = html.indexOf(label + ":");
        if (idx >= 0) return idx;

        // Try case-insensitive
        String htmlLower = html.toLowerCase();
        String labelLower = label.toLowerCase();
        idx = htmlLower.indexOf(labelLower);
        if (idx >= 0) return idx;

        // Try with encoding quirks (German umlauts might render differently)
        // ü → u, ö → o, ä → a, ß → ss
        String simplified = label
                .replace("ü", "u").replace("Ü", "U")
                .replace("ö", "o").replace("Ö", "O")
                .replace("ä", "a").replace("Ä", "A")
                .replace("ß", "ss");
        idx = htmlLower.indexOf(simplified.toLowerCase());
        if (idx >= 0) return idx;

        // Try partial match (first 10 chars)
        if (label.length() > 10) {
            String partial = labelLower.substring(0, 10);
            idx = htmlLower.indexOf(partial);
            if (idx >= 0) return idx;
        }

        return -1;
    }

    private List<double[]> parseTradeHistory(String html, double initialDeposit) {
        List<double[]> history = new ArrayList<>();
        double balance = initialDeposit > 0 ? initialDeposit : 10000;
        history.add(new double[]{0, balance, balance});

        // Find the "Trades" section in the report
        int tradesStart = html.indexOf(">Trades<");
        if (tradesStart < 0) tradesStart = html.indexOf(">Deals<");
        if (tradesStart < 0) tradesStart = html.indexOf(">Orders<");
        if (tradesStart < 0) {
            log.info("No trade history section found in report");
            return history;
        }

        String tradesSection = html.substring(tradesStart);
        // Find where the table ends
        int tableEnd = tradesSection.indexOf("</table>");
        if (tableEnd > 0) {
            tradesSection = tradesSection.substring(0, tableEnd);
        }

        Matcher trMatcher = Pattern.compile("<tr bgcolor=\"#[^\"]+\".*?>(.*?)</tr>", Pattern.CASE_INSENSITIVE).matcher(tradesSection);
        int tradeNum = 0;
        while (trMatcher.find()) {
            String rowInner = trMatcher.group(1);
            List<String> tds = new ArrayList<>();
            Matcher tdMatcher = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.CASE_INSENSITIVE).matcher(rowInner);
            while (tdMatcher.find()) {
                tds.add(tdMatcher.group(1).replaceAll("<[^>]*>", "").trim());
            }
            
            // Standard MT5 row has around 13 columns. The balance is usually the 2nd to last column.
            if (tds.size() >= 12) {
                String balStr = tds.get(tds.size() - 2);
                try {
                    double val = parseNumber(balStr);
                    // Filter: must be reasonably close to initial deposit
                    if (val > initialDeposit * 0.1 && val < initialDeposit * 10) {
                        tradeNum++;
                        // For a simple balance curve, we set equity = balance. 
                        // MT5 reports don't include tick-by-tick equity floating loss in this table.
                        history.add(new double[]{tradeNum, val, val});
                    }
                } catch (Exception e) {
                    // Skip invalid rows
                }
            }
        }

        if (history.size() <= 1) {
            log.info("Could not extract equity history from trade data");
        } else {
            log.info("Extracted {} equity data points from trade history", history.size());
        }

        return history;
    }

    /**
     * Parse a number from text like "377.23" or "1 227.68" or "-850.45"
     * Handles thousands separator (space) and negative values.
     */
    private double parseNumber(String text) {
        if (text == null || text.isEmpty()) return 0;

        // Remove everything except digits, dots, minus, commas
        String cleaned = text.replaceAll("[^\\d.,\\-]", "");

        // Handle German number format: 1.234,56 → 1234.56
        if (cleaned.contains(",") && cleaned.indexOf(',') > cleaned.lastIndexOf('.')) {
            cleaned = cleaned.replace(".", "").replace(",", ".");
        }
        // Handle format like "1 227.68" (space as thousands separator already removed)

        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Parse the first integer from text like "241 (77.00%)" → 241
     */
    private int parseFirstNumber(String text) {
        if (text == null || text.isEmpty()) return 0;
        Matcher m = Pattern.compile("(\\d+)").matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Parse percentage from text like "978.80 (9.76%)" → 9.76
     * or "241 (77.00%)" → 77.00
     */
    private double parsePercentageValue(String text) {
        if (text == null || text.isEmpty()) return 0;
        Matcher m = Pattern.compile("([\\d.]+)%").matcher(text);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
