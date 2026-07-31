package com.backtester.report;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.*;

import com.backtester.ui.javafx.MultiBacktestView;

import static org.junit.Assert.*;

public class CurrencyPairCsvExportImportTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testExportFilterLogic() throws Exception {
        List<BacktestResult> results = new ArrayList<>();

        // 1. Profit > 0 AND Trades > 10 -> Should be exported
        BacktestResult r1 = new BacktestResult();
        r1.setSymbol("EURUSD");
        r1.setSuccess(true);
        r1.setTotalProfit(500.0);
        r1.setTotalTrades(15);
        results.add(r1);

        // 2. Profit > 0 BUT Trades <= 10 -> Should NOT be exported
        BacktestResult r2 = new BacktestResult();
        r2.setSymbol("GBPUSD");
        r2.setSuccess(true);
        r2.setTotalProfit(300.0);
        r2.setTotalTrades(8);
        results.add(r2);

        // 3. Profit <= 0 AND Trades > 10 -> Should NOT be exported
        BacktestResult r3 = new BacktestResult();
        r3.setSymbol("USDJPY");
        r3.setSuccess(true);
        r3.setTotalProfit(-150.0);
        r3.setTotalTrades(25);
        results.add(r3);

        // 4. Failed run -> Should NOT be exported
        BacktestResult r4 = new BacktestResult();
        r4.setSymbol("AUDCAD");
        r4.setSuccess(false);
        r4.setTotalProfit(100.0);
        r4.setTotalTrades(20);
        results.add(r4);

        // Filter symbols matching criteria
        List<BacktestResult> successfulRuns = new ArrayList<>();
        for (BacktestResult r : results) {
            if (r.isSuccess() && r.getTotalProfit() > 0 && r.getTotalTrades() > 10) {
                if (r.getSymbol() != null && !r.getSymbol().trim().isEmpty()) {
                    successfulRuns.add(r);
                }
            }
        }

        assertEquals(1, successfulRuns.size());
        assertEquals("EURUSD", successfulRuns.get(0).getSymbol());

        // Write detailed CSV file
        File csvFile = tempFolder.newFile("test_pairs.csv");
        try (PrintWriter writer = new PrintWriter(csvFile)) {
            writer.println("Symbol,Period,Profit,Trades,Drawdown");
            for (BacktestResult r : successfulRuns) {
                writer.println(String.format(Locale.US, "%s,%s,%.2f,%d,%.2f", r.getSymbol(), "M5", r.getTotalProfit(), r.getTotalTrades(), 4.25));
            }
        }

        // Read back from CSV file
        List<MultiBacktestView.ImportedPair> importedPairs = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.toLowerCase().startsWith("symbol") || line.startsWith("#")) continue;
                String[] tokens = line.split("[,;\\t]+");
                if (tokens.length >= 1) {
                    String sym = tokens[0].trim().toUpperCase();
                    if (!sym.isEmpty()) {
                        MultiBacktestView.ImportedPair pair = new MultiBacktestView.ImportedPair(sym);
                        if (tokens.length >= 5) {
                            pair.period = tokens[1].trim();
                            pair.profit = Double.parseDouble(tokens[2].trim());
                            pair.trades = Integer.parseInt(tokens[3].trim());
                            pair.drawdown = Double.parseDouble(tokens[4].trim());
                            pair.hasDetails = true;
                        }
                        importedPairs.add(pair);
                    }
                }
            }
        }

        assertEquals(1, importedPairs.size());
        MultiBacktestView.ImportedPair item = importedPairs.get(0);
        assertEquals("EURUSD", item.symbol);
        assertEquals("M5", item.period);
        assertEquals(500.0, item.profit, 0.001);
        assertEquals(15, item.trades);
        assertEquals(4.25, item.drawdown, 0.001);
        assertTrue(item.hasDetails);
    }
}
