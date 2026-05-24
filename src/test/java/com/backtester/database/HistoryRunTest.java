package com.backtester.database;

import org.junit.Test;
import static org.junit.Assert.*;

public class HistoryRunTest {

    @Test
    public void testConstructorAndGetters() {
        HistoryRun run = new HistoryRun(1, "BACKTEST", "MyEA", 123456789L, "{}", "/path/to/report.html");
        
        assertEquals(1, run.getId());
        assertEquals("BACKTEST", run.getRunType());
        assertEquals("MyEA", run.getExpertName());
        assertEquals(123456789L, run.getTimestamp());
        assertEquals("{}", run.getResultJson());
        assertEquals("/path/to/report.html", run.getHtmlPath());
    }

    @Test
    public void testSetters() {
        HistoryRun run = new HistoryRun();
        run.setId(2);
        run.setRunType("OPTIMIZATION");
        run.setExpertName("AnotherEA");
        run.setTimestamp(987654321L);
        run.setResultJson("{\"key\":\"value\"}");
        run.setHtmlPath("/new/path.html");
        
        assertEquals(2, run.getId());
        assertEquals("OPTIMIZATION", run.getRunType());
        assertEquals("AnotherEA", run.getExpertName());
        assertEquals(987654321L, run.getTimestamp());
        assertEquals("{\"key\":\"value\"}", run.getResultJson());
        assertEquals("/new/path.html", run.getHtmlPath());
    }
}
