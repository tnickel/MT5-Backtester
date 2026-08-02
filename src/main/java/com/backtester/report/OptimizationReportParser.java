package com.backtester.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses MT5 XML and MT4 HTML optimization reports.
 */
public class OptimizationReportParser {

    private static final Logger log = LoggerFactory.getLogger(OptimizationReportParser.class);

    public void parse(Path xmlFile, OptimizationResult result) throws Exception {
        parseInternal(xmlFile, result, false);
    }

    public void parseForward(Path xmlFile, OptimizationResult result) throws Exception {
        parseInternal(xmlFile, result, true);
    }

    public void parseHtml(Path htmlFile, OptimizationResult result) throws Exception {
        log.info("Parsing MT4 HTML optimization report: {}", htmlFile);
        byte[] bytes = Files.readAllBytes(htmlFile);
        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        if (content.contains("\uFFFD")) {
            content = new String(bytes, java.nio.charset.Charset.forName("windows-1252"));
        }

        // Normalize spaces and newlines to simplify regex matching
        content = content.replaceAll("\\s+", " ");

        // Regex to find table rows and cell elements
        Pattern rowPattern = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.CASE_INSENSITIVE);
        Pattern cellPattern = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.CASE_INSENSITIVE);

        Matcher rowMatcher = rowPattern.matcher(content);
        List<String> columnHeaders = new ArrayList<>();
        boolean headersParsed = false;
        
        List<OptimizationResult.Pass> passes = result.getPasses();

        while (rowMatcher.find()) {
            String rowContent = rowMatcher.group(1);
            Matcher cellMatcher = cellPattern.matcher(rowContent);
            List<String> cellValues = new ArrayList<>();
            while (cellMatcher.find()) {
                String val = cellMatcher.group(1).replaceAll("<[^>]*>", "").trim();
                cellValues.add(val);
            }

            if (cellValues.isEmpty()) continue;

            // Check if this is the header row
            if (!headersParsed) {
                String firstCell = cellValues.get(0).toLowerCase();
                if (firstCell.contains("pass") || firstCell.contains("durchlauf") || firstCell.contains("profit") || firstCell.contains("gewinn")) {
                    columnHeaders.addAll(cellValues);
                    headersParsed = true;
                }
                continue;
            }

            // Parse data row
            if (cellValues.size() < 2) continue; // Not a valid data row
            
            OptimizationResult.Pass pass = new OptimizationResult.Pass();
            String inputsVal = "";
            for (int col = 0; col < Math.min(cellValues.size(), columnHeaders.size()); col++) {
                String header = columnHeaders.get(col).toLowerCase();
                String value = cellValues.get(col);

                try {
                    if (header.contains("pass") || header.contains("durchlauf")) {
                        pass.setPassNumber(Integer.parseInt(value));
                    } else if (header.contains("profit factor") || header.contains("profitfaktor")) {
                        pass.setProfitFactor(parseDouble(value));
                    } else if (header.contains("profit") || header.contains("gewinn")) {
                        pass.setProfit(parseDouble(value));
                    } else if (header.contains("trades")) {
                        pass.setTotalTrades(Integer.parseInt(value));
                    } else if (header.contains("payoff") || header.contains("auszahlungserwartung")) {
                        pass.setExpectedPayoff(parseDouble(value));
                    } else if ((header.contains("drawdown") || header.contains("rückgang")) && header.contains("%")) {
                        pass.setDrawdownPercent(parsePercentage(value));
                    } else if (header.contains("drawdown") || header.contains("rückgang")) {
                        double ddVal = parsePercentage(value);
                        pass.setDrawdown(ddVal);
                        if (pass.getDrawdownPercent() == 0 && value.contains("%")) {
                            pass.setDrawdownPercent(ddVal);
                        }
                    } else if (header.contains("input") || header.contains("parameter") || header.contains("eingabevariablen")) {
                        inputsVal = value;
                    }
                } catch (NumberFormatException e) {
                    // ignore cell parse error
                }
            }

            // Parse the inputs column to extract individual parameters
            if (!inputsVal.isEmpty()) {
                String[] params = inputsVal.split("[,;]");
                for (String p : params) {
                    int eqIdx = p.indexOf('=');
                    if (eqIdx > 0) {
                        String paramName = p.substring(0, eqIdx).trim();
                        String paramVal = p.substring(eqIdx + 1).trim();
                        if (!paramName.isEmpty()) {
                            pass.setParameter(paramName, paramVal);
                            if (!result.getParameterNames().contains(paramName)) {
                                result.getParameterNames().add(paramName);
                            }
                        }
                    }
                }
            }

            // Calculate Recovery Factor if not present in MT4 report
            if (pass.getRecoveryFactor() == 0.0 && pass.getDrawdown() > 0) {
                pass.setRecoveryFactor(pass.getProfit() / pass.getDrawdown());
            }

            passes.add(pass);
        }
    }

    private void parseInternal(Path xmlFile, OptimizationResult result, boolean isForward) throws Exception {
        log.info("Parsing {} optimization report: {}", isForward ? "forward" : "main", xmlFile);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Ignore namespaces to simplify parsing
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlFile.toFile());

        NodeList rowNodes = doc.getElementsByTagName("Row");
        if (rowNodes.getLength() == 0) {
            log.warn("No rows found in optimization XML.");
            return;
        }

        List<String> columnHeaders = new ArrayList<>();
        boolean headersParsed = false;
        
        List<OptimizationResult.Pass> passes = isForward ? result.getForwardPasses() : result.getPasses();

        for (int i = 0; i < rowNodes.getLength(); i++) {
            Element row = (Element) rowNodes.item(i);
            NodeList cells = row.getElementsByTagName("Cell");
            
            // Extract text from cells
            List<String> cellValues = new ArrayList<>();
            for (int j = 0; j < cells.getLength(); j++) {
                Element cell = (Element) cells.item(j);
                NodeList dataList = cell.getElementsByTagName("Data");
                if (dataList.getLength() > 0) {
                    cellValues.add(dataList.item(0).getTextContent().trim());
                } else {
                    cellValues.add("");
                }
            }

            if (cellValues.isEmpty()) continue;

            // The first row with enough columns is the header row
            if (!headersParsed) {
                // If it looks like a header row
                if (cellValues.get(0).equalsIgnoreCase("Pass") || cellValues.get(0).equalsIgnoreCase("Result")) {
                    columnHeaders.addAll(cellValues);
                    
                    // Add parameters to the result if it's the main pass
                    if (!isForward && result.getParameterNames().isEmpty()) {
                        for (int col = 0; col < columnHeaders.size(); col++) {
                            String header = columnHeaders.get(col);
                            // Detect parameters (they usually come after the main metrics)
                            if (!isBuiltinMetric(header)) {
                                result.getParameterNames().add(header);
                            }
                        }
                    }
                    headersParsed = true;
                }
                continue;
            }

            // Parse data row
            OptimizationResult.Pass pass = new OptimizationResult.Pass();
            for (int col = 0; col < Math.min(cellValues.size(), columnHeaders.size()); col++) {
                String header = columnHeaders.get(col);
                String value = cellValues.get(col);
                
                try {
                    if (header.equalsIgnoreCase("Pass")) {
                        pass.setPassNumber(Integer.parseInt(value));
                    } else if (header.equalsIgnoreCase("Result") || header.equalsIgnoreCase("Profit")) {
                        pass.setProfit(parseDouble(value));
                    } else if (header.equalsIgnoreCase("Total Trades") || header.equalsIgnoreCase("Trades")) {
                        pass.setTotalTrades(Integer.parseInt(value));
                    } else if (header.equalsIgnoreCase("Profit Factor")) {
                        pass.setProfitFactor(parseDouble(value));
                    } else if (header.equalsIgnoreCase("Expected Payoff")) {
                        pass.setExpectedPayoff(parseDouble(value));
                    } else if (header.equalsIgnoreCase("Drawdown") || header.equalsIgnoreCase("Drawdown $") || header.equalsIgnoreCase("Equity DD $")) {
                        pass.setDrawdown(parseDouble(value));
                    } else if (header.equalsIgnoreCase("Drawdown %") || header.equalsIgnoreCase("Equity DD %")) {
                        // Sometimes format is "X.XX%"
                        pass.setDrawdownPercent(parseDouble(value.replace("%", "")));
                    } else if (header.equalsIgnoreCase("Recovery Factor")) {
                        pass.setRecoveryFactor(parseDouble(value));
                    } else if (header.equalsIgnoreCase("Sharpe Ratio")) {
                        pass.setSharpeRatio(parseDouble(value));
                    } else if (header.equalsIgnoreCase("Custom")) {
                        pass.setCustomCriterion(parseDouble(value));
                    } else {
                        // It's an EA parameter
                        pass.setParameter(header, value);
                    }
                } catch (NumberFormatException e) {
                    // Ignore parsing errors for individual cells
                }
            }
            passes.add(pass);
        }
    }
    
    private boolean isBuiltinMetric(String header) {
        String h = header.toLowerCase();
        return h.equals("pass") || h.equals("result") || h.equals("profit") || 
               h.equals("total trades") || h.equals("trades") || h.equals("profit factor") || 
               h.equals("expected payoff") || h.equals("drawdown") || h.equals("drawdown $") || 
               h.equals("drawdown %") || h.equals("recovery factor") || h.equals("sharpe ratio") || 
               h.equals("custom") || h.equals("custom criterion") || h.equals("equity dd %") ||
               h.equals("margin level");
    }
    
    private double parseDouble(String val) {
        if (val == null || val.isEmpty()) return 0.0;
        return Double.parseDouble(val.replace(" ", "")); // e.g. "1 000.50"
    }

    private static double parsePercentage(String value) {
        if (value == null || value.trim().isEmpty()) return 0.0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([\\d.]+)\\s*%").matcher(value);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        try {
            String cleaned = value.replaceAll("[^\\d.]", "");
            return cleaned.isEmpty() ? 0.0 : Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
