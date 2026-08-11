package com.backtester.workflow;

import com.backtester.config.EaParameter;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.backtester.workflow.WorkflowFlowSummaryService.FlowStepSummary;
import com.backtester.workflow.WorkflowFlowSummaryService.ProofStatus;
import com.backtester.workflow.WorkflowFlowSummaryService.SetfileProof;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class WorkflowFlowSummaryServiceTest {

    @Test
    public void buildNarratesOptimizerFilterAndAdoption() {
        CustomProject project = new CustomProject("Flow", "ToTheMoon", "EURUSD", "M5");
        project.setAutomaticModeEnabled(true);

        WorkflowTask opt = new WorkflowTask("05 ADX — Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        opt.setStatus(WorkflowTask.TaskStatus.COMPLETED);
        opt.setTargetDatabank("g05_raw");
        opt.setOptimizerTargetParameters(List.of("Inp_Use_ADX_Filter", "Inp_ADX_Period"));

        WorkflowTask filter = new WorkflowTask("05 ADX — Trade/Qualitätsfilter", WorkflowTask.TaskType.PRE_FILTER);
        filter.setStatus(WorkflowTask.TaskStatus.COMPLETED);
        filter.setSourceDatabank("g05_raw");
        filter.setTargetDatabank("g05_pick");
        filter.setFilterConditions(List.of(
                new FilterCondition(FilterCondition.Metric.BT_NET_PROFIT,
                        FilterCondition.Operator.GREATER_THAN, 0)));

        WorkflowTask next = new WorkflowTask("06 RSI — Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        next.setStatus(WorkflowTask.TaskStatus.PENDING);
        next.setSourceDatabank("g05_pick");
        next.setTargetDatabank("g06_raw");
        next.setOptimizerTargetParameters(List.of("Inp_Use_RSI_Filter"));
        next.setOptimizerParameterBasisAdopted(true);
        next.setOptimizerParameterBasisPassNumber(3);
        next.setOptimizerParameterBasisDatabank("g05_pick");
        next.setOptimizerParameterSnapshot(List.of(
                param("Inp_Use_ADX_Filter", "true", false),
                param("Inp_ADX_Period", "14", false),
                param("Inp_Use_RSI_Filter", "false", true)));

        project.addTask(opt);
        project.addTask(filter);
        project.addTask(next);

        CombinedPass best = scoredPass(3, 88, Map.of(
                "Inp_Use_ADX_Filter", "true",
                "Inp_ADX_Period", "14",
                "Inp_Use_RSI_Filter", "false"));
        CombinedPass other = scoredPass(1, 40, Map.of(
                "Inp_Use_ADX_Filter", "false",
                "Inp_ADX_Period", "20"));

        project.getDatabanks().put("g05_raw", new ArrayList<>(List.of(best, other)));
        project.getDatabanks().put("g05_pick", new ArrayList<>(List.of(best)));

        DatabankManager manager = new DatabankManager();
        manager.loadFromProject(project);

        List<FlowStepSummary> steps = WorkflowFlowSummaryService.build(
                project, manager, t -> "C:/missing/report/dir");

        assertEquals(3, steps.size());

        FlowStepSummary optStep = steps.get(0);
        assertTrue(optStep.getWhatHappened().contains("Optimizer ausgeführt"));
        assertTrue(optStep.getWhatHappened().contains("2 Strategien"));
        assertTrue(optStep.getDecision().contains("Pass #3"));
        assertTrue(optStep.getSetfileProof().isPresent());
        assertEquals(ProofStatus.VERIFIED, optStep.getSetfileProof().getStatus());
        assertTrue(optStep.getSetfileProof().getLines().stream()
                .anyMatch(l -> l.contains("Inp_Use_ADX_Filter") && l.startsWith("✓")));

        FlowStepSummary filterStep = steps.get(1);
        assertTrue(filterStep.getWhatHappened().contains("2 → 1"));
        assertTrue(filterStep.getDecision().contains("Pass #3"));
        assertTrue(filterStep.getSetfileProof().isPresent());
        assertEquals(ProofStatus.VERIFIED, filterStep.getSetfileProof().getStatus());

        FlowStepSummary nextStep = steps.get(2);
        assertTrue(nextStep.getDetails().stream().anyMatch(d -> d.contains("Start-Basis übernommen")));
        assertTrue(nextStep.getSetfileProof().isPresent());
        assertEquals(ProofStatus.VERIFIED, nextStep.getSetfileProof().getStatus());
        assertTrue(nextStep.getSetfileProof().getHeadline().contains("VERIFIZIERT"));
    }

    @Test
    public void setfileProofDetectsMismatchWhenSnapshotDivergesFromPass() {
        WorkflowTask next = new WorkflowTask("Next", WorkflowTask.TaskType.OPTIMIZER);
        next.setOptimizerTargetParameters(List.of("Inp_Use_RSI_Filter"));
        next.setOptimizerParameterBasisAdopted(true);
        next.setOptimizerParameterBasisPassNumber(1);
        next.setOptimizerParameterBasisDatabank("pick");
        next.setOptimizerParameterSnapshot(List.of(
                param("Inp_Use_ADX_Filter", "false", false), // should be true from pass
                param("Inp_Use_RSI_Filter", "true", true)));

        CombinedPass adopted = scoredPass(1, 50, Map.of("Inp_Use_ADX_Filter", "true"));

        SetfileProof proof = WorkflowFlowSummaryService.buildHandoffSetfileProof(
                "Prev", next, adopted, List.of("Inp_Use_ADX_Filter"), "Filter AN empfohlen");

        assertEquals(ProofStatus.MISMATCH, proof.getStatus());
        assertTrue(proof.getLines().stream().anyMatch(l -> l.contains("Inp_Use_ADX_Filter") && l.startsWith("✗")));
        assertTrue(proof.getLines().stream().anyMatch(l -> l.toLowerCase().contains("max-score")
                || l.toLowerCase().contains("empfehlung")));
    }

    @Test
    public void disabledTaskIsIdle() {
        CustomProject project = new CustomProject("P", "EA", "EURUSD", "H1");
        WorkflowTask task = new WorkflowTask("Skip me", WorkflowTask.TaskType.RETESTER);
        task.setEnabled(false);
        project.addTask(task);

        List<FlowStepSummary> steps = WorkflowFlowSummaryService.build(
                project, new DatabankManager(), t -> "");

        assertEquals(1, steps.size());
        assertEquals(WorkflowFlowSummaryService.StepTone.IDLE, steps.get(0).getTone());
        assertTrue(steps.get(0).getWhatHappened().toLowerCase().contains("deaktiviert"));
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
