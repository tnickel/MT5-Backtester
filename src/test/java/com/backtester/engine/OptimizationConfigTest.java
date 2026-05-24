package com.backtester.engine;

import org.junit.Test;
import static org.junit.Assert.*;

public class OptimizationConfigTest {

    @Test
    public void testDefaultValues() {
        OptimizationConfig config = new OptimizationConfig();
        assertEquals(2, config.getOptimizationMode()); // 2 = Fast Genetic
        assertEquals(0, config.getOptimizationCriterion()); // 0 = Balance max
        assertEquals(0, config.getForwardMode()); // 0 = Off
        assertTrue(config.isUseLocal());
        assertFalse(config.isUseRemote());
        assertFalse(config.isUseCloud());
        assertFalse(config.isForwardEnabled());
    }

    @Test
    public void testSetters() {
        OptimizationConfig config = new OptimizationConfig();
        config.setOptimizationMode(1); // Complete
        config.setOptimizationCriterion(4); // Recovery Factor max
        config.setForwardMode(2); // 1/3
        
        assertEquals(1, config.getOptimizationMode());
        assertEquals(4, config.getOptimizationCriterion());
        assertEquals(2, config.getForwardMode());
        assertTrue(config.isForwardEnabled());
    }

    @Test
    public void testToDirectoryName() {
        OptimizationConfig config = new OptimizationConfig();
        config.setExpert("Experts\\Advisors\\AwesomeEA");
        config.setPeriod("M5");
        config.setSymbol("XAUUSD");
        
        String dirName = config.toDirectoryName();
        assertEquals("OPT_AwesomeEA_M5_XAUUSD", dirName);
        
        // Test with spaces
        config.setExpert("My Great EA.ex5");
        assertEquals("OPT_My_Great_EA.ex5_M5_XAUUSD", config.toDirectoryName());
    }
}
