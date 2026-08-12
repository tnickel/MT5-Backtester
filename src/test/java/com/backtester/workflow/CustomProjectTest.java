package com.backtester.workflow;

import com.backtester.config.EaParameter;
import com.backtester.database.DatabaseManager;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class CustomProjectTest {

    @Test
    public void automaticModeHasSafeLegacyDefault() {
        CustomProject freshProject = new CustomProject();
        CustomProject legacyProject = DatabaseManager.createCustomProjectGson().fromJson(
                "{\"name\":\"Legacy\",\"tasks\":[]}", CustomProject.class);

        assertFalse(freshProject.isAutomaticModeEnabled());
        assertNotNull(legacyProject);
        assertFalse(legacyProject.isAutomaticModeEnabled());
    }

    @Test
    public void theProvenMasterSurvivesACrashBecauseItIsPersistedAndDetached() {
        // The candidate is written to the task snapshot before the minutes-long reference
        // run; only this field says which basis a measurement actually confirmed, so it has
        // to come back intact after a restart.
        CustomProject project = new CustomProject("Guided", "EA", "AUDCAD", "M5");
        EaParameter grid = new EaParameter("GridStep", "23");
        project.setProvenMasterParameters(List.of(grid));

        grid.setValue("999");
        project.getProvenMasterParameters().get(0).setValue("777");
        assertEquals("23", project.getProvenMasterParameters().get(0).getValue());

        // Deliberately through copyMetadataForPersistence: that copy — not the live object —
        // is what the save coordinator hands to SQLite. Serialising the project directly
        // would pass even when the field never reaches the database.
        CustomProject persisted = project.copyMetadataForPersistence();
        String json = DatabaseManager.createCustomProjectGson().toJson(persisted);
        CustomProject restored = DatabaseManager.createCustomProjectGson()
                .fromJson(json, CustomProject.class);

        assertEquals(1, restored.getProvenMasterParameters().size());
        assertEquals("23", restored.getProvenMasterParameters().get(0).getValue());
        assertTrue(restored.hasProvenMaster());
    }

    @Test
    public void theFloorNeverOutlivesTheBasisItBelongsTo() {
        // A floor without its parameters is a limit nothing can be measured against: after
        // a restart it would reject every candidate while the chain has nothing to fall
        // back to. The two are only meaningful together, so they are saved and cleared
        // together.
        CustomProject project = new CustomProject("Guided", "EA", "AUDCAD", "M5");
        project.setProvenMasterParameters(List.of(new EaParameter("GridStep", "23")));
        project.setProvenMasterContextKey("EA|AUDCAD|M5");
        project.setMasterSelectionRatio(3.5);

        CustomProject persisted = project.copyMetadataForPersistence();
        assertEquals(3.5, persisted.getMasterSelectionRatio(), 1e-9);
        assertEquals("EA|AUDCAD|M5", persisted.getProvenMasterContextKey());
        assertTrue(persisted.hasProvenMaster());

        project.clearProvenMaster();
        assertFalse(project.hasProvenMaster());
        assertTrue(Double.isNaN(project.getMasterSelectionRatio()));
        assertEquals("", project.getProvenMasterContextKey());
    }

    @Test
    public void aLegacyProjectWithoutAProvenMasterReportsAnEmptyBasis() {
        CustomProject legacy = DatabaseManager.createCustomProjectGson().fromJson(
                "{\"name\":\"Legacy\",\"tasks\":[]}", CustomProject.class);

        assertNotNull(legacy);
        assertTrue(legacy.getProvenMasterParameters().isEmpty());
    }

    @Test
    public void automaticModePersistsThroughProjectCopiesAndJson() {
        CustomProject project = new CustomProject("Guided", "EA", "AUDCAD", "M5");
        project.setAutomaticModeEnabled(true);

        CustomProject clone = project.cloneProject("Guided Copy", null, null);
        CustomProject metadataCopy = project.copyMetadataForPersistence();
        String json = DatabaseManager.createCustomProjectGson().toJson(project);
        CustomProject restored = DatabaseManager.createCustomProjectGson().fromJson(json, CustomProject.class);

        assertTrue(clone.isAutomaticModeEnabled());
        assertTrue(metadataCopy.isAutomaticModeEnabled());
        assertNotNull(restored);
        assertTrue(restored.isAutomaticModeEnabled());
    }

    @Test
    public void metadataPersistenceCopyKeepsSortOrder() {
        CustomProject project = new CustomProject("Guided", "EA", "AUDCAD", "M5");
        project.setSortOrder(7);

        CustomProject copy = project.copyMetadataForPersistence();

        assertEquals(7, copy.getSortOrder());
    }

    @Before
    public void setUp() {
        DatabaseManager.getInstance();
    }

    @Test
    public void testCustomProjectTemplateCreation() {
        CustomProject proj = CustomProject.createDefaultTemplate("Test Project", "MyEA.ex5", "EURUSD", "H1");
        assertNotNull(proj);
        assertEquals("Test Project", proj.getName());
        assertEquals("EURUSD", proj.getSymbol());
        assertEquals(9, proj.getTasks().size());
        assertEquals(9, proj.getEnabledTaskCount());
        assertEquals(WorkflowTask.TaskType.PRE_FILTER, proj.getTasks().get(2).getType());
        assertEquals("Kurzzeit-Vorauswahl", proj.getTasks().get(2).getName());
        assertEquals(WorkflowTask.TaskType.RETESTER, proj.getTasks().get(3).getType());
        assertEquals("Langzeittest (5-10 Jahre)", proj.getTasks().get(3).getName());
        assertEquals("Diversitäts-Clustering", proj.getTasks().get(4).getName());
        assertEquals(WorkflowTask.TaskType.RETESTER, proj.getTasks().get(7).getType());
        assertEquals("Validierung (OOS)", proj.getTasks().get(7).getName());
        assertEquals("Results", proj.getTasks().get(7).getSourceDatabank());
        assertEquals("Final", proj.getTasks().get(7).getTargetDatabank());
        assertEquals(WorkflowTask.TaskType.PORTFOLIO_EXPORT, proj.getTasks().get(8).getType());
        assertEquals("Final", proj.getTasks().get(8).getSourceDatabank());
        assertEquals("Final", proj.getTasks().get(8).getTargetDatabank());
    }

    @Test
    public void testTaskReordering() {
        CustomProject proj = new CustomProject("Reorder Test", "EA.ex5", "GBPUSD", "M15");
        WorkflowTask t1 = new WorkflowTask("Task 1", WorkflowTask.TaskType.OPTIMIZER);
        WorkflowTask t2 = new WorkflowTask("Task 2", WorkflowTask.TaskType.RETESTER);

        proj.addTask(t1);
        proj.addTask(t2);

        assertEquals("Task 1", proj.getTasks().get(0).getName());

        assertTrue(proj.moveTaskDown(0));
        assertEquals("Task 2", proj.getTasks().get(0).getName());
        assertEquals("Task 1", proj.getTasks().get(1).getName());

        assertTrue(proj.moveTaskUp(1));
        assertEquals("Task 1", proj.getTasks().get(0).getName());
    }

    @Test
    public void insertTaskBelowClonesWithSettings() {
        CustomProject proj = new CustomProject("Clone Test", "EA.ex5", "AUDCAD", "M5");
        WorkflowTask t1 = new WorkflowTask("Tickdatatest", WorkflowTask.TaskType.RETESTER);
        t1.setSourceDatabank("data2");
        t1.setTargetDatabank("ticktest");
        t1.setRetestSymbol("AUDCAD");
        t1.setRetestPeriod("M5");
        t1.setExecutionMode(WorkflowTask.MODE_OHLC_M1);
        t1.setStatus(WorkflowTask.TaskStatus.COMPLETED);
        WorkflowTask t2 = new WorkflowTask("After", WorkflowTask.TaskType.DIVERSITY_FILTER);
        proj.addTask(t1);
        proj.addTask(t2);

        WorkflowTask clone = t1.cloneWithSettings();
        assertTrue(proj.insertTaskBelow(0, clone));

        assertEquals(3, proj.getTasks().size());
        assertSame(t1, proj.getTasks().get(0));
        assertSame(clone, proj.getTasks().get(1));
        assertSame(t2, proj.getTasks().get(2));
        assertNotEquals(t1.getId(), clone.getId());
        assertEquals("Tickdatatest (copy)", clone.getName());
        assertEquals("data2", clone.getSourceDatabank());
        assertEquals("ticktest", clone.getTargetDatabank());
        assertEquals("AUDCAD", clone.getRetestSymbol());
        assertEquals("M5", clone.getRetestPeriod());
        assertEquals(WorkflowTask.MODE_OHLC_M1, clone.getExecutionMode());
        assertEquals(WorkflowTask.TaskStatus.PENDING, clone.getStatus());
    }

    @Test
    public void testDatabasePersistence() {
        CustomProject proj = CustomProject.createDefaultTemplate("Persistence Test Proj", "EA.ex5", "AUDCAD", "H1");
        DatabaseManager.getInstance().saveCustomProject(proj);

        List<CustomProject> loadedList = DatabaseManager.getInstance().getAllCustomProjects();
        assertNotNull(loadedList);
        assertFalse(loadedList.isEmpty());

        boolean found = false;
        for (CustomProject p : loadedList) {
            if (proj.getId().equals(p.getId())) {
                found = true;
                assertEquals("Persistence Test Proj", p.getName());
                assertEquals(9, p.getTasks().size());
                break;
            }
        }
        assertTrue("Saved project should be retrieved from SQLite database", found);

        // Clean up test project
        DatabaseManager.getInstance().deleteCustomProject(proj.getId());
    }

    @Test
    public void workflowModesMapToRawMt5Models() {
        WorkflowTask task = new WorkflowTask("Retest", WorkflowTask.TaskType.RETESTER);

        task.setExecutionMode(WorkflowTask.MODE_EVERY_TICK);
        assertEquals(0, task.getMt5Model());
        task.setExecutionMode(WorkflowTask.MODE_OHLC_M1);
        assertEquals(1, task.getMt5Model());
        task.setExecutionMode(WorkflowTask.MODE_REAL_TICKS);
        assertEquals(4, task.getMt5Model());
        task.setExecutionMode(WorkflowTask.MODE_OPEN_PRICES);
        assertEquals(2, task.getMt5Model());
    }

    @Test
    public void addTaskTypesExposeOnlyOneRetesterModule() {
        List<WorkflowTask.TaskType> selectable = List.of(WorkflowTask.TaskType.userSelectableValues());

        assertEquals(1, selectable.stream().filter(type -> type == WorkflowTask.TaskType.RETESTER).count());
        assertFalse(selectable.contains(WorkflowTask.TaskType.LONGTERM_RETEST));
        assertFalse(selectable.contains(WorkflowTask.TaskType.OOS_VALIDATION));
        assertFalse(selectable.contains(WorkflowTask.TaskType.CUSTOM_SCRIPT));
    }

    @Test
    public void legacyRetestTypesAndNumberedNamesAreMigrated() {
        String json = "{\"name\":\"Legacy\",\"tasks\":[" +
                "{\"name\":\"1. Langzeittest (5-10 Jahre)\",\"type\":\"LONGTERM_RETEST\"}," +
                "{\"name\":\"2. Validierung (OOS)\",\"type\":\"OOS_VALIDATION\"}," +
                "{\"name\":\"3. Mein Sondertest\",\"type\":\"CUSTOM_SCRIPT\"}]}";
        CustomProject project = new com.google.gson.Gson().fromJson(json, CustomProject.class);

        assertTrue(project.migrateLegacyTaskDefinitions());
        assertEquals(List.of("Langzeittest (5-10 Jahre)", "Validierung (OOS)", "Mein Sondertest"),
                project.getTasks().stream().map(WorkflowTask::getName).toList());
        assertTrue(project.getTasks().stream()
                .allMatch(task -> task.getType() == WorkflowTask.TaskType.RETESTER));
        assertFalse(project.migrateLegacyTaskDefinitions());
    }

    @Test
    public void diversitySettingsAreTaskSpecificAndCopiedForPersistence() {
        WorkflowTask task = new WorkflowTask("Langzeit-Clustering", WorkflowTask.TaskType.DIVERSITY_FILTER);
        task.setDiversityParamDiffPct(0.22);
        task.setDiversityTradeDiffPct(0.31);
        task.setDiversityMinDifferentParams(4);
        task.setDiversityMaxStrategies(17);
        task.setDiversityRankByScore(true);
        EaParameter comparison = new EaParameter("Inp_Grid", "10");
        comparison.setOptimizeEnabled(true);
        task.setDiversityParameterSnapshot(List.of(comparison));

        WorkflowTask copy = task.copyForPersistence();

        assertEquals(0.22, copy.getDiversityParamDiffPct(), 0.0);
        assertEquals(0.31, copy.getDiversityTradeDiffPct(), 0.0);
        assertEquals(4, copy.getDiversityMinDifferentParams());
        assertEquals(17, copy.getDiversityMaxStrategies());
        assertTrue(copy.isDiversityRankByScore());
        assertEquals(List.of("Inp_Grid"), copy.getDiversityParameterSnapshot().stream()
                .map(EaParameter::getName).toList());
    }

    @Test
    public void optimizerOutputDirectoryIsTaskSpecificAndCopiedForPersistence() {
        WorkflowTask task = new WorkflowTask("Mein Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        task.setOptimizerOutputDirectory("  D:\\Strategien\\Optimiert  ");

        WorkflowTask copy = task.copyForPersistence();

        assertEquals("D:\\Strategien\\Optimiert", task.getOptimizerOutputDirectory());
        assertEquals(task.getOptimizerOutputDirectory(), copy.getOptimizerOutputDirectory());
    }

    @Test
    public void optimizerAndRobustnessRunSettingsAreTaskSpecificAndPersisted() {
        WorkflowTask task = new WorkflowTask("Mein Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        task.setOptimizerMode(1);
        task.setOptimizerCriterion(7);
        task.setOptimizerForwardMode(4);
        task.setOptimizerForwardDate("2026-05-01");
        task.setOptimizerTargetParameters(List.of("Inp_Entry", "Inp_Exit"));
        task.setSensitivityRunTimestamp(123456789L);

        WorkflowTask copy = task.copyForPersistence();

        assertEquals(1, copy.getOptimizerMode());
        assertEquals(7, copy.getOptimizerCriterion());
        assertEquals(4, copy.getOptimizerForwardMode());
        assertEquals("2026-05-01", copy.getOptimizerForwardDate());
        assertEquals(List.of("Inp_Entry", "Inp_Exit"), copy.getOptimizerTargetParameters());
        assertEquals(123456789L, copy.getSensitivityRunTimestamp());
    }

    @Test
    public void optimizerTargetParametersAreDefensiveAndPersistThroughJson() {
        WorkflowTask task = new WorkflowTask("Stage Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        List<String> source = new ArrayList<>(List.of("  Inp_Entry  ", "Inp_Exit"));

        task.setOptimizerTargetParameters(source);
        source.set(0, "Inp_ChangedOutside");
        List<String> returned = task.getOptimizerTargetParameters();
        returned.clear();

        assertEquals(List.of("Inp_Entry", "Inp_Exit"), task.getOptimizerTargetParameters());

        String json = new com.google.gson.Gson().toJson(task.copyForPersistence());
        WorkflowTask restored = new com.google.gson.Gson().fromJson(json, WorkflowTask.class);
        assertEquals(List.of("Inp_Entry", "Inp_Exit"), restored.getOptimizerTargetParameters());

        WorkflowTask clone = task.cloneWithSettings();
        assertEquals(List.of("Inp_Entry", "Inp_Exit"), clone.getOptimizerTargetParameters());
        clone.setOptimizerTargetParameters(List.of("Inp_CloneOnly"));
        assertEquals(List.of("Inp_Entry", "Inp_Exit"), task.getOptimizerTargetParameters());
    }

    @Test
    public void legacyOptimizerTaskUsesCanonicalSafeDefaults() {
        WorkflowTask task = new com.google.gson.Gson().fromJson(
                "{\"name\":\"Legacy Optimizer\",\"type\":\"OPTIMIZER\"}", WorkflowTask.class);

        assertEquals(WorkflowTask.DEFAULT_OPTIMIZER_MODE, task.getOptimizerMode());
        assertEquals(WorkflowTask.DEFAULT_OPTIMIZER_CRITERION, task.getOptimizerCriterion());
        assertEquals(WorkflowTask.DEFAULT_OPTIMIZER_FORWARD_MODE, task.getOptimizerForwardMode());
        assertTrue(task.getOptimizerTargetParameters().isEmpty());
    }

    @Test
    public void legacyOptimizerTaskHasEmptyOutputDirectoryOverride() {
        WorkflowTask task = new com.google.gson.Gson().fromJson(
                "{\"name\":\"Legacy Optimizer\",\"type\":\"OPTIMIZER\"}", WorkflowTask.class);

        assertEquals("", task.getOptimizerOutputDirectory());
    }

    @Test
    public void legacyDiversityTaskUsesSafeDefaults() {
        WorkflowTask task = new com.google.gson.Gson().fromJson(
                "{\"name\":\"Legacy Cluster\",\"type\":\"DIVERSITY_FILTER\"}", WorkflowTask.class);

        assertEquals(WorkflowTask.DEFAULT_DIVERSITY_PARAM_DIFF_PCT, task.getDiversityParamDiffPct(), 0.0);
        assertEquals(WorkflowTask.DEFAULT_DIVERSITY_TRADE_DIFF_PCT, task.getDiversityTradeDiffPct(), 0.0);
        assertEquals(WorkflowTask.DEFAULT_DIVERSITY_MIN_DIFFERENT_PARAMS, task.getDiversityMinDifferentParams());
        assertEquals(WorkflowTask.DEFAULT_DIVERSITY_MAX_STRATEGIES, task.getDiversityMaxStrategies());
        assertFalse(task.isDiversityRankByScore());
    }

    @Test
    public void legacyDualFilterNameIsMigratedToClustering() {
        CustomProject project = new CustomProject("Legacy", "EA.ex5", "EURUSD", "H1");
        project.addTask(new WorkflowTask("Dual- & Diversitäts-Filter", WorkflowTask.TaskType.DIVERSITY_FILTER));

        assertTrue(project.migrateLegacyTaskDefinitions());
        assertEquals("Diversitäts-Clustering", project.getTasks().get(0).getName());
        assertFalse(project.migrateLegacyTaskDefinitions());
    }

    @Test
    public void robustnessTaskSettingsAreTaskSpecificAndPersisted() {
        WorkflowTask task = new WorkflowTask("Robustness CV", WorkflowTask.TaskType.ROBUSTNESS_CV);
        task.setRobustnessSweepPct(0.15);
        task.setRobustnessSteps(20);
        task.setRobustnessTimeShifts(5);
        task.setRobustnessShiftDays(14);
        task.setRobustnessExcludedParams("Inp_Min_Lot, Inp_Max_Lot");

        WorkflowTask copy = task.copyForPersistence();

        assertEquals(0.15, copy.getRobustnessSweepPct(), 0.0001);
        assertEquals(20, copy.getRobustnessSteps());
        assertEquals(5, copy.getRobustnessTimeShifts());
        assertEquals(14, copy.getRobustnessShiftDays());
        assertEquals("Inp_Min_Lot, Inp_Max_Lot", copy.getRobustnessExcludedParams());
    }

    @Test
    public void legacyRobustnessTaskUsesSafeDefaults() {
        WorkflowTask task = new com.google.gson.Gson().fromJson(
                "{\"name\":\"Legacy Robustness\",\"type\":\"ROBUSTNESS_CV\"}", WorkflowTask.class);

        assertEquals(WorkflowTask.DEFAULT_ROBUSTNESS_SWEEP_PCT, task.getRobustnessSweepPct(), 0.0001);
        assertEquals(WorkflowTask.DEFAULT_ROBUSTNESS_STEPS, task.getRobustnessSteps());
        assertEquals(WorkflowTask.DEFAULT_ROBUSTNESS_TIME_SHIFTS, task.getRobustnessTimeShifts());
        assertEquals(WorkflowTask.DEFAULT_ROBUSTNESS_SHIFT_DAYS, task.getRobustnessShiftDays());
        assertEquals("", task.getRobustnessExcludedParams());
    }
}
