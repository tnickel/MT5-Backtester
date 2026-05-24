package com.backtester.engine;

import org.junit.Test;
import static org.junit.Assert.*;

public class KiReportTest {

    @Test
    public void testConstructorAndGetters() {
        KiReport report = new KiReport(1, 1680000000L, "SuperBot", "EURUSD", "H1", "2023-01-01 10:00:00", "## Report\nGood results.");
        
        assertEquals(1, report.getId());
        assertEquals(1680000000L, report.getRunTimestamp());
        assertEquals("SuperBot", report.getExpertName());
        assertEquals("EURUSD", report.getSymbol());
        assertEquals("H1", report.getPeriod());
        assertEquals("2023-01-01 10:00:00", report.getCreatedAt());
        assertEquals("## Report\nGood results.", report.getReportMarkdown());
    }

    @Test
    public void testEmptyStrings() {
        KiReport report = new KiReport(0, 0L, "", "", "", "", "");
        
        assertEquals(0, report.getId());
        assertEquals("", report.getExpertName());
        assertEquals("", report.getReportMarkdown());
    }

    @Test
    public void testNullValues() {
        KiReport report = new KiReport(99, -1L, null, null, null, null, null);
        
        assertNull("Expert name should be null", report.getExpertName());
        assertNull("Symbol should be null", report.getSymbol());
        assertNull("Report Markdown should be null", report.getReportMarkdown());
    }

    @Test
    public void testRunTimestampEdgeCases() {
        KiReport reportMin = new KiReport(1, Long.MIN_VALUE, "Bot", "EURUSD", "H1", "date", "text");
        assertEquals(Long.MIN_VALUE, reportMin.getRunTimestamp());
        
        KiReport reportMax = new KiReport(2, Long.MAX_VALUE, "Bot", "EURUSD", "H1", "date", "text");
        assertEquals(Long.MAX_VALUE, reportMax.getRunTimestamp());
    }

    @Test
    public void testLargeMarkdownContent() {
        StringBuilder largeText = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeText.append("Line ").append(i).append("\n");
        }
        
        KiReport report = new KiReport(1, 123L, "Bot", "EURUSD", "H1", "date", largeText.toString());
        assertNotNull(report.getReportMarkdown());
        assertTrue("Markdown content should be large", report.getReportMarkdown().length() > 50000);
        assertTrue("Markdown content should end properly", report.getReportMarkdown().endsWith("Line 9999\n"));
    }
}
