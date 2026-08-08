package com.backtester.config;

import org.junit.Test;
import static org.junit.Assert.*;

public class EaParameterTest {

    @Test
    public void testDefaultState() {
        EaParameter param = new EaParameter();
        assertNull(param.getName());
        assertNull(param.getValue());
        assertNull(param.getDefaultValue());
        assertNull(param.getSection());
        assertEquals("", param.getOptimizeStart());
        assertEquals("", param.getOptimizeStep());
        assertEquals("", param.getOptimizeEnd());
        assertFalse(param.isOptimizeEnabled());
        assertFalse(param.isStringType());
        assertEquals("", param.getRawLine());
    }

    @Test
    public void testConstructorAndGettersSetters() {
        EaParameter param = new EaParameter("InpADRPeriod", "14");
        assertEquals("InpADRPeriod", param.getName());
        assertEquals("14", param.getValue());
        assertEquals("14", param.getDefaultValue());

        param.setOptimizeStart("10");
        param.setOptimizeStep("2");
        param.setOptimizeEnd("20");
        param.setOptimizeEnabled(true);
        param.setSection("Indicator settings");
        param.setStringType(false);
        param.setRawLine("InpADRPeriod=14||10||2||20||Y");

        assertEquals("10", param.getOptimizeStart());
        assertEquals("2", param.getOptimizeStep());
        assertEquals("20", param.getOptimizeEnd());
        assertTrue(param.isOptimizeEnabled());
        assertEquals("Indicator settings", param.getSection());
        assertFalse(param.isStringType());
        assertEquals("InpADRPeriod=14||10||2||20||Y", param.getRawLine());
    }

    @Test
    public void testIsModified() {
        EaParameter param = new EaParameter("InpGridStep", "20");
        assertFalse(param.isModified());

        param.setValue("25");
        assertTrue(param.isModified());

        param.resetToDefault();
        assertFalse(param.isModified());
        assertEquals("20", param.getValue());
    }

    @Test
    public void testToSetFileLine() {
        EaParameter p1 = new EaParameter("InpGridStep", "20");
        p1.setOptimizeStart("10");
        p1.setOptimizeStep("5");
        p1.setOptimizeEnd("50");
        p1.setOptimizeEnabled(true);
        assertEquals("InpGridStep=20||10||5||50||Y", p1.toSetFileLine());

        p1.setOptimizeEnabled(false);
        assertEquals("InpGridStep=20||10||5||50||N", p1.toSetFileLine());

        EaParameter p2 = new EaParameter("InpEAComment", "CC ADR EA");
        p2.setStringType(true);
        assertEquals("InpEAComment=CC ADR EA", p2.toSetFileLine());
    }

    @Test
    public void testToggleOptimizeEnabled() {
        EaParameter param = new EaParameter("InpStochK", "5");
        assertFalse(param.isOptimizeEnabled());

        param.setOptimizeEnabled(true);
        assertTrue(param.isOptimizeEnabled());

        param.setOptimizeEnabled(false);
        assertFalse(param.isOptimizeEnabled());
    }

    @Test
    public void testSectionHeaderDetectionAndFormatting() {
        EaParameter sectionParam = new EaParameter();
        sectionParam.setSectionHeader(true);
        sectionParam.setSection("MONEY MANAGE");
        assertTrue(sectionParam.isSectionHeader());
        assertEquals("📁  ---- MONEY MANAGE ----", sectionParam.getFormattedSectionTitle());

        EaParameter stringHeader = new EaParameter("Inp_Header_1", "--- GRID MODE ----");
        assertTrue(stringHeader.isSectionHeader());
        assertEquals("📁  ---- GRID MODE ----", stringHeader.getFormattedSectionTitle());

        EaParameter regularParam = new EaParameter("Inp_Initial_Lot", "0.01");
        assertFalse(regularParam.isSectionHeader());
    }
}
