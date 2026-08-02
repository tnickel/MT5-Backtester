package com.backtester.workflow;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WorkflowConfigurationValidatorTest {

    @Test
    public void warnsWhenSecondRetesterConsumesFirstRetesterThroughIntermediateTask() {
        WorkflowTask longterm = task("Langzeittest", WorkflowTask.TaskType.RETESTER,
                "Results", "Longterm");
        WorkflowTask clustering = task("Langzeit-Clustering", WorkflowTask.TaskType.DIVERSITY_FILTER,
                "Longterm", "LongtermClustered");
        WorkflowTask oos = task("OOS-Retest", WorkflowTask.TaskType.RETESTER,
                "LongtermClustered", "Final");

        List<WorkflowConfigurationValidator.RetesterOverwriteRisk> risks =
                WorkflowConfigurationValidator.findRetesterOverwriteRisks(
                        List.of(longterm, clustering, oos));

        assertEquals(1, risks.size());
        assertEquals(oos, risks.get(0).task());
        assertEquals("LongtermClustered", risks.get(0).sourceDatabank());
        assertEquals(List.of("Langzeittest"), risks.get(0).upstreamRetesterNames());
    }

    @Test
    public void acceptsRetestersThatReadFromIndependentSourceBranch() {
        WorkflowTask longterm = task("Langzeittest", WorkflowTask.TaskType.RETESTER,
                "Results", "Longterm");
        WorkflowTask oos = task("OOS-Retest", WorkflowTask.TaskType.RETESTER,
                "Results", "Final");

        assertTrue(WorkflowConfigurationValidator.findRetesterOverwriteRisks(
                List.of(longterm, oos)).isEmpty());
    }

    @Test
    public void disabledRetesterDoesNotTaintDownstreamDatabank() {
        WorkflowTask disabled = task("Deaktiviert", WorkflowTask.TaskType.RETESTER,
                "Results", "Results");
        disabled.setEnabled(false);
        WorkflowTask oos = task("OOS-Retest", WorkflowTask.TaskType.RETESTER,
                "Results", "Final");

        assertTrue(WorkflowConfigurationValidator.findRetesterOverwriteRisks(
                List.of(disabled, oos)).isEmpty());
    }

    @Test
    public void defaultTemplateWarnsAboutOosRetesterConsumingLongtermPath() {
        CustomProject project = CustomProject.createDefaultTemplate(
                "Project", "EA", "EURUSD", "H1");

        List<WorkflowConfigurationValidator.RetesterOverwriteRisk> risks =
                WorkflowConfigurationValidator.findRetesterOverwriteRisks(project.getTasks());

        assertEquals(1, risks.size());
        assertEquals("Validierung (OOS)", risks.get(0).task().getName());
        assertTrue(risks.get(0).upstreamRetesterNames().contains("Langzeittest (5-10 Jahre)"));
    }

    private static WorkflowTask task(String name,
                                     WorkflowTask.TaskType type,
                                     String source,
                                     String target) {
        WorkflowTask task = new WorkflowTask(name, type);
        task.setSourceDatabank(source);
        task.setTargetDatabank(target);
        return task;
    }
}
