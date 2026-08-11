package com.backtester.workflow;

import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FilterGateAdxReportSmokeTest {

    @Test
    public void adxReportProducesTopLists() {
        Path dir = Path.of(
                "backtest_reports/GUIDED_ToTheMoon132_AUDCAD_M5_4Y/g05_adx/"
                        + "OPT_ToTheMoon_KI_v132_M5_AUDCAD_20260810_162823");
        Assume.assumeTrue("Local ADX report present", Files.isDirectory(dir));

        WorkflowTask task = new WorkflowTask("05 ADX", WorkflowTask.TaskType.OPTIMIZER);
        task.setOptimizerTargetParameters(java.util.List.of(
                "Inp_Use_ADX_Filter", "Inp_ADX_Period", "Inp_ADX_Max_Level"));
        FilterGateAnalysisService.PassLoadResult loaded =
                FilterGateAnalysisService.loadPassesForTask(task, dir.getParent().toString(), null);
        assertEquals(FilterGateAnalysisService.DataSource.OPTIMIZER_REPORT, loaded.getDataSource());
        assertTrue(loaded.getPasses().size() > 0);

        FilterGateAnalysisService.FilterGateAnalysis analysis = FilterGateAnalysisService.analyze(
                loaded.getPasses(), "Inp_Use_ADX_Filter",
                loaded.getDataSource(), loaded.getSourcePath(), "");

        assertEquals(108, analysis.getOnStats().getCount());
        assertEquals(108, analysis.getOffStats().getCount());
        assertFalse(analysis.getOnStats().getTopByScore().isEmpty());
        assertFalse(analysis.getOffStats().getTopByScore().isEmpty());
    }
}