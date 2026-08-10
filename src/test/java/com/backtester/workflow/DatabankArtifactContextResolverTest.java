package com.backtester.workflow;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DatabankArtifactContextResolverTest {

    @Test
    public void usesRetesterConfigurationInsteadOfStaleProjectDefaults() {
        CustomProject project = new CustomProject("Test1", "ToTheMoon_KI_v132", "EURUSD", "H1");
        WorkflowTask optimizer = task("Optimizer", WorkflowTask.TaskType.OPTIMIZER,
                "Results", "data0", "AUDCAD", "M5", "2025-07-28", "2026-07-28");
        WorkflowTask clustering = task("Clustering", WorkflowTask.TaskType.DIVERSITY_FILTER,
                "data0", "data2", "EURUSD", "H1", "", "");
        WorkflowTask retester = task("Tickdatatest", WorkflowTask.TaskType.RETESTER,
                "data2", "ticktest", "AUDCAD", "M5", "2022-08-01", "2026-08-01");
        project.setTasks(List.of(optimizer, clustering, retester));

        DatabankArtifactContextResolver.Context context =
                DatabankArtifactContextResolver.resolve(project, "ticktest",
                        List.of(pass("AUDCAD")), "FallbackEA", "GBPUSD", "M15");

        assertEquals("ToTheMoon_KI_v132", context.expert());
        assertEquals("AUDCAD", context.symbol());
        assertEquals("M5", context.period());
        assertEquals(LocalDate.of(2022, 8, 1), context.from());
        assertEquals(LocalDate.of(2026, 8, 1), context.to());
        assertTrue(context.retestRange());
    }

    @Test
    public void followsFilteringLineageBackToOptimizer() {
        CustomProject project = new CustomProject("Test1", "TestEA", "EURUSD", "H1");
        project.setTasks(List.of(
                task("Optimizer", WorkflowTask.TaskType.OPTIMIZER,
                        "Results", "data0", "AUDCAD", "M5", "2025-07-28", "2026-07-28"),
                task("Filter", WorkflowTask.TaskType.PRE_FILTER,
                        "data0", "data1", "EURUSD", "H1", "", ""),
                task("Clustering", WorkflowTask.TaskType.DIVERSITY_FILTER,
                        "data1", "data2", "EURUSD", "H1", "", "")
        ));

        DatabankArtifactContextResolver.Context context =
                DatabankArtifactContextResolver.resolve(project, "data2",
                        List.of(pass("AUDCAD", null)), "", "", "");

        assertEquals("AUDCAD", context.symbol());
        assertEquals("M5", context.period());
        assertEquals(LocalDate.of(2025, 7, 28), context.from());
        assertEquals(LocalDate.of(2026, 7, 28), context.to());
    }

    @Test
    public void prefersPersistedPassPeriodOverStaleFilterTask() {
        CustomProject project = new CustomProject("Test1", "TestEA", "EURUSD", "H1");
        project.setTasks(List.of(
                task("Optimizer", WorkflowTask.TaskType.OPTIMIZER,
                        "Results", "data0", "AUDCAD", "M5", "2025-07-28", "2026-07-28"),
                task("Filter", WorkflowTask.TaskType.PRE_FILTER,
                        "data0", "data1", "EURUSD", "H1", "", "")
        ));

        DatabankArtifactContextResolver.Context context =
                DatabankArtifactContextResolver.resolve(project, "data1",
                        List.of(pass("AUDCAD", "M5")), "", "", "");

        assertEquals("AUDCAD", context.symbol());
        assertEquals("M5", context.period());
    }

    private static WorkflowTask task(String name, WorkflowTask.TaskType type,
                                     String source, String target,
                                     String symbol, String period,
                                     String from, String to) {
        WorkflowTask task = new WorkflowTask(name, type);
        task.setSourceDatabank(source);
        task.setTargetDatabank(target);
        task.setRetestSymbol(symbol);
        task.setRetestPeriod(period);
        task.setStartDate(from);
        task.setEndDate(to);
        return task;
    }

    private static CombinedPass pass(String symbol) {
        return pass(symbol, null);
    }

    private static CombinedPass pass(String symbol, String period) {
        Pass bt = new Pass();
        bt.setPassNumber(1);
        bt.setTickModel("1 minute OHLC");
        CombinedPass pass = new CombinedPass(bt, null, 0.0, 0.0, "");
        pass.setSymbol(symbol);
        pass.setPeriod(period);
        return pass;
    }
}
