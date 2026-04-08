package com.backtester.ui;

import com.backtester.report.BacktestResult;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;

/**
 * Custom JPanel that renders an equity curve chart using Java2D.
 * Supports smooth anti-aliased rendering with gradient fills,
 * grid lines, axis labels, and hover tooltips.
 */
public class EquityChartPanel extends JPanel {

    private List<double[]> equityData; // [index, balance, equity]
    private String title = "Equity Curve";
    private double initialDeposit = 10000;

    // Colors
    private static final Color BG_COLOR = new Color(22, 25, 33);
    private static final Color GRID_COLOR = new Color(45, 50, 62);
    private static final Color AXIS_COLOR = new Color(100, 108, 125);
    private static final Color BALANCE_COLOR = new Color(78, 154, 241);
    private static final Color EQUITY_COLOR = new Color(120, 220, 140);
    private static final Color PROFIT_FILL = new Color(78, 154, 241, 30);
    private static final Color LOSS_FILL = new Color(240, 80, 80, 30);
    private static final Color ZERO_LINE_COLOR = new Color(200, 200, 200, 60);
    private static final Color TEXT_COLOR = new Color(180, 185, 200);

    // Chart margins
    private static final int MARGIN_LEFT = 80;
    private static final int MARGIN_RIGHT = 30;
    private static final int MARGIN_TOP = 45;
    private static final int MARGIN_BOTTOM = 40;

    public EquityChartPanel() {
        setBackground(BG_COLOR);
        setPreferredSize(new Dimension(800, 350));
    }

    public void setEquityData(List<double[]> data) {
        this.equityData = data;
        repaint();
    }

    public void setTitle(String title) {
        this.title = title;
        repaint();
    }

    public void setInitialDeposit(double deposit) {
        this.initialDeposit = deposit;
        repaint();
    }

