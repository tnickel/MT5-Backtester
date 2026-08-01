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
        assertEquals(WorkflowTask.TaskType.OOS_VALIDATION, proj.getTasks().get(7).getType());
        assertEquals(WorkflowTask.TaskType.PORTFOLIO_EXPORT, proj.getTasks().get(8).getType());
        assertEquals("Final", proj.getTasks().get(8).getTargetDatabank());
    }

    @Test
    public void testTaskReordering() {
        CustomProject proj = new CustomProject("Reorder Test", "EA.ex5", "GBPUSD", "M15");
        WorkflowTask t1 = new WorkflowTask("Task 1", WorkflowTask.TaskType.OPTIMIZER);
        WorkflowTask t2 = new WorkflowTask("Task 2", WorkflowTask.TaskType.LONGTERM_RETEST);

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
        WorkflowTask task = new WorkflowTask("Retest", WorkflowTask.TaskType.LONGTERM_RETEST);

        task.setExecutionMode(WorkflowTask.MODE_EVERY_TICK);
        assertEquals(0, task.getMt5Model());
        task.setExecutionMode(WorkflowTask.MODE_OHLC_M1);
        assertEquals(1, task.getMt5Model());
        task.setExecutionMode(WorkflowTask.MODE_REAL_TICKS);
        assertEquals(4, task.getMt5Model());
        task.setExecutionMode(WorkflowTask.MODE_OPEN_PRICES);
        assertEquals(2, task.getMt5Model());
    }
}
