package com.backtester.tools;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StrategyExporterTest {

    @Test
    public void sanitizesEaNameForWindowsFilename() {
        assertEquals("EA_Name______", StrategyExporter.sanitizeFilenameComponent("EA:Name?*\"<>|"));
    }
}
