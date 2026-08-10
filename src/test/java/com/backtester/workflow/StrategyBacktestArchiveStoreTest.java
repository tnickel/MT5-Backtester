package com.backtester.workflow;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class StrategyBacktestArchiveStoreTest {

    @Test
    public void upsertSameTabReplacesRunOtherTabAppends() {
        CustomProject project = new CustomProject("P", "EA.ex5", "EURUSD", "H1");
        CombinedPass pass = samplePass(12, "Strat 12");

        StrategyBacktestRun tick1 = StrategyBacktestArchiveStore.buildRun(
                "ticktest", "Tickdatatest", "EURUSD", "H1", "1 minute OHLC",
                "2020-01-01", "2024-01-01", "SET-A", sampleResult(100));
        StrategyBacktestArchiveStore.upsertRun(project, pass, tick1);

        StrategyBacktestRun tick2 = StrategyBacktestArchiveStore.buildRun(
                "ticktest", "Tickdatatest", "EURUSD", "H1", "Every tick",
                "2018-01-01", "2024-01-01", "SET-B", sampleResult(200));
        StrategyBacktestArchiveStore.upsertRun(project, pass, tick2);

        StrategyBacktestRun data2 = StrategyBacktestArchiveStore.buildRun(
                "data2", "Other", "EURUSD", "H1", "1 minute OHLC",
                "2021-01-01", "2023-01-01", "SET-C", sampleResult(50));
        StrategyBacktestArchiveStore.upsertRun(project, pass, data2);

        List<StrategyBacktestRun> runs = StrategyBacktestArchiveStore.getAllRuns(project, pass);
        assertEquals(2, runs.size());

        Optional<StrategyBacktestRun> tick = StrategyBacktestArchiveStore.getRun(
                project, StrategyBacktestArchiveStore.strategyKey(pass), "ticktest");
        assertTrue(tick.isPresent());
        assertEquals("SET-B", tick.get().getSetfileContent());
        assertEquals(200.0, tick.get().getResult().getProfit(), 0.001);

        Optional<StrategyBacktestRun> other = StrategyBacktestArchiveStore.getRun(
                project, StrategyBacktestArchiveStore.strategyKey(pass), "data2");
        assertTrue(other.isPresent());
        assertEquals("SET-C", other.get().getSetfileContent());
    }

    @Test
    public void copyMetadataForPersistenceKeepsArchivesWhenDatabanksPersist() {
        CustomProject project = new CustomProject("P", "EA.ex5", "EURUSD", "H1");
        project.setSaveDatabanksPersistently(true);
        CombinedPass pass = samplePass(1, "Strat 1");
        StrategyBacktestArchiveStore.upsertRun(project, pass,
                StrategyBacktestArchiveStore.buildRun("ticktest", "T", "EURUSD", "H1",
                        "1 minute OHLC", "a", "b", "CONTENT", sampleResult(1)));

        CustomProject snapshot = project.copyMetadataForPersistence();
        assertEquals(1, StrategyBacktestArchiveStore.getAllRuns(snapshot, pass).size());
        assertEquals("CONTENT", StrategyBacktestArchiveStore.getAllRuns(snapshot, pass)
                .get(0).getSetfileContent());
    }

    @Test
    public void copyMetadataForPersistenceDropsArchivesWhenDatabanksNotPersisted() {
        CustomProject project = new CustomProject("P", "EA.ex5", "EURUSD", "H1");
        project.setSaveDatabanksPersistently(false);
        CombinedPass pass = samplePass(1, "Strat 1");
        StrategyBacktestArchiveStore.upsertRun(project, pass,
                StrategyBacktestArchiveStore.buildRun("ticktest", "T", "EURUSD", "H1",
                        "1 minute OHLC", "a", "b", "CONTENT", sampleResult(1)));

        CustomProject snapshot = project.copyMetadataForPersistence();
        assertTrue(StrategyBacktestArchiveStore.getAllRuns(snapshot, pass).isEmpty());
    }

    @Test
    public void gsonRoundTripPreservesArchive() {
        CustomProject project = new CustomProject("P", "EA.ex5", "EURUSD", "H1");
        CombinedPass pass = samplePass(7, "Strat 7");
        StrategyBacktestArchiveStore.upsertRun(project, pass,
                StrategyBacktestArchiveStore.buildRun("ticktest", "Task", "AUDCAD", "M5",
                        "Every tick", "2020-01-01", "2021-01-01", "ABC", sampleResult(42)));

        Gson gson = new GsonBuilder().create();
        String json = gson.toJson(project);
        CustomProject loaded = gson.fromJson(json, CustomProject.class);

        List<StrategyBacktestRun> runs = StrategyBacktestArchiveStore.getAllRuns(loaded, pass);
        assertEquals(1, runs.size());
        assertEquals("ticktest", runs.get(0).getTabName());
        assertEquals("ABC", runs.get(0).getSetfileContent());
        assertNotNull(runs.get(0).getResult());
        assertEquals(42.0, runs.get(0).getResult().getProfit(), 0.001);
        assertEquals("AUDCAD", runs.get(0).getSymbol());
    }

    @Test
    public void migrateFromLongtermPassSeedsMissingTab() {
        CustomProject project = new CustomProject("P", "EA.ex5", "EURUSD", "H1");
        CombinedPass pass = samplePass(3, "Strat 3");
        Pass lt = sampleResult(99);
        lt.setFromDate("2015-01-01");
        lt.setToDate("2020-01-01");
        lt.setTickModel("1 minute OHLC");
        pass.setLongtermPass(lt);
        pass.setSymbol("EURUSD");
        pass.setPeriod("H1");

        List<CombinedPass> list = new ArrayList<>();
        list.add(pass);
        project.getDatabanks().put("ticktest", list);

        StrategyBacktestArchiveStore.migrateFromLongtermPasses(project);

        List<StrategyBacktestRun> runs = StrategyBacktestArchiveStore.getAllRuns(project, pass);
        assertEquals(1, runs.size());
        assertEquals("ticktest", runs.get(0).getTabName());
        assertEquals(99.0, runs.get(0).getResult().getProfit(), 0.001);
        assertEquals("", runs.get(0).getSetfileContent());

        // Second migrate must not overwrite an existing richer run
        runs.get(0).setSetfileContent("KEEP");
        StrategyBacktestArchiveStore.migrateFromLongtermPasses(project);
        assertEquals("KEEP", StrategyBacktestArchiveStore.getAllRuns(project, pass)
                .get(0).getSetfileContent());
    }

    @Test
    public void loadFromProjectMigratesLegacyLongtermPass() {
        CustomProject project = new CustomProject("P", "EA.ex5", "EURUSD", "H1");
        CombinedPass pass = samplePass(5, "Strat 5");
        pass.setLongtermPass(sampleResult(11));
        project.getDatabanks().put("data2", List.of(pass));

        DatabankManager manager = new DatabankManager();
        manager.loadFromProject(project);

        assertFalse(StrategyBacktestArchiveStore.getAllRuns(project, pass).isEmpty());
        assertEquals("data2", StrategyBacktestArchiveStore.getAllRuns(project, pass).get(0).getTabName());
    }

    @Test
    public void readSetfileContentDecodesUtf16LeWithBom() throws Exception {
        Path temp = Files.createTempFile("archive-set", ".set");
        try {
            byte[] bom = new byte[]{(byte) 0xFF, (byte) 0xFE};
            byte[] body = "Param=1\r\n".getBytes(StandardCharsets.UTF_16LE);
            byte[] all = new byte[bom.length + body.length];
            System.arraycopy(bom, 0, all, 0, bom.length);
            System.arraycopy(body, 0, all, bom.length, body.length);
            Files.write(temp, all);

            String content = StrategyBacktestArchiveStore.readSetfileContent(temp);
            assertTrue(content.contains("Param=1"));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static CombinedPass samplePass(int passNumber, String name) {
        Pass bt = new Pass();
        bt.setPassNumber(passNumber);
        bt.setProfit(1);
        bt.setTotalTrades(10);
        CombinedPass cp = new CombinedPass(bt, null, 1.0, 1.0, "");
        cp.setStrategyName(name);
        return cp;
    }

    private static Pass sampleResult(double profit) {
        Pass p = new Pass();
        p.setPassNumber(1);
        p.setProfit(profit);
        p.setTotalTrades(20);
        p.setProfitFactor(1.5);
        p.setDrawdownPercent(10.0);
        return p;
    }
}
