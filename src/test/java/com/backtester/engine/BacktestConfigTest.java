package com.backtester.engine;

import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class BacktestConfigTest {

    @Test
    public void testDefaultValues() {
        BacktestConfig config = new BacktestConfig();
        assertEquals("", config.getExpert());
        assertEquals("EURUSD", config.getSymbol());
        assertEquals("H1", config.getPeriod());
        assertEquals(1, config.getModel());
        assertEquals(0, config.getExecutionMode());
        assertEquals(10000, config.getDeposit());
        assertEquals("USD", config.getCurrency());
        assertEquals("1:100", config.getLeverage());
        assertEquals(0, config.getOptimization());
        assertTrue(config.isReplaceReport());
        assertTrue(config.isShutdownTerminal());
    }

    @Test
    public void testSetters() {
        BacktestConfig config = new BacktestConfig();
        config.setExpert("Experts\\Advisors\\MyEA.ex5");
        config.setSymbol("GBPUSD");
        config.setPeriod("M15");
        config.setModel(4);
        config.setDeposit(50000);
        
        assertEquals("Experts\\Advisors\\MyEA.ex5", config.getExpert());
        assertEquals("GBPUSD", config.getSymbol());
        assertEquals("M15", config.getPeriod());
        assertEquals(4, config.getModel());
        assertEquals(50000, config.getDeposit());
    }

    @Test
    public void testToDirectoryName() {
        BacktestConfig config = new BacktestConfig();
        config.setExpert("Experts\\Advisors\\MySuperEA.ex5");
        config.setPeriod("H4");
        config.setSymbol("USDJPY");
        LocalDate date = LocalDate.of(2023, 5, 15);
        config.setFromDate(date);
        
        String dirName = config.toDirectoryName();
        assertEquals("MySuperEA_H4_USDJPY_20230515", dirName);
    }
}
