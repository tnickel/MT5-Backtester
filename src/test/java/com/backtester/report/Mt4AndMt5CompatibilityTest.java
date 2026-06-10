package com.backtester.report;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Compatibility test suite containing 100 dynamically generated unit tests
 * running on both MT4 and MT5 strategy tester HTML formats, covering English and German.
 */
@RunWith(Parameterized.class)
public class Mt4AndMt5CompatibilityTest {

    private final boolean isMt4;
    private final String html;
    private final double expectedProfit;
    private final double expectedDrawdown;
    private final int expectedTrades;

    public Mt4AndMt5CompatibilityTest(boolean isMt4, String html, double expectedProfit, double expectedDrawdown, int expectedTrades) {
        this.isMt4 = isMt4;
        this.html = html;
        this.expectedProfit = expectedProfit;
        this.expectedDrawdown = expectedDrawdown;
        this.expectedTrades = expectedTrades;
    }

    @Parameters(name = "{index}: MT4={0}, profit={2}, drawdown={3}%, trades={4}")
    public static Collection<Object[]> data() {
        List<Object[]> list = new ArrayList<>();

        // Generate 50 tests for MT4 (alternating German / English)
        for (int i = 1; i <= 50; i++) {
            double profit = i * 12.5;
            double drawdown = 0.5 + (i * 0.05);
            int trades = 5 + i;
            boolean isGerman = (i % 2 == 0);

            String html;
            if (isGerman) {
                html = "<table>"
                        + "<tr><td>Nettoprofit gesamt</td><td>" + profit + "</td></tr>"
                        + "<tr><td>Maximaler Rückgang</td><td>100.00 (" + drawdown + "%)</td></tr>"
                        + "<tr><td>Anzahl an Trades</td><td>" + trades + "</td></tr>"
                        + "</table>";
            } else {
                html = "<table>"
                        + "<tr><td>Total net profit</td><td>" + profit + "</td></tr>"
                        + "<tr><td>Maximal drawdown</td><td>100.00 (" + drawdown + "%)</td></tr>"
                        + "<tr><td>Total trades</td><td>" + trades + "</td></tr>"
                        + "</table>";
            }

            list.add(new Object[]{true, html, profit, drawdown, trades});
        }

        // Generate 50 tests for MT5 (alternating German / English)
        for (int i = 1; i <= 50; i++) {
            double profit = i * 22.0;
            double drawdown = 1.0 + (i * 0.08);
            int trades = 15 + i;
            boolean isGerman = (i % 2 == 0);

            String html;
            if (isGerman) {
                html = "<table>"
                        + "<tr><td>Nettogewinn gesamt:</td><td><b>" + profit + "</b></td></tr>"
                        + "<tr><td>Rückgang Equity maximal:</td><td><b>300.00 (" + drawdown + "%)</b></td></tr>"
                        + "<tr><td>Gesamtanzahl Trades:</td><td><b>" + trades + "</b></td></tr>"
                        + "</table>";
            } else {
                html = "<table>"
                        + "<tr><td>Total Net Profit:</td><td><b>" + profit + "</b></td></tr>"
                        + "<tr><td>Maximal Equity Drawdown:</td><td><b>300.00 (" + drawdown + "%)</b></td></tr>"
                        + "<tr><td>Total Trades:</td><td><b>" + trades + "</b></td></tr>"
                        + "</table>";
            }

            list.add(new Object[]{false, html, profit, drawdown, trades});
        }

        return list;
    }

    @Test
    public void testCompatParsing() throws Exception {
        Path file = Files.createTempFile("compat_report_test", ".htm");
        Files.writeString(file, html);
        try {
            ReportParser parser = new ReportParser();
            BacktestResult result = parser.parse(file);
            assertEquals(expectedProfit, result.getTotalProfit(), 0.001);
            assertEquals(expectedDrawdown, result.getMaxDrawdown(), 0.001);
            assertEquals(expectedTrades, result.getTotalTrades());
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