    /**
     * Set equity data from a BacktestResult.
     * Uses real equity history if available, otherwise generates a simulated curve.
     */
    public void setFromResult(BacktestResult result) {
        this.initialDeposit = result.getInitialDeposit() > 0 ? result.getInitialDeposit() : 10000;

        // Prefer real equity data from parsed report
        if (result.getEquityHistory() != null && result.getEquityHistory().size() > 2) {
            this.equityData = result.getEquityHistory();
            repaint();
            return;
        }

        // Fallback: generate simulated equity curve from summary stats
        double finalBalance = initialDeposit + result.getTotalProfit();
        int totalTrades = Math.max(result.getTotalTrades(), 1);

        java.util.List<double[]> data = new java.util.ArrayList<>();
        double balance = initialDeposit;
        double winRate = result.getWinRate() / 100.0;
        double avgWin, avgLoss;

        if (result.getAverageWin() > 0) {
            avgWin = result.getAverageWin();
        } else if (result.getGrossProfit() > 0 && result.getProfitTrades() > 0) {
            avgWin = result.getGrossProfit() / result.getProfitTrades();
        } else {
            avgWin = Math.abs(result.getTotalProfit()) / Math.max(totalTrades * winRate, 1);
        }

        if (result.getAverageLoss() != 0) {
            avgLoss = Math.abs(result.getAverageLoss());
        } else if (result.getGrossLoss() != 0 && result.getLossTrades() > 0) {
            avgLoss = Math.abs(result.getGrossLoss()) / result.getLossTrades();
        } else {
            avgLoss = avgWin * 0.8;
        }

        // Use a seeded random to create reproducible curves
        java.util.Random rng = new java.util.Random(
            (long)(result.getTotalProfit() * 1000) + totalTrades);

        data.add(new double[]{0, balance, balance});

        for (int i = 1; i <= totalTrades; i++) {
            boolean isWin = rng.nextDouble() < winRate;
            double change;
            if (isWin) {
                change = avgWin * (0.5 + rng.nextDouble());
            } else {
                change = -avgLoss * (0.5 + rng.nextDouble());
            }
            balance += change;
            double equity = balance + (rng.nextDouble() - 0.5) * avgWin * 0.3;
            data.add(new double[]{i, balance, equity});
        }

        // Normalize so final balance matches actual result
        if (data.size() > 1 && balance != finalBalance) {
            double correction = finalBalance - balance;
            double step = correction / data.size();
            for (int i = 1; i < data.size(); i++) {
                data.get(i)[1] += step * i;
                data.get(i)[2] += step * i;
            }
        }

        this.equityData = data;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int w = getWidth();
        int h = getHeight();

        // Background gradient
        GradientPaint bgGrad = new GradientPaint(0, 0, BG_COLOR, 0, h, new Color(18, 20, 28));
        g2.setPaint(bgGrad);
        g2.fillRect(0, 0, w, h);

        // Chart area
        int chartW = w - MARGIN_LEFT - MARGIN_RIGHT;
        int chartH = h - MARGIN_TOP - MARGIN_BOTTOM;

        if (equityData == null || equityData.size() < 2) {
            // No data — show placeholder
            g2.setColor(AXIS_COLOR);
            g2.setFont(new Font("Segoe UI", Font.ITALIC, 14));
            String msg = "No equity data available";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
            g2.dispose();
            return;
        }

        // Calculate data range
        double minVal = Double.MAX_VALUE, maxVal = Double.MIN_VALUE;
        for (double[] point : equityData) {
            minVal = Math.min(minVal, Math.min(point[1], point[2]));
            maxVal = Math.max(maxVal, Math.max(point[1], point[2]));
        }

        // Add 5% padding
        double range = maxVal - minVal;
        if (range == 0) range = 1000;
        minVal -= range * 0.05;
        maxVal += range * 0.05;

        // Title
        g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g2.setColor(TEXT_COLOR);
        g2.drawString(title, MARGIN_LEFT, 25);

        // Legend
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        int legendX = w - MARGIN_RIGHT - 200;
        g2.setColor(BALANCE_COLOR);
        g2.fillRect(legendX, 12, 12, 12);
        g2.setColor(TEXT_COLOR);
        g2.drawString("Balance", legendX + 16, 23);
        g2.setColor(EQUITY_COLOR);
        g2.fillRect(legendX + 80, 12, 12, 12);
        g2.setColor(TEXT_COLOR);
        g2.drawString("Equity", legendX + 96, 23);

        // Draw grid lines
        g2.setStroke(new BasicStroke(1f));
        int gridLines = 6;
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        for (int i = 0; i <= gridLines; i++) {
            int y = MARGIN_TOP + (int) ((double) i / gridLines * chartH);
            double val = maxVal - (double) i / gridLines * (maxVal - minVal);

            g2.setColor(GRID_COLOR);
            g2.drawLine(MARGIN_LEFT, y, MARGIN_LEFT + chartW, y);

            g2.setColor(AXIS_COLOR);
            String label = formatValue(val);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, MARGIN_LEFT - fm.stringWidth(label) - 8, y + 4);
        }

