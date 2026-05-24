package com.backtester.report;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class RobustnessResultTest {

    @Test
    public void testDefaultValues() {
        RobustnessResult result = new RobustnessResult();
        assertFalse(result.isSuccess());
        assertEquals("Not initialized", result.getMessage());
        assertNull(result.getOutputDirectory());
        assertTrue(result.getParameterSweeps().isEmpty());
    }

    @Test
    public void testSuccessToggle() {
        RobustnessResult result = new RobustnessResult();
        result.setSuccess(true);
        assertTrue(result.isSuccess());
        result.setSuccess(false);
        assertFalse(result.isSuccess());
    }

    @Test
    public void testMessageToggle() {
        RobustnessResult result = new RobustnessResult();
        result.setMessage("Processing complete");
        assertEquals("Processing complete", result.getMessage());
    }

    @Test
    public void testOutputDirectory() {
        RobustnessResult result = new RobustnessResult();
        result.setOutputDirectory("C:/test/dir");
        assertEquals("C:/test/dir", result.getOutputDirectory());
    }

    @Test
    public void testAddSweepMultipleParameters() {
        RobustnessResult result = new RobustnessResult();
        Map<String, OptimizationResult> map1 = new HashMap<>();
        Map<String, OptimizationResult> map2 = new HashMap<>();
        
        result.addSweep("TakeProfit", map1);
        result.addSweep("StopLoss", map2);
        
        assertEquals(2, result.getParameterSweeps().size());
        assertTrue(result.getParameterSweeps().containsKey("TakeProfit"));
        assertTrue(result.getParameterSweeps().containsKey("StopLoss"));
    }

    @Test
    public void testAddSweepEmptyMap() {
        RobustnessResult result = new RobustnessResult();
        result.addSweep("EmptyParam", new HashMap<>());
        
        assertNotNull(result.getParameterSweeps().get("EmptyParam"));
        assertTrue(result.getParameterSweeps().get("EmptyParam").isEmpty());
    }

    @Test
    public void testSweepIterationOrder() {
        RobustnessResult result = new RobustnessResult();
        result.addSweep("ParamA", new HashMap<>());
        result.addSweep("ParamC", new HashMap<>());
        result.addSweep("ParamB", new HashMap<>());
        
        // LinkedHashMap should preserve insertion order
        String[] keys = result.getParameterSweeps().keySet().toArray(new String[0]);
        assertEquals("ParamA", keys[0]);
        assertEquals("ParamC", keys[1]);
        assertEquals("ParamB", keys[2]);
    }
}
