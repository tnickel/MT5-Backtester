package com.backtester.workflow;

import com.backtester.config.EaParameter;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.backtester.workflow.WorkflowHandoffAuditService.HandoffTransition;
import com.backtester.workflow.WorkflowHandoffAuditService.MatchStatus;
import com.backtester.workflow.WorkflowHandoffAuditService.ParamSource;
import com.backtester.workflow.WorkflowHandoffAuditService.ParameterTransfer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class WorkflowHandoffAuditServiceTest {

    @Test
    public void buildShowsForcedGateAndPassFixedAndTargets() {
        CustomProject project = new CustomProject("Flow", "EA", "EURUSD", "M5");

        WorkflowTask producer = new WorkflowTask("05 ADX — Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        producer.setTargetDatabank("g05_raw");
        producer.setOptimizerTargetParameters(List.of("Inp_Use_ADX_Filter", "Inp_ADX_Period"));

        WorkflowTask filter = new WorkflowTask("05 ADX — Filter", WorkflowTask.TaskType.PRE_FILTER);
        filter.setSourceDatabank("g05_raw");
        filter.setTargetDatabank("g05_pick");

        WorkflowTask consumer = new WorkflowTask("06 RSI — Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        consumer.setSourceDatabank("g05_pick");
        consumer.setTargetDatabank("g06_raw");
        consumer.setOptimizerTargetParameters(List.of("Inp_Use_RSI_Filter"));
        consumer.setOptimizerParameterBasisAdopted(true);
        consumer.setOptimizerParameterBasisPassNumber(3);
        consumer.setOptimizerParameterBasisDatabank("g05_pick");
        consumer.recordAdoptedFilterGate(
                "Inp_Use_ADX_Filter", "FILTER_ON_BETTER", "true", true, 80, 20,
                "Filter AN erzwungen");
        consumer.setOptimizerParameterSnapshot(List.of(
                param("Inp_Use_ADX_Filter", "true", false),
                param("Inp_ADX_Period", "14", false),
                param("Inp_Use_RSI_Filter", "false", true)));

        project.addTask(producer);
        project.addTask(filter);
        project.addTask(consumer);

        CombinedPass adopted = scoredPass(3, 88, Map.of(
                "Inp_Use_ADX_Filter", "false", // pass said false, force overwrote to true
                "Inp_ADX_Period", "14",
                "Inp_Use_RSI_Filter", "false"));
        project.getDatabanks().put("g05_pick", new ArrayList<>(List.of(adopted)));

        DatabankManager manager = new DatabankManager();
        manager.loadFromProject(project);

        List<HandoffTransition> handoffs = WorkflowHandoffAuditService.build(project, manager);
        assertEquals(2, handoffs.size());
        assertEquals(1, handoffs.get(0).getIndex());
        assertEquals(3, handoffs.get(1).getIndex());

        HandoffTransition handoff = handoffs.get(1);
        assertEquals("05 ADX — Optimizer", handoff.getFromTaskName());
        assertEquals("05 ADX — Filter", handoff.getViaTaskName());
        assertEquals("06 RSI — Optimizer", handoff.getToTaskName());
        assertTrue(handoff.isAdopted());
        assertTrue(handoff.isGateForced());
        assertEquals(0, handoff.getMismatchCount());

        ParameterTransfer gate = find(handoff, "Inp_Use_ADX_Filter");
        assertEquals(ParamSource.GATE_FORCED, gate.getSource());
        assertEquals("true", gate.getExpectedValue());
        assertEquals("true", gate.getActualValue());
        assertEquals(MatchStatus.OK, gate.getMatchStatus());
        assertFalse(gate.isOptimizeEnabled());

        ParameterTransfer period = find(handoff, "Inp_ADX_Period");
        assertEquals(ParamSource.PASS_FIXED, period.getSource());
        assertEquals("14", period.getActualValue());
        assertEquals(MatchStatus.OK, period.getMatchStatus());

        ParameterTransfer rsi = find(handoff, "Inp_Use_RSI_Filter");
        assertEquals(ParamSource.STAGE_TARGET, rsi.getSource());
        assertTrue(rsi.isOptimizeEnabled());
        assertEquals(MatchStatus.OK, rsi.getMatchStatus());
    }

    @Test
    public void mismatchWhenForcedValueNotInSnapshot() {
        CustomProject project = new CustomProject("Flow", "EA", "EURUSD", "M5");
        WorkflowTask producer = new WorkflowTask("A", WorkflowTask.TaskType.OPTIMIZER);
        producer.setOptimizerTargetParameters(List.of("Inp_Use_X"));
        WorkflowTask consumer = new WorkflowTask("B", WorkflowTask.TaskType.OPTIMIZER);
        consumer.setOptimizerTargetParameters(List.of("Foo"));
        consumer.setOptimizerParameterBasisAdopted(true);
        consumer.setOptimizerParameterBasisPassNumber(1);
        consumer.setOptimizerParameterBasisDatabank("db");
        consumer.recordAdoptedFilterGate("Inp_Use_X", "FILTER_ON_BETTER", "true", true, 1, 0, "forced");
        consumer.setOptimizerParameterSnapshot(List.of(
                param("Inp_Use_X", "false", false),
                param("Foo", "1", true)));
        project.addTask(producer);
        project.addTask(consumer);
        project.getDatabanks().put("db", new ArrayList<>(List.of(
                scoredPass(1, 10, Map.of("Inp_Use_X", "false", "Foo", "1")))));

        DatabankManager manager = new DatabankManager();
        manager.loadFromProject(project);

        assertTrue(consumer.isAdoptedFilterGateForced());
        HandoffTransition handoff = WorkflowHandoffAuditService.buildOne(2, producer, null, consumer, manager);
        assertTrue("Gate-Force Audit muss am Übergang hängen", handoff.isGateForced());
        assertEquals(MatchStatus.MISMATCH, find(handoff, "Inp_Use_X").getMatchStatus());
        assertTrue(handoff.getMismatchCount() >= 1);
    }

    @Test
    public void buildFindsHandoffsEvenWhenPersistedTargetsAreEmptyButGuidedNameResolves() {
        CustomProject project = new CustomProject("Guided", "EA", "AUDCAD", "M5");
        // Names must match ToTheMoon132 stage titles so factory can resolve targets.
        WorkflowTask first = new WorkflowTask("01 Grid-Fundament — Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        WorkflowTask filter = new WorkflowTask("01 Grid-Fundament — Trade/Qualitätsfilter", WorkflowTask.TaskType.PRE_FILTER);
        WorkflowTask second = new WorkflowTask("02 Order-Taktung — Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        // Intentionally no setOptimizerTargetParameters — mimics older/persisted guided projects.
        project.addTask(first);
        project.addTask(filter);
        project.addTask(second);

        List<HandoffTransition> handoffs = WorkflowHandoffAuditService.build(project, new DatabankManager());
        assertEquals(2, handoffs.size());
        assertTrue(handoffs.get(0).isRootStage());
        assertEquals(1, handoffs.get(0).getIndex());
        assertEquals("01 Grid-Fundament — Optimizer", handoffs.get(0).getToTaskName());
        assertEquals(3, handoffs.get(1).getIndex()); // Filter is workflow tile #2
        assertEquals("01 Grid-Fundament — Optimizer", handoffs.get(1).getFromTaskName());
        assertEquals("02 Order-Taktung — Optimizer", handoffs.get(1).getToTaskName());

        List<WorkflowHandoffAuditService.FlowNode> timeline =
                WorkflowHandoffAuditService.buildTimeline(project, new DatabankManager());
        assertEquals(3, timeline.size());
        assertEquals(1, timeline.get(0).getWorkflowNumber());
        assertEquals(2, timeline.get(1).getWorkflowNumber());
        assertEquals(3, timeline.get(2).getWorkflowNumber());
        assertTrue(timeline.get(0).hasHandoff());
        assertFalse(timeline.get(1).hasHandoff());
        assertTrue(timeline.get(2).hasHandoff());
        assertFalse("Guided targets should resolve from stage name",
                handoffs.get(1).getFromTargets().isEmpty());
        assertFalse(handoffs.get(1).getToTargets().isEmpty());
    }

    @Test
    public void buildDoesNotInventPassIdentityBeforeAdoption() {
        CustomProject project = new CustomProject("Flow", "EA", "EURUSD", "M5");
        WorkflowTask producer = new WorkflowTask("05 ADX — Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        producer.setTargetDatabank("g05_raw");
        producer.setOptimizerTargetParameters(List.of("Inp_Use_ADX_Filter"));

        WorkflowTask consumer = new WorkflowTask("06 RSI — Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        consumer.setSourceDatabank("g05_pick");
        consumer.setTargetDatabank("g06_raw");
        consumer.setOptimizerTargetParameters(List.of("Inp_Use_RSI_Filter"));
        consumer.setOptimizerParameterBasisAdopted(false);
        consumer.setOptimizerParameterSnapshot(List.of(
                param("Inp_Use_ADX_Filter", "false", false),
                param("Inp_Use_RSI_Filter", "false", true)));

        project.addTask(producer);
        project.addTask(consumer);

        CombinedPass candidate = scoredPass(99, 77, Map.of("Inp_Use_ADX_Filter", "true"));
        project.getDatabanks().put("g05_pick", new ArrayList<>(List.of(candidate)));
        DatabankManager manager = new DatabankManager();
        manager.loadFromProject(project);

        HandoffTransition handoff = WorkflowHandoffAuditService.build(project, manager).get(1);
        assertFalse(handoff.isAdopted());
        assertEquals(-1, handoff.getPassNumber());
        assertTrue(handoff.getDatabank() == null || handoff.getDatabank().isBlank());
    }

    @Test
    public void adoptedButMissingPassIsNotFalselyVerified() {
        CustomProject project = new CustomProject("Flow", "EA", "EURUSD", "M5");
        WorkflowTask producer = new WorkflowTask("05 ADX — Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        producer.setTargetDatabank("g05_raw");
        producer.setOptimizerTargetParameters(List.of("Inp_Use_ADX_Filter", "Inp_ADX_Period"));

        WorkflowTask consumer = new WorkflowTask("06 RSI — Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        consumer.setSourceDatabank("g05_pick");
        consumer.setTargetDatabank("g06_raw");
        consumer.setOptimizerTargetParameters(List.of("Inp_Use_RSI_Filter"));
        consumer.setOptimizerParameterBasisAdopted(true);
        consumer.setOptimizerParameterBasisPassNumber(3);
        consumer.setOptimizerParameterBasisDatabank("g05_pick");
        consumer.setOptimizerParameterSnapshot(List.of(
                param("Inp_Use_ADX_Filter", "true", false),
                param("Inp_ADX_Period", "14", false),
                param("Inp_Use_RSI_Filter", "false", true)));

        project.addTask(producer);
        project.addTask(consumer);
        // Databank empty — adopted pass gone
        project.getDatabanks().put("g05_pick", new ArrayList<>());
        DatabankManager manager = new DatabankManager();
        manager.loadFromProject(project);

        HandoffTransition handoff = WorkflowHandoffAuditService.build(project, manager).get(1);
        assertTrue(handoff.isAdopted());
        assertTrue(handoff.getMismatchCount() + handoff.getPendingCount() > 0
                || handoff.getOkCount() == 0
                || handoff.getProofHeadline().contains("ABWEICHUNG")
                || handoff.getProofHeadline().toLowerCase().contains("pass"));
        assertFalse(handoff.getProofHeadline().startsWith("VERIFIZIERT"));

        ParameterTransfer adx = find(handoff, "Inp_ADX_Period");
        assertEquals(MatchStatus.MISSING, adx.getMatchStatus());
    }

    @Test
    public void resolveForcedValueForGateMapsPositionalMultiGateValues() {
        assertEquals("false", FilterGateAnalysisService.resolveForcedValueForGate(
                "Inp_Use_Vol_Filter, Inp_Use_Correlation_Filter", "true, false",
                "Inp_Use_Correlation_Filter"));
        assertEquals("true", FilterGateAnalysisService.resolveForcedValueForGate(
                "Inp_Use_Vol_Filter, Inp_Use_Correlation_Filter", "true, false",
                "Inp_Use_Vol_Filter"));
    }

    private static ParameterTransfer find(HandoffTransition handoff, String name) {
        return handoff.getTransfers().stream()
                .filter(t -> name.equalsIgnoreCase(t.getName()))
                .findFirst()
                .orElseThrow();
    }

    private static EaParameter param(String name, String value, boolean optimize) {
        EaParameter p = new EaParameter();
        p.setName(name);
        p.setValue(value);
        p.setOptimizeEnabled(optimize);
        p.setOptimizeStart("0");
        p.setOptimizeStep("1");
        p.setOptimizeEnd("1");
        return p;
    }

    private static CombinedPass scoredPass(int passNumber, double score, Map<String, String> params) {
        Pass bt = new Pass();
        bt.setPassNumber(passNumber);
        bt.setProfit(score * 10);
        bt.setTotalTrades(100);
        bt.setDrawdownPercent(10);
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                bt.setParameter(e.getKey(), e.getValue());
            }
        }
        return new CombinedPass(bt, null, score, 1.0, "test");
    }
}
