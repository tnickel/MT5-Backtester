package com.backtester.report;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class SensitivityResultTest {

    @Test
    public void testInitialization() {
        OptimizationResult.CombinedPass pass = null; // using null for tests
        SensitivityResult result = new SensitivityResult(pass);
        
        assertEquals(pass, result.getOriginalPass());
        assertEquals("Pending", result.getStatus());
        assertEquals("", result.getKiResult());
        assertFalse(result.hasForwardCV());
    }

    @Test
    public void testBacktestCVsAndWorstCase() {
        SensitivityResult result = new SensitivityResult(null);
        result.addParameterCV("TakeProfit", 0.05);
        result.addParameterCV("StopLoss", 0.15); // Worst case
        result.addParameterCV("Trailing", 0.10);
        
        assertEquals(3, result.getParameterCVs().size());
        assertEquals(0.15, result.getOverallCV(), 0.0001);
    }

    @Test
    public void testForwardCVsAndWorstCase() {
        SensitivityResult result = new SensitivityResult(null);
        result.addParameterCVFw("TakeProfit", 0.20); // Worst case
        result.addParameterCVFw("StopLoss", 0.05);
        
        assertTrue(result.hasForwardCV());
        assertEquals(2, result.getParameterCVsFw().size());
        assertEquals(0.20, result.getOverallCVFw(), 0.0001);
    }

    @Test
    public void testParameterCurves() {
        SensitivityResult result = new SensitivityResult(null);
        List<SensitivityResult.DataPoint> curve = new ArrayList<>();
        curve.add(new SensitivityResult.DataPoint(10.0, 100.0));
        curve.add(new SensitivityResult.DataPoint(20.0, 150.0));
        
        result.addParameterCurve("TakeProfit", curve);
        
        assertEquals(1, result.getParameterCurves().size());
        assertEquals(2, result.getParameterCurves().get("TakeProfit").size());
        assertEquals(150.0, result.getParameterCurves().get("TakeProfit").get(1).profit, 0.001);
    }

    @Test
    public void testStatusAndKiResult() {
        SensitivityResult result = new SensitivityResult(null);
        result.setStatus("Analyzed");
        result.setKiResult("Great stability");
        
        assertEquals("Analyzed", result.getStatus());
        assertEquals("Great stability", result.getKiResult());
        assertEquals("Great stability", result.kiResultProperty().get());
    }

    @Test
    public void testHasForwardCV() {
        SensitivityResult result = new SensitivityResult(null);
        assertFalse(result.hasForwardCV());
        
        result.addParameterCVFw("Param", 0.1);
        assertTrue(result.hasForwardCV());
    }
    @Test
    public void testSerialization() {
        com.backtester.report.OptimizationResult.Pass btPass = new com.backtester.report.OptimizationResult.Pass();
        btPass.setPassNumber(42);
        btPass.setProfit(1000.0);
        btPass.setTotalTrades(20);
        
        com.backtester.report.OptimizationResult.Pass fwPass = new com.backtester.report.OptimizationResult.Pass();
        fwPass.setPassNumber(42);
        fwPass.setProfit(500.0);
        fwPass.setTotalTrades(10);
        
        com.backtester.report.OptimizationResult.CombinedPass combined = new com.backtester.report.OptimizationResult.CombinedPass(
            btPass, fwPass, 85.0, 90.0, "details"
        );
        
        SensitivityResult result = new SensitivityResult(combined);
        result.addParameterCV("Param1", 0.12);
        result.addParameterCVFw("Param1", 0.18);
        result.setKiResult("95");
        
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
            .registerTypeHierarchyAdapter(javafx.beans.property.StringProperty.class,
                new com.google.gson.TypeAdapter<javafx.beans.property.StringProperty>() {
                    @Override
                    public void write(com.google.gson.stream.JsonWriter out, javafx.beans.property.StringProperty value) throws java.io.IOException {
                        if (value == null) out.nullValue();
                        else out.value(value.get());
                    }
                    @Override
                    public javafx.beans.property.StringProperty read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
                        if (in.peek() == com.google.gson.stream.JsonToken.NULL) { in.nextNull(); return new javafx.beans.property.SimpleStringProperty(""); }
                        return new javafx.beans.property.SimpleStringProperty(in.nextString());
                    }
                })
            .create();
            
        String json = gson.toJson(result);
        assertNotNull(json);
        assertTrue(json.contains("\"overallCV\":0.12"));
        assertTrue(json.contains("\"overallCVFw\":0.18"));
        assertTrue(json.contains("\"kiResult\":\"95\""));
        
        SensitivityResult deserialized = gson.fromJson(json, SensitivityResult.class);
        assertNotNull(deserialized);
        assertEquals("95", deserialized.getKiResult());
        assertEquals(0.12, deserialized.getOverallCV(), 0.0001);
        assertEquals(0.18, deserialized.getOverallCVFw(), 0.0001);
        assertNotNull(deserialized.getOriginalPass());
        assertEquals(42, deserialized.getOriginalPass().getPassNumber());
        assertEquals(1000.0, deserialized.getOriginalPass().getBtProfit(), 0.001);
    }
}