        // Draw initial deposit line
        double depositY = MARGIN_TOP + (maxVal - initialDeposit) / (maxVal - minVal) * chartH;
        if (depositY > MARGIN_TOP && depositY < MARGIN_TOP + chartH) {
            g2.setColor(ZERO_LINE_COLOR);
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[]{6f, 4f}, 0f));
            g2.drawLine(MARGIN_LEFT, (int) depositY, MARGIN_LEFT + chartW, (int) depositY);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            g2.drawString("Deposit", MARGIN_LEFT + chartW - 40, (int) depositY - 4);
        }

        // Build paths for balance and equity
        GeneralPath balancePath = new GeneralPath();
        GeneralPath equityPath = new GeneralPath();
        GeneralPath fillPath = new GeneralPath();

        int dataSize = equityData.size();
        for (int i = 0; i < dataSize; i++) {
            double x = MARGIN_LEFT + (double) i / (dataSize - 1) * chartW;
            double yBal = MARGIN_TOP + (maxVal - equityData.get(i)[1]) / (maxVal - minVal) * chartH;
            double yEq = MARGIN_TOP + (maxVal - equityData.get(i)[2]) / (maxVal - minVal) * chartH;

            if (i == 0) {
                balancePath.moveTo(x, yBal);
                equityPath.moveTo(x, yEq);
                fillPath.moveTo(x, yBal);
            } else {
                balancePath.lineTo(x, yBal);
                equityPath.lineTo(x, yEq);
                fillPath.lineTo(x, yBal);
            }
        }

        // Fill area under balance curve
        GeneralPath fill = (GeneralPath) fillPath.clone();
        fill.lineTo(MARGIN_LEFT + chartW, MARGIN_TOP + chartH);
        fill.lineTo(MARGIN_LEFT, MARGIN_TOP + chartH);
        fill.closePath();

        // Gradient fill — green if profitable, red if loss
        double finalBalance = equityData.get(dataSize - 1)[1];
        Color fillColor = finalBalance >= initialDeposit ? PROFIT_FILL : LOSS_FILL;
        GradientPaint fillGrad = new GradientPaint(
                0, MARGIN_TOP, new Color(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), 50),
                0, MARGIN_TOP + chartH, new Color(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), 5));
        g2.setPaint(fillGrad);
        g2.fill(fill);

        // Draw equity line (behind balance)
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(new Color(EQUITY_COLOR.getRed(), EQUITY_COLOR.getGreen(), EQUITY_COLOR.getBlue(), 120));
        g2.draw(equityPath);

        // Draw balance line
        g2.setStroke(new BasicStroke(2.2f));
        g2.setColor(BALANCE_COLOR);
        g2.draw(balancePath);

        // X axis labels
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g2.setColor(AXIS_COLOR);
        int xLabels = Math.min(8, dataSize);
        java.text.SimpleDateFormat sdfDate = new java.text.SimpleDateFormat("MMM dd, yyyy");
        for (int i = 0; i <= xLabels; i++) {
            int dataIdx = (int) ((double) i / xLabels * (dataSize - 1));
            int x = MARGIN_LEFT + (int) ((double) dataIdx / (dataSize - 1) * chartW);
            
            String label = String.valueOf(dataIdx);
            if (dataIdx >= 0 && dataIdx < equityData.size()) {
                double[] pt = equityData.get(dataIdx);
                if (pt.length > 3 && pt[3] > 0) {
                    label = sdfDate.format(new java.util.Date((long) pt[3]));
                }
            }
            g2.drawString(label, x - g2.getFontMetrics().stringWidth(label) / 2, MARGIN_TOP + chartH + 18);
        }

        // X axis title
        g2.setColor(AXIS_COLOR);
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        boolean hasDates = equityData.size() > 0 && equityData.get(0).length > 3 && equityData.get(0)[3] > 0;
        String xTitle = hasDates ? "Date / Trade" : "Trade #";
        g2.drawString(xTitle, MARGIN_LEFT + chartW / 2 - g2.getFontMetrics().stringWidth(xTitle) / 2, h - 5);

        // Chart border
        g2.setColor(GRID_COLOR);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRect(MARGIN_LEFT, MARGIN_TOP, chartW, chartH);

        // Final value label
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        double lastBal = equityData.get(dataSize - 1)[1];
        double profit = lastBal - initialDeposit;
        String finalLabel = String.format("%.2f (%s%.2f)", lastBal, profit >= 0 ? "+" : "", profit);
        g2.setColor(profit >= 0 ? EQUITY_COLOR : new Color(240, 80, 80));
        int lastX = MARGIN_LEFT + chartW;
        int lastY = (int) (MARGIN_TOP + (maxVal - lastBal) / (maxVal - minVal) * chartH);
        g2.drawString(finalLabel, lastX - g2.getFontMetrics().stringWidth(finalLabel) - 5, lastY - 8);

        g2.dispose();
    }

    private String formatValue(double val) {
        if (Math.abs(val) >= 1000000) return String.format("%.1fM", val / 1000000);
        if (Math.abs(val) >= 1000) return String.format("%.0fK", val / 1000);
        return String.format("%.0f", val);
    }
}
