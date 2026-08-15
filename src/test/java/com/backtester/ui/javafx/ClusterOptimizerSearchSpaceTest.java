package com.backtester.ui.javafx;

import com.backtester.config.EaParameter;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.backtester.workflow.ClusterAutomation;
import com.backtester.workflow.ClusterCensus;
import com.backtester.workflow.CustomProject;
import com.backtester.workflow.GuidedOptimizationService;
import com.backtester.workflow.MasterSearchSpaceValidator;
import com.backtester.workflow.MasterStrategyLineageService;
import com.backtester.workflow.WorkflowTask;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClusterOptimizerSearchSpaceTest {

    @Test
    public void twoLiveClustersDoNotAbortStage02WhenEachSetMatchesItsOwnBasis() {
        CustomProject project = new CustomProject("Tick-Kill", "EA.ex5", "GBPJPY", "M5");
        project.setAutomaticModeEnabled(true);

        WorkflowTask g01 = new WorkflowTask("01 Grid", WorkflowTask.TaskType.OPTIMIZER);
        g01.setTargetDatabank("g01_grid_pick");
        g01.setOptimizerTargetParameters(List.of("Inp_Grid_Step"));
        g01.setOptimizerParameterSnapshot(List.of(numeric("Inp_Grid_Step", "725", "500", "25", "900")));

        WorkflowTask g02 = new WorkflowTask("02 Order-Taktung", WorkflowTask.TaskType.OPTIMIZER);
        g02.setSourceDatabank("g01_grid_pick");
        g02.setTargetDatabank("g02_taktung_pick");
        g02.setOptimizerTargetParameters(List.of("Inp_Order_Delay"));
        g02.setOptimizerParameterSnapshot(basis("725", "1proz_Pass11429", "5"));
        project.addTask(g01);
        project.addTask(g02);

        CombinedPass b1 = clustered(346, 40.0, "B1", "725", "1proz_Pass11429");
        CombinedPass b2Dead = clustered(200, 10.0, "B2", "600", "dead_Pass");
        CombinedPass b7 = clustered(3463, 50.0, "B7", "875", "18proz_Pass3463");

        ClusterCensus census = new ClusterCensus();
        ClusterAutomation.markImproved(census, "B1", "g01_grid_master", b1, null);
        ClusterAutomation.markDied(census, "B2", "g01_grid_master", "g01_grid_master");
        ClusterAutomation.markImproved(census, "B7", "g01_grid_master", b7, null);
        project.setClusterCensus(census);
        project.setProvenMasterParameters(basis("875", "18proz_Pass3463", "5"));
        project.setProvenMasterContextKey(MasterStrategyLineageService.currentContextKey(project));

        List<CombinedPass> source = List.of(b1, b2Dead, b7);
        assertEquals(2, ClusterAutomation.liveChampions(project, source).size());
        assertTrue(ProjectWorkflowPipelineRunner.shouldDeferRuntimeSearchSpaceCheck(
                project, g02, source));

        List<EaParameter> leftoverB1Snapshot = g02.getOptimizerParameterSnapshot();
        List<EaParameter> globalB7 = project.getProvenMasterParameters();
        assertFalse("Global leftover SET must not be compared to another line",
                MasterSearchSpaceValidator.validateRuntimeTask(g02, globalB7, "M5").isEmpty());
        assertEquals("725", leftoverB1Snapshot.get(0).getValue());

        adoptAndAssertOwnBasis(project, g02, b1, "725", "1proz_Pass11429");
        adoptAndAssertOwnBasis(project, g02, b7, "875", "18proz_Pass3463");
        assertEquals("875", find(g02.getOptimizerParameterSnapshot(), "Inp_Grid_Step").getValue());
        assertEquals("18proz_Pass3463",
                find(g02.getOptimizerParameterSnapshot(), "Inp_Order_Comment").getValue());
    }

    @Test
    public void unclusteredFollowUpStillValidatesAgainstTheSingleMaster() {
        CustomProject project = new CustomProject("Classic", "EA.ex5", "GBPJPY", "M5");
        project.setAutomaticModeEnabled(true);
        WorkflowTask g02 = new WorkflowTask("02 Order-Taktung", WorkflowTask.TaskType.OPTIMIZER);
        g02.setOptimizerTargetParameters(List.of("Inp_Order_Delay"));
        g02.setOptimizerParameterSnapshot(basis("725", "1proz_Pass11429", "5"));
        project.addTask(g02);

        CombinedPass champion = clustered(1, 10.0, null, "725", "1proz_Pass11429");
        assertFalse(ProjectWorkflowPipelineRunner.shouldDeferRuntimeSearchSpaceCheck(
                project, g02, List.of(champion)));
        assertTrue(MasterSearchSpaceValidator.validateRuntimeTask(
                g02, basis("725", "1proz_Pass11429", "5"), "M5").isEmpty());
    }

    private static void adoptAndAssertOwnBasis(CustomProject project,
                                               WorkflowTask g02,
                                               CombinedPass champion,
                                               String gridStep,
                                               String comment) {
        List<EaParameter> championSet = basis(gridStep, comment, "5");
        GuidedOptimizationService.AdoptionResult result = GuidedOptimizationService.adoptPassParameters(
                project, g02.getOptimizerParameterSnapshot(), championSet, champion, "g01_grid_pick");
        assertEquals(g02, result.getNextOptimizer());
        assertTrue(MasterSearchSpaceValidator.validateRuntimeTask(
                g02, result.getParameters(), "M5").isEmpty());
        assertEquals(gridStep, find(g02.getOptimizerParameterSnapshot(), "Inp_Grid_Step").getValue());
        assertEquals(comment, find(g02.getOptimizerParameterSnapshot(), "Inp_Order_Comment").getValue());
        assertTrue(MasterSearchSpaceValidator.validateRuntimeTask(
                g02, championSet, "M5").isEmpty());
    }

    private static CombinedPass clustered(int number, double score, String clusterId,
                                          String gridStep, String comment) {
        Pass bt = new Pass();
        bt.setPassNumber(number);
        bt.setProfit(score);
        bt.setTotalTrades(10);
        bt.setRecoveryFactor(1.0);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("Inp_Grid_Step", gridStep);
        values.put("Inp_Order_Comment", comment);
        values.put("Inp_Order_Delay", "5");
        bt.setParameterValues(values);
        CombinedPass pass = new CombinedPass(bt, null, score, 1.0, "t");
        pass.setClusterId(clusterId);
        return pass;
    }

    private static List<EaParameter> basis(String gridStep, String comment, String delay) {
        List<EaParameter> parameters = new ArrayList<>();
        parameters.add(numeric("Inp_Grid_Step", gridStep, gridStep, "1", gridStep));
        EaParameter orderComment = new EaParameter("Inp_Order_Comment", comment);
        orderComment.setStringType(true);
        orderComment.setOptimizeEnabled(false);
        parameters.add(orderComment);
        parameters.add(numeric("Inp_Order_Delay", delay, "1", "1", "20"));
        parameters.get(2).setOptimizeEnabled(true);
        return parameters;
    }

    private static EaParameter numeric(String name, String value,
                                       String start, String step, String end) {
        EaParameter parameter = new EaParameter(name, value);
        parameter.setOptimizeStart(start);
        parameter.setOptimizeStep(step);
        parameter.setOptimizeEnd(end);
        parameter.setStringType(false);
        parameter.setOptimizeEnabled(false);
        return parameter;
    }

    private static EaParameter find(List<EaParameter> parameters, String name) {
        return parameters.stream()
                .filter(parameter -> name.equals(parameter.getName()))
                .findFirst()
                .orElseThrow();
    }
}
