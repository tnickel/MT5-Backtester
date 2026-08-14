package com.backtester.workflow;

import com.backtester.config.EaParameter;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class CustomProjectBackupTest {

    @Test
    public void backupRoundTripKeepsTasksDatabanksArchivesLineageAndProvenMaster() {
        CustomProject project = new CustomProject("Guided", "ToTheMoon_KI_v132", "GBPJPY", "M5");
        project.setId("open-project-id");
        project.setSortOrder(4);
        project.setSaveDatabanksPersistently(false);
        project.setProvenMasterParameters(List.of(new EaParameter("Inp_Grid_Step", "540")));
        project.setConfirmedMasterSequence(3);

        WorkflowTask grid = new WorkflowTask("01 Grid-Fundament — Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        grid.setOptimizerTargetParameters(List.of("Inp_Grid_Step", "Min_Profit"));
        EaParameter gridStep = new EaParameter("Inp_Grid_Step", "540");
        gridStep.setOptimizeEnabled(true);
        grid.setOptimizerParameterSnapshot(List.of(gridStep));
        project.addTask(grid);

        MasterStrategyEntry lineageEntry = new MasterStrategyEntry();
        lineageEntry.setSequence(3);
        lineageEntry.setStageTaskName("01 Grid-Fundament — Optimizer");
        lineageEntry.setProfit(1234.5);
        project.setMasterStrategyLineage(List.of(lineageEntry));

        StrategyBacktestArchive archive = new StrategyBacktestArchive("42|Strat 42", "Strat 42", 42);
        StrategyBacktestRun run = new StrategyBacktestRun();
        run.setTabName("g01_grid_raw");
        archive.upsert(run);
        project.getStrategyArchives().put(archive.getStrategyKey(), archive);

        DatabankManager databanks = new DatabankManager();
        databanks.setDatabankContent("g01_grid_raw", List.of(pass(42)));

        String json = CustomProjectBackup.toJson(project, databanks);
        CustomProject current = new CustomProject("Open", "EA", "EURUSD", "H1");
        current.setId("keep-this-id");
        current.setSortOrder(9);

        CustomProject restored = CustomProjectBackup.restoreInto(current, CustomProjectBackup.fromJson(json));

        assertEquals("keep-this-id", restored.getId());
        assertEquals(9, restored.getSortOrder());
        assertEquals("Guided", restored.getName());
        assertEquals("GBPJPY", restored.getSymbol());
        assertEquals(List.of("Inp_Grid_Step", "Min_Profit"),
                restored.getTasks().get(0).getOptimizerTargetParameters());
        assertEquals("540", restored.getTasks().get(0).getOptimizerParameterSnapshot().get(0).getValue());
        assertEquals(1, restored.getDatabanks().get("g01_grid_raw").size());
        assertEquals(42, restored.getDatabanks().get("g01_grid_raw").get(0).getPassNumber());
        assertEquals(1, restored.getStrategyArchives().size());
        assertEquals(3, restored.getConfirmedMasterSequence());
        assertEquals("540", restored.getProvenMasterParameters().get(0).getValue());
        assertEquals(1, restored.getMasterStrategyLineage().size());
        assertEquals(1234.5, restored.getMasterStrategyLineage().get(0).getProfit(), 0.0);
    }

    @Test
    public void restoreDoesNotRewriteCustomOptimizerTargets() {
        CustomProject project = ToTheMoon132GuidedWorkflowFactory.create(
                "Guided", syntheticPreset(), null);
        WorkflowTask grid = project.getTasks().stream()
                .filter(task -> task.getName().startsWith("01 Grid-Fundament"))
                .findFirst().orElseThrow();
        List<String> customTargets = List.of(
                "Inp_Grid_Step", "Inp_Step_Multiplier", "Inp_Next_Lot_Multiplier", "Min_Profit");
        grid.setOptimizerTargetParameters(new ArrayList<>(customTargets));

        String json = CustomProjectBackup.toJson(project, new DatabankManager());
        CustomProject restored = CustomProjectBackup.fromJson(json);

        WorkflowTask restoredGrid = restored.getTasks().stream()
                .filter(task -> task.getName().startsWith("01 Grid-Fundament"))
                .findFirst().orElseThrow();
        assertEquals(customTargets, restoredGrid.getOptimizerTargetParameters());
    }

    private static CombinedPass pass(int passNumber) {
        Pass pass = new Pass();
        pass.setPassNumber(passNumber);
        pass.setProfit(100);
        pass.setTotalTrades(100);
        return new CombinedPass(pass, null, 50, 1, "");
    }

    private static List<EaParameter> syntheticPreset() {
        List<EaParameter> result = new ArrayList<>();
        for (List<String> names : ToTheMoon132GuidedWorkflowFactory.stageTargetParameters().values()) {
            for (String name : names) {
                if (result.stream().anyMatch(existing -> name.equals(existing.getName()))) {
                    continue;
                }
                EaParameter parameter = new EaParameter(name, "1");
                parameter.setOptimizeStart("1");
                parameter.setOptimizeStep("1");
                parameter.setOptimizeEnd("2");
                parameter.setStringType(false);
                result.add(parameter);
            }
        }
        return result;
    }
}
