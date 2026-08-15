package com.backtester.workflow;

import com.backtester.report.OptimizationResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.workflow.ClusterCensus.ClusterLine;
import com.backtester.workflow.ClusterCensus.ClusterStageSnapshot;
import com.backtester.workflow.ClusterCensus.ClusterStatus;
import com.backtester.workflow.ClusterCensus.StageVerdict;
import com.backtester.workflow.WorkflowHandoffAuditService.FlowNode;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ClusterFlowTreeModelTest {

    @Test
    public void emptyCensusFallsBackToLinearSummary() {
        CustomProject project = new CustomProject("Old", "EA", "EURUSD", "M5");
        WorkflowTask task = new WorkflowTask("01 Grid", WorkflowTask.TaskType.OPTIMIZER);
        task.setTargetDatabank("g01_grid_pick");
        project.addTask(task);

        List<FlowNode> nodes = WorkflowHandoffAuditService.buildTimeline(project, new DatabankManager());
        ClusterFlowTreeModel model = ClusterFlowTreeModel.from(project, nodes);

        assertFalse(model.hasTree());
        assertTrue(model.getColumns().isEmpty());
        assertTrue(model.getRows().isEmpty());
        assertFalse(nodes.isEmpty());
        List<WorkflowFlowSummaryService.FlowStepSummary> steps = WorkflowFlowSummaryService.build(
                project, new DatabankManager(), t -> "");
        assertEquals(1, steps.size());
    }

    @Test
    public void deadClusterStaysVisibleWithZeroCount() {
        CustomProject project = new CustomProject("Lines", "EA", "GBPJPY", "M5");
        WorkflowTask g01 = new WorkflowTask("01 Grid", WorkflowTask.TaskType.OPTIMIZER);
        g01.setTargetDatabank("g01_grid_pick");
        WorkflowTask g02 = new WorkflowTask("02 Taktung", WorkflowTask.TaskType.OPTIMIZER);
        g02.setSourceDatabank("g01_grid_pick");
        g02.setTargetDatabank("g02_taktung_pick");
        project.addTask(g01);
        project.addTask(g02);

        ClusterLine b1 = line("B1", ClusterStatus.LIVE,
                snapshot("g01_grid_pick", 3, StageVerdict.PENDING, 11),
                snapshot("g02_taktung_pick", 3, StageVerdict.IMPROVED, 11));
        ClusterLine b3 = line("B3", ClusterStatus.DEAD,
                snapshot("g01_grid_pick", 1, StageVerdict.PENDING, 4),
                snapshot("g02_taktung_pick", 0, StageVerdict.DIED, -1));
        ClusterCensus census = new ClusterCensus();
        census.setClusters(List.of(b1, b3));
        project.setClusterCensus(census);

        List<FlowNode> nodes = WorkflowHandoffAuditService.buildTimeline(project, new DatabankManager());
        ClusterFlowTreeModel model = ClusterFlowTreeModel.from(project, nodes);

        assertTrue(model.hasTree());
        assertEquals(2, model.getColumns().size());
        assertEquals("B1", model.getColumns().get(0).getClusterId());
        assertEquals("B3", model.getColumns().get(1).getClusterId());
        assertTrue(model.getColumns().get(1).isDead());
        assertEquals(2, model.getRows().size());

        ClusterFlowTreeModel.Cell g01b1 = model.cell(0, "B1");
        assertNotNull(g01b1);
        assertEquals(3, g01b1.getLiveCount());
        assertEquals("3", g01b1.cellLabel());

        ClusterFlowTreeModel.Cell g02b3 = model.cell(1, "B3");
        assertNotNull(g02b3);
        assertEquals(0, g02b3.getLiveCount());
        assertEquals("0 ✕", g02b3.cellLabel());
        assertTrue(g02b3.greyed());
        assertEquals("B3 · g02 · 0", g02b3.fullLabel());

        ClusterFlowTreeModel.Cell g02b1 = model.cell(1, "B1");
        assertEquals("3 ▲", g02b1.cellLabel());
        assertEquals(11, g02b1.getChampionPassNumber());
        assertTrue(g02b1.hoverText().contains("#11"));
    }

    @Test
    public void clusterCellIsGalleryTrunkIsHandoff() {
        assertEquals(ClusterFlowTreeModel.ClickAction.GALLERY, ClusterFlowTreeModel.resolveClick(true));
        assertEquals(ClusterFlowTreeModel.ClickAction.HANDOFF, ClusterFlowTreeModel.resolveClick(false));
    }

    @Test
    public void filterByClusterKeepsOnlyMatchingLine() {
        CombinedPass b1a = pass(1, 10.0, "B1");
        CombinedPass b2 = pass(2, 20.0, "B2");
        CombinedPass b1b = pass(3, 30.0, "B1");
        List<CombinedPass> bank = List.of(b1a, b2, b1b);

        List<CombinedPass> onlyB1 = ClusterFlowTreeModel.filterByCluster(bank, "B1");
        assertEquals(2, onlyB1.size());
        assertEquals(1, onlyB1.get(0).getPassNumber());
        assertEquals(3, onlyB1.get(1).getPassNumber());
        assertSame(b1a, onlyB1.get(0));
        assertEquals(3, bank.size());

        List<CombinedPass> onlyB2 = ClusterFlowTreeModel.filterByCluster(bank, "b2");
        assertEquals(1, onlyB2.size());
        assertEquals(2, onlyB2.get(0).getPassNumber());

        assertTrue(ClusterFlowTreeModel.filterByCluster(bank, "B9").isEmpty());
        assertTrue(ClusterFlowTreeModel.filterByCluster(bank, null).isEmpty());
        assertTrue(ClusterFlowTreeModel.filterByCluster(null, "B1").isEmpty());
    }

    @Test
    public void pickDatabankSkipsRaw() {
        ClusterFlowTreeModel.Row pick = new ClusterFlowTreeModel.Row(
                1, "01 Grid", "g01", "g01_grid_raw", "g01_grid_pick", List.of());
        assertEquals("g01_grid_pick", ClusterFlowTreeModel.pickDatabankName(pick));

        ClusterFlowTreeModel.Row onlyRaw = new ClusterFlowTreeModel.Row(
                1, "01 Grid", "g01", "g01_grid_raw", "g01_grid_raw", List.of());
        assertEquals("", ClusterFlowTreeModel.pickDatabankName(onlyRaw));

        ClusterFlowTreeModel.Row sourcePick = new ClusterFlowTreeModel.Row(
                2, "02", "g02", "g01_grid_pick", "g02_taktung_raw", List.of());
        assertEquals("g01_grid_pick", ClusterFlowTreeModel.pickDatabankName(sourcePick));
    }

    @Test
    public void nullProjectDoesNotCrash() {
        ClusterFlowTreeModel model = ClusterFlowTreeModel.from(null, null);
        assertFalse(model.hasTree());
    }

    private static CombinedPass pass(int number, double profit, String clusterId) {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setPassNumber(number);
        bt.setProfit(profit);
        CombinedPass combined = new CombinedPass(bt, null, profit, 1.0, "test");
        combined.setClusterId(clusterId);
        return combined;
    }

    private static ClusterLine line(String id, ClusterStatus status, ClusterStageSnapshot... stages) {
        ClusterLine line = new ClusterLine();
        line.setClusterId(id);
        line.setStatus(status);
        line.setPerStage(List.of(stages));
        return line;
    }

    private static ClusterStageSnapshot snapshot(String databank, int live, StageVerdict verdict, int champion) {
        ClusterStageSnapshot snapshot = new ClusterStageSnapshot();
        snapshot.setDatabankName(databank);
        snapshot.setLiveCount(live);
        snapshot.setVerdict(verdict);
        snapshot.setChampionPassNumber(champion);
        return snapshot;
    }
}
