package com.backtester.engine;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class MultiBacktestConfigTest {

    @Test
    public void testGetTotalCombinations() {
        MultiBacktestConfig config = new MultiBacktestConfig();
        config.setExperts(Arrays.asList("EA1", "EA2"));
        config.setSymbols(Arrays.asList("EURUSD", "GBPUSD", "USDJPY"));
        config.setPeriods(Arrays.asList("M15", "H1"));

        // 2 EAs * 3 Symbols * 2 Periods = 12
        assertEquals(12, config.getTotalCombinations());
    }

    @Test
    public void testGetTotalCombinationsEmptyLists() {
        MultiBacktestConfig config = new MultiBacktestConfig();
        // Even with empty lists, generateSingleConfigs provides fallback defaults (1 ea * 1 sym * 1 period = 1)
        assertEquals(1, config.getTotalCombinations());
    }

    @Test
    public void testGenerateSingleConfigs() {
        MultiBacktestConfig config = new MultiBacktestConfig();
        config.setExperts(Arrays.asList("EA1"));
        config.setSymbols(Arrays.asList("EURUSD", "GBPUSD"));
        config.setPeriods(Arrays.asList("H1"));
        config.setDeposit(25000);
        
        config.setExpertParameters("EA1", "EA1.set");

        List<BacktestConfig> singleConfigs = config.generateSingleConfigs();
        
        assertEquals(2, singleConfigs.size());
        
        BacktestConfig c1 = singleConfigs.get(0);
        assertEquals("EA1", c1.getExpert());
        assertEquals("EURUSD", c1.getSymbol());
        assertEquals("H1", c1.getPeriod());
        assertEquals(25000, c1.getDeposit());
        assertEquals("EA1.set", c1.getExpertParameters());
        
        BacktestConfig c2 = singleConfigs.get(1);
        assertEquals("EA1", c2.getExpert());
        assertEquals("GBPUSD", c2.getSymbol());
    }

    @Test
    public void testExpertParametersMap() {
        MultiBacktestConfig config = new MultiBacktestConfig();
        config.setExpertParameters("EA1", "set1.set");
        config.setExpertParameters("EA2", "set2.set");
        
        assertEquals("set1.set", config.getExpertParameters("EA1"));
        assertEquals("set2.set", config.getExpertParameters("EA2"));
        assertNull(config.getExpertParameters("EA3"));
    }
}
