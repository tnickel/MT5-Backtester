package com.backtester.ui;

import com.backtester.report.BacktestResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.nio.file.*;
import java.nio.file.StandardCopyOption;

/**
 * Modal dialog that displays backtest results in a visually appealing format.
 * Shows:
 *   - Header with EA name, symbol, timeframe
 *   - Key metric cards (Profit, Trades, Win Rate, Drawdown, etc.)
 *   - Equity curve chart
 *   - Detailed statistics panel
 *   - Buttons to open report files
 */
public class ReportViewerDialog extends JDialog {

    private static final Color BG_DARK = new Color(22, 25, 33);
    private static final Color BG_CARD = new Color(32, 36, 48);
    private static final Color ACCENT = new Color(78, 154, 241);
    private static final Color TEXT_PRIMARY = new Color(220, 225, 235);
    private static final Color TEXT_SECONDARY = new Color(140, 148, 165);
    private static final Color GREEN = new Color(80, 210, 120);
    private static final Color RED = new Color(240, 85, 85);
    private static final Color GOLD = new Color(240, 200, 60);

    private final BacktestResult result;
    private final String outputDir;

    public ReportViewerDialog(Window parent, BacktestResult result) {
        super(parent, "Backtest Report \u2014 " + result.getExpert(), ModalityType.APPLICATION_MODAL);
        this.result = result;
        this.outputDir = result.getOutputDirectory();

        setSize(1000, 780);
        setLocationRelativeTo(parent);
        setBackground(BG_DARK);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 0)) {
            private java.awt.Image bgImage;
            {
                try {
                    bgImage = javax.imageio.ImageIO.read(getClass().getResource("/images/brushed_metal.png"));
                } catch (Exception e) {}
            }
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (bgImage != null) {
                    int w = getWidth();
                    int h = getHeight();
                    int tw = bgImage.getWidth(null);
                    int th = bgImage.getHeight(null);
                    for (int x = 0; x < w; x += tw) {
                        for (int y = 0; y < h; y += th) {
                            g.drawImage(bgImage, x, y, null);
                        }
                    }
                    g.setColor(new Color(22, 25, 33, 180));
                    g.fillRect(0, 0, w, h);
                }
            }
        };
        mainPanel.setBackground(BG_DARK);
        mainPanel.setOpaque(false);

        mainPanel.add(createHeader(), BorderLayout.NORTH);
        mainPanel.add(createBody(), BorderLayout.CENTER);
        mainPanel.add(createFooter(), BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(15, 0));
        header.setBackground(new Color(28, 32, 42, 150));
        header.setOpaque(true);
        header.setBorder(new EmptyBorder(18, 25, 18, 25));

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setOpaque(false);

        JLabel titleLabel = new JLabel(result.getExpert());
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subLabel = new JLabel(result.getSymbol() + " | " + result.getPeriod() +
                " | " + (result.isSuccess() ? "\u2713 Completed" : "\u2717 " + result.getMessage()));
        subLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subLabel.setForeground(result.isSuccess() ? GREEN : RED);
        subLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(subLabel);

        JPanel profitPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        profitPanel.setOpaque(false);

        double profit = result.getTotalProfit();
        double initial = result.getInitialDeposit() > 0 ? result.getInitialDeposit() : 10000;
        double profitPct = (profit / initial) * 100;
        
        JLabel profitLabel = new JLabel(String.format("%s%.2f %s (%s%.2f%%)",
                profit >= 0 ? "+" : "", profit, "USD", profit >= 0 ? "+" : "", profitPct));
        profitLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        profitLabel.setForeground(profit >= 0 ? GREEN : RED);
        profitPanel.add(profitLabel);

        header.add(titlePanel, BorderLayout.WEST);
        header.add(profitPanel, BorderLayout.EAST);

        return header;
    }

    private JPanel createBody() {
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(12, 20, 12, 20));

        body.add(createMetricCards(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                createChartPanel(), createDetailsPanel());
        split.setDividerLocation(350);
        split.setResizeWeight(0.6);
        split.setBorder(null);
        split.setOpaque(false);
        split.setDividerSize(4);

        body.add(split, BorderLayout.CENTER);
        return body;
    }

    private JPanel createMetricCards() {
        JPanel cards = new JPanel(new GridLayout(1, 6, 10, 0));
        cards.setOpaque(false);

        double profit = result.getTotalProfit();
        double initial = result.getInitialDeposit() > 0 ? result.getInitialDeposit() : 10000;
        
        cards.add(createCard("Total Profit", String.format("%s (%.2f%%)", formatMoney(profit), (profit/initial)*100),
                profit >= 0 ? GREEN : RED));
        cards.add(createCard("Total Trades", String.format("%,d", result.getTotalTrades()),
                ACCENT));
        cards.add(createCard("Win Rate", String.format("%.1f%%", result.getWinRate()),
                result.getWinRate() >= 50 ? GREEN : GOLD));
                
        String ddText = result.getBalanceDrawdown() > 0 ? 
            String.format("%.2f%%(Eq)|%.2f%%(Bal)", result.getMaxDrawdown(), result.getBalanceDrawdown()) : 
            String.format("%.2f%%", result.getMaxDrawdown());
            
        cards.add(createCard("Drawdown", ddText,
                result.getMaxDrawdown() < 20 ? GREEN : RED));
        cards.add(createCard("Profit Factor", String.format("%.2f", result.getProfitFactor()),
                result.getProfitFactor() >= 1.5 ? GREEN : result.getProfitFactor() >= 1 ? GOLD : RED));
        cards.add(createCard("Sharpe Ratio", String.format("%.2f", result.getSharpeRatio()),
                result.getSharpeRatio() >= 1 ? GREEN : GOLD));

        return cards;
    }

    private JPanel createCard(String label, String value, Color valueColor) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(32, 36, 48, 200));
        card.setOpaque(true);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(50, 55, 68), 1),
                new EmptyBorder(12, 14, 12, 14)));

        JLabel labelLbl = new JLabel(label);
        labelLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        labelLbl.setForeground(TEXT_SECONDARY);
        labelLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel valueLbl = new JLabel(value);
        valueLbl.setFont(new Font("Segoe UI", Font.BOLD, 20));
        valueLbl.setForeground(valueColor);
        valueLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(labelLbl);
        card.add(Box.createVerticalStrut(6));
        card.add(valueLbl);

        return card;
    }

    private JPanel createChartPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(8, 0, 0, 0));

        EquityChartPanel chart = new EquityChartPanel();
        chart.setTitle("Equity Curve \u2014 " + result.getSymbol() + " " + result.getPeriod());
        chart.setFromResult(result);

        panel.add(chart, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createDetailsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(8, 0, 0, 0));

        JPanel leftCol = createDetailColumn(new String[][]{
                {"Initial Deposit", formatMoney(result.getInitialDeposit())},
                {"Total Net Profit", formatMoney(result.getTotalProfit())},
                {"Gross Profit", formatMoney(result.getGrossProfit())},
                {"Gross Loss", formatMoney(result.getGrossLoss())},
                {"Profit Factor", String.format("%.2f", result.getProfitFactor())},
                {"Expected Payoff", formatMoney(result.getExpectedPayoff())},
                {"Recovery Factor", String.format("%.2f", result.getRecoveryFactor())},
        });

        String ddDetailed = result.getBalanceDrawdown() > 0 ?
            String.format("%.2f%% ($%.2f) / Bal %.2f%% ($%.2f)", 
                result.getMaxDrawdown(), result.getMaxDrawdownAbsolute(),
                result.getBalanceDrawdown(), result.getBalanceDrawdownAbsolute()) :
            String.format("%.2f%% ($%.2f)", result.getMaxDrawdown(), result.getMaxDrawdownAbsolute());

        JPanel rightCol = createDetailColumn(new String[][]{
                {"Total Trades", String.format("%,d", result.getTotalTrades())},
                {"Profit Trades", String.format("%,d", result.getProfitTrades())},
                {"Loss Trades", String.format("%,d", result.getLossTrades())},
                {"Win Rate", String.format("%.1f%%", result.getWinRate())},
                {"Max Drawdown", ddDetailed},
                {"Sharpe Ratio", String.format("%.2f", result.getSharpeRatio())},
                {"Short / Long", result.getShortPositions() + " / " + result.getLongPositions()},
        });

        JPanel columns = new JPanel(new GridLayout(1, 2, 15, 0));
        columns.setOpaque(false);
        columns.add(leftCol);
        columns.add(rightCol);

        JLabel detailTitle = new JLabel("Detailed Statistics");
        detailTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        detailTitle.setForeground(ACCENT);
        detailTitle.setBorder(new EmptyBorder(0, 0, 6, 0));

        panel.add(detailTitle, BorderLayout.NORTH);
        panel.add(columns, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createDetailColumn(String[][] rows) {
        JPanel col = new JPanel(new GridLayout(rows.length, 1, 0, 2));
        col.setBackground(new Color(32, 36, 48, 200));
        col.setOpaque(true);
        col.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(50, 55, 68), 1),
                new EmptyBorder(8, 12, 8, 12)));

        for (String[] row : rows) {
            JPanel rowPanel = new JPanel(new BorderLayout());
            rowPanel.setOpaque(false);

            JLabel key = new JLabel(row[0]);
            key.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            key.setForeground(TEXT_SECONDARY);

            JLabel val = new JLabel(row[1]);
            val.setFont(new Font("Segoe UI", Font.BOLD, 12));

            if (row[1].startsWith("+") || (row[1].startsWith("$") && !row[1].contains("-"))) {
                val.setForeground(GREEN);
            } else if (row[1].startsWith("-") || row[1].startsWith("$-")) {
                val.setForeground(RED);
            } else {
                val.setForeground(TEXT_PRIMARY);
            }

            rowPanel.add(key, BorderLayout.WEST);
            rowPanel.add(val, BorderLayout.EAST);
            col.add(rowPanel);
        }

        return col;
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(new Color(28, 32, 42, 150));
        footer.setOpaque(true);
        footer.setBorder(new EmptyBorder(10, 20, 10, 20));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttons.setOpaque(false);

        JButton openFolderBtn = new JButton("\uD83D\uDCC1 Open Report Folder");
        openFolderBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        openFolderBtn.addActionListener(e -> openFolder());
        buttons.add(openFolderBtn);

        JButton openReportBtn = new JButton("\uD83C\uDF10 Open in Browser");
        openReportBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        openReportBtn.addActionListener(e -> openReport("report.htm"));
        buttons.add(openReportBtn);

        JButton exportCsvBtn = new JButton("\uD83D\uDCCA Export Trades (CSV)");
        exportCsvBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        exportCsvBtn.addActionListener(e -> exportTradesToCsv());
        buttons.add(exportCsvBtn);

        JButton closeBtn = new JButton("Close");
        closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        closeBtn.setPreferredSize(new Dimension(100, 32));
        closeBtn.addActionListener(e -> dispose());

        footer.add(buttons, BorderLayout.WEST);
        footer.add(closeBtn, BorderLayout.EAST);

        JLabel pathLabel = new JLabel(outputDir != null ? outputDir : "");
        pathLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        pathLabel.setForeground(TEXT_SECONDARY);
        footer.add(pathLabel, BorderLayout.SOUTH);

        return footer;
    }

    private void openFolder() {
        if (outputDir != null) {
            try {
                Desktop.getDesktop().open(new File(outputDir));
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                        "Could not open folder:\n" + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void openReport(String filename) {
        if (outputDir == null) return;
        Path reportPath = Paths.get(outputDir).resolve(filename);
        
        // Fallback: try old .xml format if .htm not found
        if (!Files.exists(reportPath) && filename.endsWith(".htm")) {
            Path xmlFallback = Paths.get(outputDir).resolve("report.xml");
            if (Files.exists(xmlFallback)) {
                reportPath = xmlFallback;
            }
        }
        
        if (Files.exists(reportPath)) {
            try {
                // Ensure file has .htm or .html extension for browser rendering
                String name = reportPath.getFileName().toString();
                if (name.endsWith(".xml")) {
                    Path htmlPath = Paths.get(outputDir).resolve(name.replace(".xml", ".html"));
                    if (!Files.exists(htmlPath) || Files.getLastModifiedTime(htmlPath).compareTo(
                            Files.getLastModifiedTime(reportPath)) < 0) {
                        Files.copy(reportPath, htmlPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    Desktop.getDesktop().browse(htmlPath.toUri());
                } else {
                    Desktop.getDesktop().browse(reportPath.toUri());
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                        "Could not open report:\n" + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(this,
                    "Report file not found:\n" + reportPath,
                    "File Not Found", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void exportTradesToCsv() {
        if (outputDir == null) return;
        Path reportPath = Paths.get(outputDir).resolve("report.htm");
        if (!Files.exists(reportPath)) {
            reportPath = Paths.get(outputDir).resolve("report.xml");
            if (!Files.exists(reportPath)) {
                JOptionPane.showMessageDialog(this, "Original report file not found, cannot extract trades.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        JFileChooser chooser = new JFileChooser(outputDir);
        chooser.setDialogTitle("Export Trades as CSV");
        chooser.setSelectedFile(new File(result.getExpert() + "_Trades.csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File outFile = chooser.getSelectedFile();
            try {
                // Determine encoding and read file
                byte[] bytes = Files.readAllBytes(reportPath);
                String html;
                if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
                    html = new String(bytes, java.nio.charset.StandardCharsets.UTF_16LE);
                } else if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
                    html = new String(bytes, java.nio.charset.StandardCharsets.UTF_16BE);
                } else {
                    int zeroCount = 0;
                    for (int i = 1; i < Math.min(bytes.length, 100); i += 2) {
                        if (bytes[i] == 0) zeroCount++;
                    }
                    if (zeroCount > 30) {
                        html = new String(bytes, java.nio.charset.StandardCharsets.UTF_16LE);
                    } else {
                        html = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    }
                }

                // Locate the Trades/Deals section
                int tradesStart = html.indexOf(">Trades<");
                if (tradesStart < 0) tradesStart = html.indexOf(">Deals<");
                if (tradesStart < 0) tradesStart = html.indexOf(">Orders<");
                if (tradesStart < 0) tradesStart = html.indexOf(">Geschäfte<");

                if (tradesStart < 0) {
                    JOptionPane.showMessageDialog(this, "Could not find Trades/Deals table in the report HTML.", "Warning", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                String section = html.substring(tradesStart);
                int tableEnd = section.indexOf("</table>");
                if (tableEnd > 0) {
                    section = section.substring(0, tableEnd);
                }

                java.util.regex.Matcher trMatcher = java.util.regex.Pattern.compile("<tr[^>]*>(.*?)</tr>", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL).matcher(section);
                
                try (java.io.BufferedWriter bw = Files.newBufferedWriter(outFile.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                    // Optional BOM for Excel UTF-8
                    bw.write("\uFEFF");
                    
                    int extractedRecords = 0;
                    while (trMatcher.find()) {
                        String rowInner = trMatcher.group(1);
                        java.util.List<String> cols = new java.util.ArrayList<>();
                        
                        java.util.regex.Matcher tdMatcher = java.util.regex.Pattern.compile("<t[dh][^>]*>(.*?)</t[dh]>", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL).matcher(rowInner);
                        while (tdMatcher.find()) {
                            String cell = tdMatcher.group(1).replaceAll("<[^>]*>", "").trim();
                            // Decode basic HTML entities that MT5 might use
                            cell = cell.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
                            // Escape quotes
                            cell = cell.replace("\"", "\"\"");
                            // Quote to handle commas inside text (like numbers e.g. 1,000.00)
                            cols.add("\"" + cell + "\"");
                        }
                        
                        if (!cols.isEmpty()) {
                            bw.write(String.join(",", cols));
                            bw.newLine();
                            extractedRecords++;
                        }
                    }
                    
                    if (extractedRecords > 0) {
                        JOptionPane.showMessageDialog(this, "Exported " + extractedRecords + " rows to:\n" + outFile.getAbsolutePath(), "Export Successful", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, "Found the table but failed to extract any data rows.", "Warning", JOptionPane.WARNING_MESSAGE);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Failed to export CSV: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private String formatMoney(double value) {
        if (value == 0) return "$0.00";
        String sign = value >= 0 ? "+" : "";
        return String.format("%s$%.2f", sign, value);
    }

    /**
     * Static factory method to show the dialog for a result loaded from a report directory.
     * Uses the ReportParser (which handles MT5's UTF-16LE HTML format) as primary data source,
     * and falls back to summary.txt for metadata.
     */
    public static void showForDirectory(Window parent, String directory) {
        try {
            UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatDarkLaf());
        } catch (Exception e) {}
        
        Path dir = Paths.get(directory);
        BacktestResult result = new BacktestResult();
        result.setOutputDirectory(directory);

        // 1. Try parsing the actual MT5 report (.htm file, or legacy .xml)
        Path reportHtm = dir.resolve("report.htm");
        Path reportXml = dir.resolve("report.xml");
        Path reportToParse = Files.exists(reportHtm) ? reportHtm : (Files.exists(reportXml) ? reportXml : null);
        if (reportToParse != null) {
            try {
                com.backtester.report.ReportParser parser = new com.backtester.report.ReportParser();
                result = parser.parse(reportToParse);
                result.setOutputDirectory(directory);
                result.setSuccess(true);
            } catch (Exception e) {
                // Parsing failed, continue with summary
            }
        }

        // 2. Read summary.txt for metadata (expert, symbol, period) and as fallback
        Path summaryFile = dir.resolve("summary.txt");
        if (Files.exists(summaryFile)) {
            try {
                String content = Files.readString(summaryFile);
                String expert = extractVal(content, "Expert:");
                String symbol = extractVal(content, "Symbol:");
                String period = extractVal(content, "Period:");
                if (!expert.isEmpty()) result.setExpert(expert);
                if (!symbol.isEmpty()) result.setSymbol(symbol);
                if (!period.isEmpty()) result.setPeriod(period);

                if (result.getTotalProfit() == 0)
                    result.setTotalProfit(parseDouble(extractVal(content, "Total Profit:")));
                if (result.getTotalTrades() == 0)
                    result.setTotalTrades(parseInt(extractVal(content, "Total Trades:")));
                if (result.getWinRate() == 0)
                    result.setWinRate(parseDouble(extractVal(content, "Win Rate:").replace("%", "")));
                if (result.getMaxDrawdown() == 0)
                    result.setMaxDrawdown(parseDouble(extractVal(content, "Max Drawdown:").replace("%", "")));
                if (result.getProfitFactor() == 0)
                    result.setProfitFactor(parseDouble(extractVal(content, "Profit Factor:")));
                if (result.getSharpeRatio() == 0)
                    result.setSharpeRatio(parseDouble(extractVal(content, "Sharpe Ratio:")));

                String status = extractVal(content, "Status:");
                if (status.contains("SUCCESS")) result.setSuccess(true);
            } catch (Exception e) {
                // summary parsing failed
            }
        }

        if (result.getExpert().isEmpty()) {
            result.setExpert(dir.getFileName().toString());
        }

        ReportViewerDialog dialog = new ReportViewerDialog(parent, result);
        dialog.setVisible(true);
    }

    private static String extractVal(String content, String key) {
        for (String line : content.split("\n")) {
            if (line.trim().startsWith(key)) {
                return line.substring(line.indexOf(':') + 1).trim();
            }
        }
        return "";
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s.replaceAll("[^\\d.\\-]", "")); }
        catch (Exception e) { return 0; }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.replaceAll("[^\\d]", "")); }
        catch (Exception e) { return 0; }
    }
}
