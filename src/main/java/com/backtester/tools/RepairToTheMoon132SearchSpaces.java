package com.backtester.tools;

import com.backtester.database.DatabaseManager;
import com.backtester.workflow.CustomProject;
import com.backtester.workflow.ToTheMoon132GuidedWorkflowFactory;
import com.backtester.workflow.WorkflowTask;

import java.util.List;

/**
 * One-shot repair for persisted Guided ToTheMoon132 projects whose optimizer
 * stages have the wrong search space. Run:
 * {@code mvn -q -DskipTests exec:java -Dexec.mainClass=com.backtester.tools.RepairToTheMoon132SearchSpaces}
 */
public final class RepairToTheMoon132SearchSpaces {
    private RepairToTheMoon132SearchSpaces() {}

    public static void main(String[] args) {
        DatabaseManager db = DatabaseManager.getInstance();
        List<CustomProject> projects = db.getAllCustomProjects();
        int repaired = 0;
        for (CustomProject project : projects) {
            if (project == null) continue;
            boolean changed = ToTheMoon132GuidedWorkflowFactory.repairStageOptimizerSearchSpaces(project);
            if (!changed) {
                System.out.println("OK (no change): " + project.getName());
                continue;
            }
            if (!db.saveCustomProject(project)) {
                System.err.println("FAILED to save: " + project.getName());
                continue;
            }
            repaired++;
            System.out.println("REPAIRED: " + project.getName());
            for (WorkflowTask task : project.getTasks()) {
                if (task == null || task.getType() != WorkflowTask.TaskType.OPTIMIZER) continue;
                System.out.println("  - " + task.getName() + " -> " + task.getOptimizerTargetParameters());
            }
        }
        System.out.println("Done. Projects repaired: " + repaired + " / " + projects.size());
    }
}
