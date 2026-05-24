package com.backtester.report;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class BacktestResultTest {

    @Test
    public void testDefaultValues() {
        BacktestResult result = new BacktestResult();
        assertEquals("", result.getExpert());
        assertEquals("", result.getSymbol());
        assertEquals("Default", result.getConfigInfo());
        assertTrue(result.isUsedDefaultConfig());
        assertFalse(result.isSuccess());
        assertEquals(0, result.getEquityHistory().size());
    }

    @Test
    public void testFinancialMetricsSetters() {
        BacktestResult result = new BacktestResult();
        result.setTotalProfit(1050.50);
        result.setGrossProfit(2000.00);
        result.setGrossLoss(-949.50);
        
        assertEquals(1050.50, result.getTotalProfit(), 0.001);
        assertEquals(2000.00, result.getGrossProfit(), 0.001);
        assertEquals(-949.50, result.getGrossLoss(), 0.001);
    }

    @Test
    public void testEquityHistory() {
        BacktestResult result = new BacktestResult();
        List<double[]> history = new ArrayList<>();
        history.add(new double[]{1, 10000, 10000});
        history.add(new double[]{2, 10500, 10400});
        
        result.setEquityHistory(history);
        assertEquals(2, result.getEquityHistory().size());
        assertEquals(10500, result.getEquityHistory().get(1)[1], 0.001);
    }

    @Test
    public void testSuccessAndMessage() {
        BacktestResult result = new BacktestResult();
        result.setSuccess(true);
        result.setMessage("Test completed");
        
        assertTrue(result.isSuccess());
        assertEquals("Test completed", result.getMessage());
    }

    @Test
    public void testConfigInfo() {
        BacktestResult result = new BacktestResult();
        result.setUsedDefaultConfig(false);
        result.setConfigInfo("Custom (2 modified)");
        
        assertFalse(result.isUsedDefaultConfig());
        assertEquals("Custom (2 modified)", result.getConfigInfo());
    }

    @Test
    public void testTradeMetrics() {
        BacktestResult result = new BacktestResult();
        result.setTotalTrades(100);
        result.setProfitTrades(60);
        result.setLossTrades(40);
        result.setWinRate(60.0);
        
        assertEquals(100, result.getTotalTrades());
        assertEquals(60, result.getProfitTrades());
        assertEquals(40, result.getLossTrades());
        assertEquals(60.0, result.getWinRate(), 0.001);
    }

    @Test
    public void testToStringFormat() {
        BacktestResult result = new BacktestResult();
        result.setExpert("MyEA");
        result.setSymbol("EURUSD");
        result.setPeriod("H1");
        result.setTotalProfit(150.0);
        
        String str = result.toString();
        assertTrue(str.contains("MyEA"));
        assertTrue(str.contains("EURUSD"));
        assertTrue(str.contains(String.format("%.2f", 150.0)));
    }
}
