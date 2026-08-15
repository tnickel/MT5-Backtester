package com.backtester.workflow;

import com.backtester.database.DatabaseManager;
import com.backtester.engine.WorkflowEngine;
import com.backtester.report.OptimizationResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ClusterCensusTest {

    @Test
    public void combinedPassCopyPreservesClusterId() {
        CombinedPass original = pass(3, 50.0);
        original.setClusterId("B4");
        original.setStrategyName("Grid");

        CombinedPass copy = original.copy();

        assertEquals("B4", copy.getClusterId());
        assertEquals("Grid", copy.getStrategyName());
        assertEquals(3, copy.getPassNumber());
        copy.setClusterId("B9");
        assertEquals("B4", original.getClusterId());
    }

    @Test
    public void gsonRoundtripKeepsCensusAndClusteredPasses() {
        CustomProject project = new CustomProject("Clusters", "EA.ex5", "EURUSD", "H1");
        CombinedPass clustered = pass(8, 120.0);
        clustered.setClusterId("B2");
        clustered.setStrategyName("Exit");
        project.getDatabanks().put("g09_entry", List.of(clustered));

        ClusterCensus.ClusterLine line = new ClusterCensus.ClusterLine();
        line.setClusterId("B2");
        line.setLabel("Grid-eng");
        line.setStatus(ClusterCensus.ClusterStatus.LIVE);
        line.setLastReferenceRatio(3.25);
        line.setLastReferenceProfit(410.0);
        ClusterCensus.ClusterStageSnapshot snapshot = new ClusterCensus.ClusterStageSnapshot();
        snapshot.setDatabankName("g09_entry");
        snapshot.setLiveCount(1);
        snapshot.setChampionPassNumber(8);
        snapshot.setVerdict(ClusterCensus.StageVerdict.PENDING);
        line.setPerStage(List.of(snapshot));
        ClusterCensus census = new ClusterCensus();
        census.setClusters(List.of(line));
        project.setClusterCensus(census);

        CustomProject persisted = project.copyMetadataForPersistence();
        persisted.getDatabanks().put("g09_entry", List.of(clustered.copy()));
        String json = DatabaseManager.createCustomProjectGson().toJson(persisted);
        CustomProject restored = DatabaseManager.createCustomProjectGson()
                .fromJson(json, CustomProject.class);

        assertNotNull(restored);
        assertEquals("B2", restored.getClusterCensus().findLine("B2").getClusterId());
        assertEquals("Grid-eng", restored.getClusterCensus().findLine("B2").getLabel());
        assertEquals(3.25, restored.getClusterCensus().findLine("B2").getLastReferenceRatio(), 1e-9);
        assertEquals(410.0, restored.getClusterCensus().findLine("B2").getLastReferenceProfit(), 1e-9);
        assertEquals("B2", restored.getDatabanks().get("g09_entry").get(0).getClusterId());
        assertTrue(json.contains("\"clusterId\": \"B2\""));
    }

    @Test
    public void diversityOutputLeavesClusterStampingToTheDatabank() {
        WorkflowEngine engine = new WorkflowEngine(null);
        Pass first = diversePass(1, 10);
        Pass second = diversePass(2, 80);

        List<CombinedPass> selected = engine.clusterDatabankPasses(List.of(
                new CombinedPass(first, null, 10.0, 1.0, ""),
                new CombinedPass(second, null, 80.0, 1.0, "")),
                0.10, 0.15, 1, 10);

        assertEquals(2, selected.size());
        assertTrue(selected.get(0).getClusterId() == null || selected.get(0).getClusterId().isBlank());
        assertTrue(selected.get(1).getClusterId() == null || selected.get(1).getClusterId().isBlank());
        assertEquals(1, selected.get(0).getPassNumber());
        assertEquals(2, selected.get(1).getPassNumber());

        DatabankManager manager = new DatabankManager();
        WorkflowTask cluster = new WorkflowTask("Cluster", WorkflowTask.TaskType.DIVERSITY_FILTER);
        cluster.setSourceDatabank("src");
        cluster.setTargetDatabank("g01_grid_pick");
        cluster.setDiversityMaxStrategies(ClusterIdentity.MAX_CLUSTERS);
        cluster.setDiversityStampClusterIds(true);
        List<CombinedPass> stamped = manager.processTaskDatabanks(cluster, selected);
        assertEquals("B1", stamped.get(0).getClusterId());
        assertEquals("B2", stamped.get(1).getClusterId());
    }

    @Test
    public void existingClusterIdIsKeptWhenStamping() {
        CombinedPass already = pass(1, 90.0);
        already.setClusterId("B7");
        CombinedPass fresh = pass(2, 80.0);

        List<CombinedPass> stamped = ClusterIdentity.stampInOrder(List.of(already, fresh));

        assertEquals("B7", stamped.get(0).getClusterId());
        assertEquals("B1", stamped.get(1).getClusterId());
    }

    @Test
    public void secondDiversityPassKeepsExistingClusterIds() {
        WorkflowEngine engine = new WorkflowEngine(null);
        CombinedPass first = new CombinedPass(diversePass(1, 10), null, 10.0, 1.0, "");
        first.setClusterId("B3");
        CombinedPass second = new CombinedPass(diversePass(2, 80), null, 80.0, 1.0, "");
        second.setClusterId("B7");

        List<CombinedPass> selected = engine.clusterDatabankPasses(
                List.of(first, second), 0.10, 0.15, 1, 10);

        assertEquals(2, selected.size());
        assertEquals("B3", selected.get(0).getClusterId());
        assertEquals("B7", selected.get(1).getClusterId());
    }

    @Test
    public void passIdentityKeepsOverlappingPassNumbersOnDifferentClusters() {
        DatabankManager manager = new DatabankManager();
        CombinedPass first = pass(5, 10.0);
        first.setStrategyName("Same");
        first.setClusterId("B1");
        CombinedPass second = pass(5, 99.0);
        second.setStrategyName("Same");
        second.setClusterId("B2");
        manager.setDatabankContent("Results", List.of(first, second));

        List<CombinedPass> stored = manager.getDatabank("Results");
        assertEquals(2, stored.size());
        assertEquals("B1", stored.get(0).getClusterId());
        assertEquals("B2", stored.get(1).getClusterId());
    }

    @Test
    public void rebuildKeepsLineReferenceFloorAndDeadStatus() {
        CustomProject project = new CustomProject("P", "EA.ex5", "EURUSD", "H1");
        CombinedPass live = pass(1, 80.0);
        live.setClusterId("B1");
        CombinedPass deadMember = pass(2, 10.0);
        deadMember.setClusterId("B2");
        project.getDatabanks().put("g01_pick", List.of(live, deadMember));
        project.getDatabanks().put("g02_pick", List.of(live));
        WorkflowTask g01 = new WorkflowTask("g01", WorkflowTask.TaskType.OPTIMIZER);
        g01.setTargetDatabank("g01_pick");
        WorkflowTask g02 = new WorkflowTask("g02", WorkflowTask.TaskType.OPTIMIZER);
        g02.setTargetDatabank("g02_pick");
        project.addTask(g01);
        project.addTask(g02);

        ClusterCensus.ClusterLine b1 = new ClusterCensus.ClusterLine();
        b1.setClusterId("B1");
        b1.setLastReferenceRatio(6.0);
        b1.setLastReferenceProfit(200.0);
        ClusterCensus.ClusterLine b2 = new ClusterCensus.ClusterLine();
        b2.setClusterId("B2");
        b2.setStatus(ClusterCensus.ClusterStatus.DEAD);
        b2.setDiedAtStage("g02");
        ClusterCensus.ClusterStageSnapshot died = new ClusterCensus.ClusterStageSnapshot();
        died.setDatabankName("g02_pick");
        died.setVerdict(ClusterCensus.StageVerdict.DIED);
        b2.setPerStage(List.of(died));
        ClusterCensus previous = new ClusterCensus();
        previous.setClusters(List.of(b1, b2));
        project.setClusterCensus(previous);

        ClusterCensus rebuilt = ClusterCensus.rebuild(project);
        assertEquals(6.0, rebuilt.findLine("B1").getLastReferenceRatio(), 1e-9);
        assertEquals(ClusterCensus.ClusterStatus.DEAD, rebuilt.findLine("B2").getStatus());
        assertEquals("g02", rebuilt.findLine("B2").getDiedAtStage());
        assertEquals(ClusterCensus.StageVerdict.DIED, rebuilt.findLine("B2").getPerStage().stream()
                .filter(s -> "g02_pick".equals(s.getDatabankName()))
                .findFirst().orElseThrow().getVerdict());
    }

    @Test
    public void rebuildIgnoresEmptyLaterStagesAndMasterCopies() {
        CustomProject project = new CustomProject("Tick-Kill", "EA.ex5", "GBPJPY", "M5");
        CombinedPass b1 = pass(193, 80.0);
        b1.setClusterId("B1");
        CombinedPass b7 = pass(3463, 90.0);
        b7.setClusterId("B7");
        project.getDatabanks().put("g01_grid_pick", List.of(b1, b7));
        project.getDatabanks().put("g01_grid_master", List.of(b1));
        project.getDatabanks().put("g02_cadence_raw", List.of());
        WorkflowTask pick = new WorkflowTask("01 Grid", WorkflowTask.TaskType.DIVERSITY_FILTER);
        pick.setTargetDatabank("g01_grid_pick");
        WorkflowTask master = new WorkflowTask("01 Grid — Master-Referenz (OHLC 3J)",
                WorkflowTask.TaskType.MASTER_REFERENCE);
        master.setSourceDatabank("g01_grid_pick");
        master.setTargetDatabank("g01_grid_master");
        WorkflowTask g02 = new WorkflowTask("02 Order-Taktung — Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        g02.setTargetDatabank("g02_cadence_raw");
        project.addTask(pick);
        project.addTask(master);
        project.addTask(g02);

        ClusterCensus.ClusterLine poisoned = new ClusterCensus.ClusterLine();
        poisoned.setClusterId("B1");
        poisoned.setStatus(ClusterCensus.ClusterStatus.DEAD);
        poisoned.setDiedAtStage("02 Order-Taktung — Optimizer");
        ClusterCensus previous = new ClusterCensus();
        previous.setClusters(List.of(poisoned));
        project.setClusterCensus(previous);

        ClusterCensus rebuilt = ClusterCensus.rebuild(project);
        assertEquals(ClusterCensus.ClusterStatus.LIVE, rebuilt.findLine("B1").getStatus());
        assertEquals(ClusterCensus.ClusterStatus.LIVE, rebuilt.findLine("B7").getStatus());
        assertNull(rebuilt.findLine("B1").getDiedAtStage());
    }

    private static Pass diversePass(int passNumber, int trades) {
        Pass pass = new Pass();
        pass.setPassNumber(passNumber);
        pass.setProfit(100);
        pass.setTotalTrades(trades);
        pass.setRecoveryFactor(1.0);
        pass.setDrawdownPercent(10.0);
        pass.setParameter("entry", String.valueOf(passNumber * 10));
        return pass;
    }

    private static CombinedPass pass(int number, double profit) {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setPassNumber(number);
        bt.setProfit(profit);
        bt.setTotalTrades(100);
        bt.setProfitFactor(1.5);
        bt.setDrawdownPercent(10.0);
        bt.setRecoveryFactor(2.0);
        return new CombinedPass(bt, null, profit, 1.0, "test");
    }
}
