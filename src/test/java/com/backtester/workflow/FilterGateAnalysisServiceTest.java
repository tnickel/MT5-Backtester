package com.backtester.workflow;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class FilterGateAnalysisServiceTest {

    @Test
    public void normalizeBooleanAcceptsMt5Variants() {
        assertEquals(Boolean.TRUE, FilterGateAnalysisService.normalizeBoolean("true"));
        assertEquals(Boolean.TRUE, FilterGateAnalysisService.normalizeBoolean("1"));
        assertEquals(Boolean.TRUE, FilterGateAnalysisService.normalizeBoolean("YES"));
        assertEquals(Boolean.FALSE, FilterGateAnalysisService.normalizeBoolean("false"));
        assertEquals(Boolean.FALSE, FilterGateAnalysisService.normalizeBoolean("0"));
        assertEquals(Boolean.FALSE, FilterGateAnalysisService.normalizeBoolean("off"));
        assertNull(FilterGateAnalysisService.normalizeBoolean("maybe"));
        assertNull(FilterGateAnalysisService.normalizeBoolean(""));
        assertNull(FilterGateAnalysisService.normalizeBoolean(null));
    }

    @Test
    public void resolveGatePrefersInpUseFromTargets() {
        WorkflowTask task = new WorkflowTask("Stage", WorkflowTask.TaskType.OPTIMIZER);
        task.setOptimizerTargetParameters(List.of(
                "Inp_Esc_Lookback_Bars",
                "Inp_Use_Escalation_Block",
                "Inp_Esc_ADX_Rise"));

        assertEquals("Inp_Use_Escalation_Block",
                FilterGateAnalysisService.resolveGateParameter(task).orElseThrow());
    }

    @Test
    public void analyzeSplitsTrueFalseAndUnknown() {
        List<CombinedPass> passes = List.of(
                scoredPass(1, 80, "Inp_Use_X", "true"),
                scoredPass(2, 70, "Inp_Use_X", "1"),
                scoredPass(3, 40, "Inp_Use_X", "false"),
                scoredPass(4, 30, "Inp_Use_X", "0"),
                scoredPass(5, 99, "Inp_Use_X", ""),
                scoredPass(6, 10, "Other", "true"));

        FilterGateAnalysisService.FilterGateAnalysis analysis = FilterGateAnalysisService.analyze(
                passes, "Inp_Use_X",
                FilterGateAnalysisService.DataSource.OPTIMIZER_REPORT,
                "C:/reports/run", "",
                2, 10, 0.10, List.of("Inp_Use_X"));

        assertEquals(2, analysis.getOnStats().getCount());
        assertEquals(2, analysis.getOffStats().getCount());
        assertEquals(2, analysis.getUnknownCount());
        assertEquals(FilterGateAnalysisService.Verdict.FILTER_ON_BETTER, analysis.getVerdict());
        assertFalse(analysis.isFallback());
        assertFalse(analysis.getVerdictMessage().contains("auf Basis Databank"));
    }

    @Test
    public void analyzeMarksFallbackInVerdict() {
        List<CombinedPass> passes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            passes.add(scoredPass(i + 1, 90 - i, "Inp_Use_X", "true"));
        }
        for (int i = 0; i < 6; i++) {
            passes.add(scoredPass(100 + i, 20 - i, "Inp_Use_X", "false"));
        }

        FilterGateAnalysisService.FilterGateAnalysis analysis = FilterGateAnalysisService.analyze(
                passes, "Inp_Use_X",
                FilterGateAnalysisService.DataSource.DATABANK_FALLBACK,
                "", "stage_raw",
                5, 10, 0.10, List.of());

        assertTrue(analysis.isFallback());
        assertEquals(FilterGateAnalysisService.DataSource.DATABANK_FALLBACK, analysis.getDataSource());
        assertEquals("stage_raw", analysis.getDatabankName());
        assertEquals(FilterGateAnalysisService.Verdict.FILTER_ON_BETTER, analysis.getVerdict());
        assertTrue(analysis.getVerdictMessage().contains("auf Basis Databank"));
    }

    @Test
    public void insufficientDataWhenCohortTooSmall() {
        List<CombinedPass> passes = List.of(
                scoredPass(1, 80, "Inp_Use_X", "true"),
                scoredPass(2, 10, "Inp_Use_X", "false"));

        FilterGateAnalysisService.FilterGateAnalysis analysis = FilterGateAnalysisService.analyze(
                passes, "Inp_Use_X",
                FilterGateAnalysisService.DataSource.OPTIMIZER_REPORT,
                "path", "");

        assertEquals(FilterGateAnalysisService.Verdict.INSUFFICIENT_DATA, analysis.getVerdict());
    }

    @Test
    public void missingGateReturnsGateMissing() {
        FilterGateAnalysisService.FilterGateAnalysis analysis = FilterGateAnalysisService.analyze(
                List.of(scoredPass(1, 50, "Foo", "1")),
                "",
                FilterGateAnalysisService.DataSource.DATABANK_FALLBACK,
                "", "db");

        assertEquals(FilterGateAnalysisService.Verdict.GATE_MISSING, analysis.getVerdict());
        assertTrue(analysis.getVerdictMessage().contains("Kein An/Aus-Filter-Schalter"));
        assertTrue(analysis.getVerdictMessage().contains("nur optimiert"));
        assertTrue(analysis.getVerdictMessage().contains("auf Basis Databank"));
        assertTrue(analysis.getOptimizedParameterNames().contains("Foo"));
    }

    @Test
    public void noGateExplanationListsOptimizedParameters() {
        String text = FilterGateAnalysisService.formatNoGateExplanation(
                256, List.of("Inp_Envelopes_Period", "Inp_Envelopes_Deviation"));
        assertTrue(text.contains("256"));
        assertTrue(text.contains("Inp_Envelopes_Period"));
        assertTrue(text.contains("nur optimiert"));
        assertTrue(text.contains("nichts wurde für diese Analyse weggefiltert"));
    }

    @Test
    public void databankFallbackWhenReportMissing() {
        CustomProject project = new CustomProject("P", "EA", "EURUSD", "M5");
        WorkflowTask task = new WorkflowTask("Opt", WorkflowTask.TaskType.OPTIMIZER);
        task.setTargetDatabank("Results");
        project.addTask(task);
        project.getDatabanks().put("Results", new ArrayList<>(List.of(
                scoredPass(7, 55, "Inp_Use_X", "true"))));

        DatabankManager manager = new DatabankManager();
        manager.loadFromProject(project);

        FilterGateAnalysisService.PassLoadResult loaded = FilterGateAnalysisService.loadPassesForTask(
                task, "C:/does/not/exist/optimizer", manager);

        assertEquals(FilterGateAnalysisService.DataSource.DATABANK_FALLBACK, loaded.getDataSource());
        assertTrue(loaded.isFallback());
        assertEquals("Results", loaded.getDatabankName());
        assertEquals(1, loaded.getPasses().size());
        assertEquals(7, loaded.getPasses().get(0).getPassNumber());
    }

    private static CombinedPass scoredPass(int passNumber, double score, String param, String value) {
        Pass bt = new Pass();
        bt.setPassNumber(passNumber);
        bt.setProfit(score * 10);
        bt.setTotalTrades(100);
        bt.setDrawdownPercent(10);
        bt.setParameter(param, value);
        return new CombinedPass(bt, null, score, 1.0, "test");
    }
}
