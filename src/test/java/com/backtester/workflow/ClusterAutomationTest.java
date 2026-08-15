package com.backtester.workflow;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.backtester.ui.javafx.ProjectWorkflowPipelineRunner;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ClusterAutomationTest {

    @Test
    public void championsByClusterPicksScoreLeaderInsideEachLine() {
        CombinedPass b1Weak = clustered(1, 10.0, "B1");
        CombinedPass b1Best = clustered(2, 40.0, "B1");
        CombinedPass b2 = clustered(3, 99.0, "B2");

        List<CombinedPass> champions = ClusterAutomation.championsByCluster(
                List.of(b1Weak, b2, b1Best));

        assertEquals(2, champions.size());
        assertSame(b1Best, champions.get(0));
        assertSame(b2, champions.get(1));
        assertEquals(champions, ProjectWorkflowPipelineRunner.championsByCluster(
                List.of(b1Weak, b2, b1Best)));
    }

    @Test
    public void unclusteredPassesYieldEmptyClusterChampions() {
        assertTrue(ClusterAutomation.championsByCluster(List.of(clustered(1, 50.0, null))).isEmpty());
        assertFalse(ClusterAutomation.hasAnyClusterId(List.of(clustered(1, 50.0, null))));
    }

    @Test
    public void liveChampionsDropDeadLinesAndKeepUnlistedAsLive() {
        CombinedPass b1 = clustered(1, 10.0, "B1");
        CombinedPass b2 = clustered(2, 20.0, "B2");
        CombinedPass b3 = clustered(3, 5.0, "B3");

        CustomProject project = new CustomProject("P", "EA", "EURUSD", "M5");
        ClusterCensus.ClusterLine dead = new ClusterCensus.ClusterLine();
        dead.setClusterId("B2");
        dead.setStatus(ClusterCensus.ClusterStatus.DEAD);
        ClusterCensus census = new ClusterCensus();
        census.setClusters(List.of(dead));
        project.setClusterCensus(census);

        List<CombinedPass> live = ClusterAutomation.liveChampions(project, List.of(b1, b2, b3));
        assertEquals(2, live.size());
        assertEquals("B1", live.get(0).getClusterId());
        assertEquals("B3", live.get(1).getClusterId());
    }

    @Test
    public void sequentialOptimizerOnlyWhenAutomaticAndAtLeastTwoLive() {
        CombinedPass b1 = clustered(1, 10.0, "B1");
        CombinedPass b2 = clustered(2, 20.0, "B2");
        CustomProject project = new CustomProject("P", "EA", "EURUSD", "M5");
        project.setAutomaticModeEnabled(false);
        assertFalse(ClusterAutomation.shouldRunSequentialClusterOptimizers(project, List.of(b1, b2)));

        project.setAutomaticModeEnabled(true);
        assertTrue(ClusterAutomation.shouldRunSequentialClusterOptimizers(project, List.of(b1, b2)));
        assertFalse(ClusterAutomation.shouldRunSequentialClusterOptimizers(project, List.of(b1)));
    }

    @Test
    public void zeroLiveClustersWhenEveryChampionIsDead() {
        CombinedPass b1 = clustered(1, 10.0, "B1");
        CustomProject project = new CustomProject("P", "EA", "EURUSD", "M5");
        ClusterCensus.ClusterLine dead = new ClusterCensus.ClusterLine();
        dead.setClusterId("B1");
        dead.setStatus(ClusterCensus.ClusterStatus.DEAD);
        ClusterCensus census = new ClusterCensus();
        census.setClusters(List.of(dead));
        project.setClusterCensus(census);

        assertTrue(ClusterAutomation.hasZeroLiveClusters(project, List.of(b1)));
        assertTrue(ClusterAutomation.zeroLiveClustersMessage(new WorkflowTask("g05", WorkflowTask.TaskType.OPTIMIZER))
                .contains("Keine lebende Cluster-Linie"));
    }

    @Test
    public void lineImprovementUsesLastReferenceRatioNotGlobalScore() {
        ClusterCensus.ClusterLine line = new ClusterCensus.ClusterLine();
        line.setClusterId("B3");
        line.setLastReferenceRatio(4.0);

        MasterStrategyEntry better = rated(MasterStrategyEntry.Verdict.SCHLECHTER, 4);
        better.setReturnToDrawdown(4.5);
        assertTrue(ClusterAutomation.confirmsLineImprovement(better, line, true));

        MasterStrategyEntry worse = rated(MasterStrategyEntry.Verdict.BESSER, 4);
        worse.setReturnToDrawdown(3.9);
        assertFalse(ClusterAutomation.confirmsLineImprovement(worse, line, true));
    }

    @Test
    public void firstLineMeasurementFallsBackToConfirmsImprovement() {
        MasterStrategyEntry first = rated(MasterStrategyEntry.Verdict.UNBEKANNT, -1);
        first.setReturnToDrawdown(2.0);
        ClusterCensus.ClusterLine fresh = new ClusterCensus.ClusterLine();
        fresh.setClusterId("B1");
        assertTrue(ClusterAutomation.confirmsLineImprovement(first, fresh, false));
        assertFalse(ClusterAutomation.confirmsLineImprovement(first, fresh, true));
    }

    @Test
    public void markMeasuredKeepsFiniteFloorWhenMeasurementFailed() {
        ClusterCensus census = new ClusterCensus();
        CombinedPass champion = clustered(8, 12.0, "B1");
        MasterStrategyEntry good = rated(MasterStrategyEntry.Verdict.BESSER, 1);
        good.setReturnToDrawdown(5.5);
        good.setProfit(120.0);
        ClusterAutomation.markMeasured(census, "B1", "g01_grid_master", champion, good);
        assertEquals(5.5, census.findLine("B1").getLastReferenceRatio(), 1e-9);

        MasterStrategyEntry failed = rated(MasterStrategyEntry.Verdict.UNBEKANNT, -1);
        failed.setBacktestSucceeded(false);
        failed.setReturnToDrawdown(Double.NaN);
        failed.setProfit(Double.NaN);
        ClusterAutomation.markMeasured(census, "B1", "g01_grid_master", champion, failed);

        assertEquals(5.5, census.findLine("B1").getLastReferenceRatio(), 1e-9);
        assertEquals(120.0, census.findLine("B1").getLastReferenceProfit(), 1e-9);
        assertEquals(ClusterCensus.ClusterStatus.LIVE, census.findLine("B1").getStatus());
    }

    @Test
    public void markDiedAndImprovedStampCensusWithoutTouchingOtherLines() {
        ClusterCensus census = new ClusterCensus();
        CombinedPass b1 = clustered(8, 12.0, "B1");
        MasterStrategyEntry entry = rated(MasterStrategyEntry.Verdict.BESSER, 1);
        entry.setProfit(120.0);
        entry.setReturnToDrawdown(5.5);

        ClusterAutomation.markImproved(census, "B1", "g05_pick", b1, entry);
        ClusterAutomation.markDied(census, "B2", "g05 Master", "g05_pick");

        ClusterCensus.ClusterLine live = census.findLine("B1");
        assertEquals(ClusterCensus.ClusterStatus.LIVE, live.getStatus());
        assertEquals(ClusterCensus.StageVerdict.IMPROVED, live.getPerStage().get(0).getVerdict());
        assertEquals(5.5, live.getLastReferenceRatio(), 1e-9);
        assertEquals(120.0, live.getLastReferenceProfit(), 1e-9);

        ClusterCensus.ClusterLine dead = census.findLine("B2");
        assertEquals(ClusterCensus.ClusterStatus.DEAD, dead.getStatus());
        assertEquals("g05 Master", dead.getDiedAtStage());
        assertEquals(0, dead.getPerStage().get(0).getLiveCount());
        assertEquals(ClusterCensus.StageVerdict.DIED, dead.getPerStage().get(0).getVerdict());
    }

    @Test
    public void masterReferenceMeasurementDoesNotKillLiveLines() {
        CombinedPass b1 = clustered(193, 40.0, "B1");
        CombinedPass b7 = clustered(3463, 50.0, "B7");
        CustomProject project = new CustomProject("Tick-Kill", "EA.ex5", "GBPJPY", "M5");
        project.setAutomaticModeEnabled(true);

        ClusterCensus census = new ClusterCensus();
        ClusterAutomation.markImproved(census, "B1", "g01_grid_pick", b1, null);
        ClusterAutomation.markImproved(census, "B7", "g01_grid_pick", b7, null);
        MasterStrategyEntry worse = rated(MasterStrategyEntry.Verdict.SCHLECHTER, 1);
        worse.setReturnToDrawdown(0.5);
        ClusterAutomation.markMeasured(census, "B1", "g01_grid_master", b1, worse);
        ClusterAutomation.markMeasured(census, "B7", "g01_grid_master", b7, worse);
        project.setClusterCensus(census);
        project.getDatabanks().put("g01_grid_pick", List.of(b1, b7));
        project.getDatabanks().put("g01_grid_master", List.of(b1, b7));
        project.getDatabanks().put("g02_cadence_raw", List.of());
        WorkflowTask g02 = new WorkflowTask("02 Order-Taktung — Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        g02.setSourceDatabank("g01_grid_pick");
        g02.setTargetDatabank("g02_cadence_raw");
        project.addTask(g02);

        List<CombinedPass> live = ClusterAutomation.liveChampions(project, List.of(b1, b7));
        assertEquals(2, live.size());
        assertFalse(ClusterAutomation.hasZeroLiveClusters(project, List.of(b1, b7)));
        assertTrue(ClusterAutomation.shouldRunSequentialClusterOptimizers(project, List.of(b1, b7)));
        assertEquals(ClusterCensus.ClusterStatus.LIVE, census.findLine("B1").getStatus());
        assertEquals(ClusterCensus.ClusterStatus.LIVE, census.findLine("B7").getStatus());
    }

    @Test
    public void poisonedMasterRefCensusRevivesFromPickDatabank() {
        CombinedPass b1 = clustered(193, 40.0, "B1");
        CombinedPass b7 = clustered(3463, 50.0, "B7");
        CustomProject project = new CustomProject("Tick-Kill", "EA.ex5", "GBPJPY", "M5");
        ClusterCensus census = new ClusterCensus();
        ClusterAutomation.markDied(census, "B1", "01 Grid-Fundament — Master-Referenz (OHLC 3J)",
                "g01_grid_master");
        ClusterAutomation.markDied(census, "B7", "02 Order-Taktung — Optimizer", "g02_cadence_raw");
        project.setClusterCensus(census);
        project.getDatabanks().put("g01_grid_pick", List.of(b1, b7));
        WorkflowTask g01 = new WorkflowTask("01 Grid", WorkflowTask.TaskType.DIVERSITY_FILTER);
        g01.setTargetDatabank("g01_grid_pick");
        project.addTask(g01);
        WorkflowTask later = new WorkflowTask("02 Order-Taktung — Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        later.setTargetDatabank("g02_cadence_raw");
        project.addTask(later);

        assertEquals(2, ClusterAutomation.liveChampions(project, List.of(b1, b7)).size());
        ClusterCensus rebuilt = ClusterCensus.rebuild(project);
        assertEquals(ClusterCensus.ClusterStatus.LIVE, rebuilt.findLine("B1").getStatus());
        assertEquals(ClusterCensus.ClusterStatus.LIVE, rebuilt.findLine("B7").getStatus());
    }

    @Test
    public void sequentialLineFailureDoesNotDiscardSiblingResults() {
        ClusterCensus census = new ClusterCensus();
        CombinedPass b1Prev = clustered(1, 40.0, "B1");
        CombinedPass b4Prev = clustered(4, 30.0, "B4");
        CombinedPass b1New = clustered(11, 45.0, "B1");

        ClusterAutomation.markDied(census, "B4", "02 Optimizer", "g02_cadence_raw");
        List<CombinedPass> kept = ClusterAutomation.applyOptimizerImproveOrDie(
                census, "02 Optimizer", "g02_cadence_raw",
                List.of(b1Prev, b4Prev), List.of(b1New));

        assertEquals(1, ClusterAutomation.championsByCluster(kept).size());
        assertEquals("B1", kept.get(0).getClusterId());
        assertEquals(11, kept.get(0).getPassNumber());
        assertEquals(ClusterCensus.ClusterStatus.LIVE, census.findLine("B1").getStatus());
        assertEquals(ClusterCensus.ClusterStatus.DEAD, census.findLine("B4").getStatus());
    }

    @Test
    public void optimizerImproveOrDieComparesToPreviousChampionOnly() {
        ClusterCensus census = new ClusterCensus();
        CombinedPass b1Prev = clustered(1, 40.0, "B1");
        CombinedPass b7Prev = clustered(2, 50.0, "B7");
        CombinedPass b1Better = clustered(11, 45.0, "B1");
        CombinedPass b7Worse = clustered(22, 49.0, "B7");

        List<CombinedPass> kept = ClusterAutomation.applyOptimizerImproveOrDie(
                census, "02 Optimizer", "g02_cadence_raw",
                List.of(b1Prev, b7Prev), List.of(b1Better, b7Worse));

        assertEquals(1, ClusterAutomation.championsByCluster(kept).size());
        assertEquals("B1", kept.get(0).getClusterId());
        assertEquals(ClusterCensus.ClusterStatus.LIVE, census.findLine("B1").getStatus());
        assertEquals(ClusterCensus.ClusterStatus.DEAD, census.findLine("B7").getStatus());
    }

    @Test
    public void stampFixedClusterIdDoesNotCrossOntoAnotherId() {
        CombinedPass a = clustered(1, 1.0, "B1");
        CombinedPass b = clustered(2, 1.0, "B9");
        ClusterAutomation.stampFixedClusterId(List.of(a, b), "B4");
        assertEquals("B4", a.getClusterId());
        assertEquals("B4", b.getClusterId());
    }

    private static CombinedPass clustered(int number, double score, String clusterId) {
        Pass bt = new Pass();
        bt.setPassNumber(number);
        bt.setProfit(score);
        bt.setTotalTrades(10);
        bt.setRecoveryFactor(1.0);
        CombinedPass pass = new CombinedPass(bt, null, score, 1.0, "t");
        pass.setClusterId(clusterId);
        return pass;
    }

    private static MasterStrategyEntry rated(MasterStrategyEntry.Verdict verdict, int comparedTo) {
        MasterStrategyEntry entry = new MasterStrategyEntry();
        entry.setBacktestSucceeded(true);
        entry.setVerdict(verdict);
        entry.setComparedToSequence(comparedTo);
        entry.setReturnToDrawdown(2.5);
        return entry;
    }
}
