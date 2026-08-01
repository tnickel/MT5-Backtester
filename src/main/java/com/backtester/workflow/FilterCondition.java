package com.backtester.workflow;

import com.backtester.report.OptimizationResult.CombinedPass;

/**
 * Repräsentiert eine benutzerdefinierte Filterbedingung für Strategy Quality Ranking / Filtering.
 */
public class FilterCondition {

    public enum Metric {
        BT_PROFIT_FACTOR("Profit factor (Backtest / IS)"),
        FW_PROFIT_FACTOR("Profit factor (Forward / OOS)"),
        LT_PROFIT_FACTOR("Profit factor (Longterm)"),
        
        BT_NET_PROFIT("Net profit (Backtest / IS)"),
        FW_NET_PROFIT("Net profit (Forward / OOS)"),
        LT_NET_PROFIT("Net profit (Longterm)"),

        BT_TOTAL_TRADES("# of trades (Backtest / IS)"),
        FW_TOTAL_TRADES("# of trades (Forward / OOS)"),
        LT_TOTAL_TRADES("# of trades (Longterm)"),

        BT_MAX_DD_PERCENT("Max drawdown % (Backtest / IS)"),
        FW_MAX_DD_PERCENT("Max drawdown % (Forward / OOS)"),
        LT_MAX_DD_PERCENT("Max drawdown % (Longterm)"),

        BT_RET_DD_RATIO("Return / Drawdown ratio (Backtest / IS)"),
        FW_RET_DD_RATIO("Return / Drawdown ratio (Forward / OOS)"),

        BT_RECOVERY_FACTOR("Recovery factor (Backtest / IS)"),
        FW_RECOVERY_FACTOR("Recovery factor (Forward / OOS)"),
        LT_RECOVERY_FACTOR("Recovery factor (Longterm)"),
        
        BT_SHARPE_RATIO("Sharpe Ratio (Backtest / IS)"),
        FW_SHARPE_RATIO("Sharpe Ratio (Forward / OOS)"),
        LT_SHARPE_RATIO("Sharpe Ratio (Longterm)"),

        PROFIT_FACTOR("Profit factor (Auto fallback)");

        private final String displayName;
        Metric(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
        @Override public String toString() { return displayName; }
    }

    public enum Operator {
        GREATER_THAN(">", "Größer als"),
        GREATER_EQUAL(">=", "Größer oder gleich"),
        LESS_THAN("<", "Kleiner als"),
        LESS_EQUAL("<=", "Kleiner oder gleich"),
        EQUALS("==", "Gleich");

        private final String symbol;
        private final String label;
        Operator(String symbol, String label) {
            this.symbol = symbol;
            this.label = label;
        }
        public String getSymbol() { return symbol; }
        public String getLabel() { return label; }
        @Override public String toString() { return symbol + " (" + label + ")"; }
    }

    private Metric metric;
    private Operator operator;
    private double value;
    private boolean enabled;

    public FilterCondition() {
        this.metric = Metric.BT_PROFIT_FACTOR;
        this.operator = Operator.GREATER_THAN;
        this.value = 1.2;
        this.enabled = true;
    }

    public FilterCondition(Metric metric, Operator operator, double value) {
        this.metric = metric;
        this.operator = operator;
        this.value = value;
        this.enabled = true;
    }

    public Metric getMetric() { return metric; }
    public void setMetric(Metric metric) { this.metric = metric; }

    public Operator getOperator() { return operator; }
    public void setOperator(Operator operator) { this.operator = operator; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public FilterCondition copyForPersistence() {
        FilterCondition copy = new FilterCondition(metric, operator, value);
        copy.setEnabled(enabled);
        return copy;
    }

    public boolean evaluate(CombinedPass pass) {
        if (pass == null) return false;
        if (!enabled) return true;
        if (metric == null || operator == null || !Double.isFinite(value)) return false;

        double metricVal = getPassMetricValue(pass);
        if (!Double.isFinite(metricVal)) return false;

        switch (operator) {
            case GREATER_THAN:  return metricVal > value;
            case GREATER_EQUAL: return metricVal >= value;
            case LESS_THAN:     return metricVal < value;
            case LESS_EQUAL:    return metricVal <= value;
            case EQUALS:
                double scale = Math.max(1.0, Math.max(Math.abs(metricVal), Math.abs(value)));
                return Math.abs(metricVal - value) <= 1e-9 * scale;
            default:            return false;
        }
    }

    private double getPassMetricValue(CombinedPass pass) {
        switch (metric) {
            case BT_PROFIT_FACTOR:
                return pass.getBtPf();
            case FW_PROFIT_FACTOR:
                return pass.getFwPf();
            case LT_PROFIT_FACTOR:
                return pass.getLtPf();

            case BT_NET_PROFIT:
                return pass.getBtProfit();
            case FW_NET_PROFIT:
                return pass.getFwProfit();
            case LT_NET_PROFIT:
                return pass.getLtProfit();

            case BT_TOTAL_TRADES:
                return pass.getBtTrades();
            case FW_TOTAL_TRADES:
                return pass.getFwTrades();
            case LT_TOTAL_TRADES:
                return pass.getLtTrades();

            case BT_MAX_DD_PERCENT:
                return pass.getBtDd();
            case FW_MAX_DD_PERCENT:
                return pass.getFwDd();
            case LT_MAX_DD_PERCENT:
                return pass.getLtDd();

            case BT_RET_DD_RATIO:
                return safeRatio(pass.getBtProfit(), pass.getBtDd());
            case FW_RET_DD_RATIO:
                return safeRatio(pass.getFwProfit(), pass.getFwDd());

            case BT_RECOVERY_FACTOR:
                return pass.getBtRecovery();
            case FW_RECOVERY_FACTOR:
                return pass.getFwRecovery();
            case LT_RECOVERY_FACTOR:
                return pass.getLtRecovery();

            case BT_SHARPE_RATIO:
                return pass.getBtSharpe();
            case FW_SHARPE_RATIO:
                return pass.getFwSharpe();
            case LT_SHARPE_RATIO:
                return pass.getLtSharpe();

            case PROFIT_FACTOR:
            default:
                return pass.getBtPf();
        }
    }

    private static double safeRatio(double numerator, double denominator) {
        if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || denominator <= 0.0) {
            return Double.NaN;
        }
        return numerator / denominator;
    }

    @Override
    public String toString() {
        return (metric != null ? metric.getDisplayName() : "") + " " +
               (operator != null ? operator.getSymbol() : "") + " " + value;
    }
}
