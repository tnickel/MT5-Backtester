package com.backtester.report;

import com.backtester.engine.BacktestConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BacktestStatisticsArtifactTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void persistsAllScalarBacktestStatisticsAndConfiguration() throws Exception {
        BacktestResult result = new BacktestResult();
        result.setSuccess(true);
        result.setExpert("TestEA");
        result.setSymbol("AUDCAD");
        result.setPeriod("M5");
        result.setOutputDirectory("reports/run");
        result.setTickModel("Every tick");
        result.setUsedDefaultConfig(false);
        result.setConfigInfo("Custom");
        result.setInitialDeposit(10_000);
        result.setFinalBalance(12_500);
        result.setTotalProfit(2_500);
        result.setGrossProfit(5_000);
        result.setGrossLoss(-2_500);
        result.setTotalTrades(200);
        result.setProfitTrades(150);
        result.setLossTrades(50);
        result.setShortPositions(90);
        result.setLongPositions(110);
        result.setWinRate(75);
        result.setMaxDrawdown(24.94);
        result.setMaxDrawdownAbsolute(13_709.48);
        result.setMaxDrawdownPercent(26.89);
        result.setBalanceDrawdown(21.17);
        result.setBalanceDrawdownAbsolute(11_738.29);
        result.setProfitFactor(1.41);
        result.setSharpeRatio(1.24);
        result.setRecoveryFactor(4.05);
        result.setExpectedPayoff(26.52);
        result.setLargestWin(1_499.22);
        result.setLargestLoss(-3_951.21);
        result.setAverageWin(114.41);
        result.setAverageLoss(-325.85);
        result.setEquityHistory(List.of(new double[]{0, 10_000, 10_000},
                new double[]{1, 12_500, 12_400}));
        LinkedHashMap<String, String> rawStatistics = new LinkedHashMap<>();
        rawStatistics.put("Qualität der Historie", "99%");
        rawStatistics.put("Ticks", "104559972");
        rawStatistics.put("Z-Score", "8.17 (99.74%)");
        result.setRawStatistics(rawStatistics);

        BacktestConfig config = new BacktestConfig();
        config.setExpert("TestEA");
        config.setExpertParameters("Longterm_Pass11180.set");
        config.setSymbol("AUDCAD");
        config.setPeriod("M5");
        config.setModel(0);
        config.setFromDate(LocalDate.of(2022, 8, 1));
        config.setToDate(LocalDate.of(2026, 8, 1));

        Path directory = tempFolder.newFolder("statistics").toPath();
        BacktestStatisticsArtifact.write(directory, result, config);
        Path file = directory.resolve(BacktestStatisticsArtifact.FILE_NAME);
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();

        assertTrue(Files.isRegularFile(file));
        assertEquals(1, json.get("schemaVersion").getAsInt());
        assertEquals(2_500, json.get("totalProfit").getAsDouble(), 0.001);
        assertEquals(5_000, json.get("grossProfit").getAsDouble(), 0.001);
        assertEquals(-2_500, json.get("grossLoss").getAsDouble(), 0.001);
        assertEquals(200, json.get("totalTrades").getAsInt());
        assertEquals(26.89, json.get("relativeEquityDrawdownPercent").getAsDouble(), 0.001);
        assertEquals(24.94, json.get("maximalEquityDrawdownPercent").getAsDouble(), 0.001);
        assertEquals(13_709.48, json.get("maximalEquityDrawdownAbsolute").getAsDouble(), 0.001);
        assertEquals(21.17, json.get("maximalBalanceDrawdownPercent").getAsDouble(), 0.001);
        assertEquals(1.41, json.get("profitFactor").getAsDouble(), 0.001);
        assertEquals(4.05, json.get("recoveryFactor").getAsDouble(), 0.001);
        assertEquals(2, json.get("equityHistoryPoints").getAsInt());
        assertEquals("Longterm_Pass11180.set", json.get("expertParameters").getAsString());
        assertEquals("2022-08-01", json.get("fromDate").getAsString());
        assertEquals(2_500, json.get("profit").getAsDouble(), 0.001);
        assertEquals(26.89, json.get("drawdown").getAsDouble(), 0.001);
        assertEquals(200, json.get("trades").getAsInt());
        assertEquals(24.94, json.get("maxDrawdown").getAsDouble(), 0.001);
        assertEquals(26.89, json.get("maxDrawdownPercent").getAsDouble(), 0.001);
        assertEquals(21.17, json.get("balanceDrawdown").getAsDouble(), 0.001);
        JsonObject raw = json.getAsJsonObject("rawStatistics");
        assertEquals("99%", raw.get("Qualität der Historie").getAsString());
        assertEquals("104559972", raw.get("Ticks").getAsString());
        assertEquals("8.17 (99.74%)", raw.get("Z-Score").getAsString());
    }
}
