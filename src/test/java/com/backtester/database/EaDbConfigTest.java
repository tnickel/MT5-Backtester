package com.backtester.database;

import org.junit.Test;
import static org.junit.Assert.*;

public class EaDbConfigTest {

    @Test
    public void testConstructorAndGetters() {
        EaDbConfig config = new EaDbConfig(10, "SuperEA", "Default Config", "{\"param\":\"val\"}", 1000L);
        
        assertEquals(10, config.getId());
        assertEquals("SuperEA", config.getExpertName());
        assertEquals("Default Config", config.getConfigName());
        assertEquals("{\"param\":\"val\"}", config.getParametersJson());
        assertEquals(1000L, config.getUpdatedAt());
    }

    @Test
    public void testSetters() {
        EaDbConfig config = new EaDbConfig(10, "SuperEA", "Default Config", "{\"param\":\"val\"}", 1000L);
        
        config.setConfigName("New Config");
        config.setParametersJson("{\"param\":\"newVal\"}");
        config.setUpdatedAt(2000L);
        
        assertEquals("New Config", config.getConfigName());
        assertEquals("{\"param\":\"newVal\"}", config.getParametersJson());
        assertEquals(2000L, config.getUpdatedAt());
        
        // Check that immutable fields remain unchanged
        assertEquals(10, config.getId());
        assertEquals("SuperEA", config.getExpertName());
    }
}
