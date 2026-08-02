package com.backtester.workflow;

import com.backtester.database.DatabaseManager;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class CustomProjectTest {

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

        WorkflowTask copy = task.copyForPersistence();

        assertEquals(0.22, copy.getDiversityParamDiffPct(), 0.0);
        assertEquals(0.31, copy.getDiversityTradeDiffPct(), 0.0);
        assertEquals(4, copy.getDiversityMinDifferentParams());
        assertEquals(17, copy.getDiversityMaxStrategies());
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
        task.setSensitivityRunTimestamp(123456789L);

        WorkflowTask copy = task.copyForPersistence();

        assertEquals(1, copy.getOptimizerMode());
        assertEquals(7, copy.getOptimizerCriterion());
        assertEquals(4, copy.getOptimizerForwardMode());
        assertEquals("2026-05-01", copy.getOptimizerForwardDate());
        assertEquals(123456789L, copy.getSensitivityRunTimestamp());
    }

    @Test
    public void legacyOptimizerTaskUsesCanonicalSafeDefaults() {
        WorkflowTask task = new com.google.gson.Gson().fromJson(
                "{\"name\":\"Legacy Optimizer\",\"type\":\"OPTIMIZER\"}", WorkflowTask.class);

        assertEquals(WorkflowTask.DEFAULT_OPTIMIZER_MODE, task.getOptimizerMode());
        assertEquals(WorkflowTask.DEFAULT_OPTIMIZER_CRITERION, task.getOptimizerCriterion());
        assertEquals(WorkflowTask.DEFAULT_OPTIMIZER_FORWARD_MODE, task.getOptimizerForwardMode());
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
    }

    @Test
    public void legacyDualFilterNameIsMigratedToClustering() {
        CustomProject project = new CustomProject("Legacy", "EA.ex5", "EURUSD", "H1");
        project.addTask(new WorkflowTask("Dual- & Diversitäts-Filter", WorkflowTask.TaskType.DIVERSITY_FILTER));

        assertTrue(project.migrateLegacyTaskDefinitions());
        assertEquals("Diversitäts-Clustering", project.getTasks().get(0).getName());
        assertFalse(project.migrateLegacyTaskDefinitions());
    }
}
