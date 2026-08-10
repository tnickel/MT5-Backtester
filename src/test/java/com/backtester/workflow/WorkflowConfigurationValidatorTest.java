package com.backtester.workflow;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class WorkflowConfigurationValidatorTest {

    @Test
    public void warnsWhenTwoRetestersShareSameTargetTab() {
        WorkflowTask longterm = task("Langzeittest", WorkflowTask.TaskType.RETESTER,
                "Results", "ticktest");
        WorkflowTask oos = task("OOS-Retest", WorkflowTask.TaskType.RETESTER,
                "Results", "ticktest");

        List<WorkflowConfigurationValidator.RetesterOverwriteRisk> risks =
                WorkflowConfigurationValidator.findRetesterOverwriteRisks(
                        List.of(longterm, oos));

        assertEquals(1, risks.size());
        assertEquals(oos, risks.get(0).task());
        assertEquals("ticktest", risks.get(0).targetDatabank());
        assertEquals(List.of("Langzeittest"), risks.get(0).upstreamRetesterNames());
    }

    @Test
    public void acceptsRetestersWithSeparateTargetTabs() {
        WorkflowTask longterm = task("Langzeittest", WorkflowTask.TaskType.RETESTER,
                "Results", "Longterm");
        WorkflowTask clustering = task("Langzeit-Clustering", WorkflowTask.TaskType.DIVERSITY_FILTER,
                "Longterm", "LongtermClustered");
        WorkflowTask oos = task("OOS-Retest", WorkflowTask.TaskType.RETESTER,
                "LongtermClustered", "Final");

        assertTrue(WorkflowConfigurationValidator.findRetesterOverwriteRisks(
                List.of(longterm, clustering, oos)).isEmpty());
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
    public void disabledRetesterDoesNotOccupyTargetTab() {
        WorkflowTask disabled = task("Deaktiviert", WorkflowTask.TaskType.RETESTER,
                "Results", "Results");
        disabled.setEnabled(false);
        WorkflowTask oos = task("OOS-Retest", WorkflowTask.TaskType.RETESTER,
                "Results", "Results");

        assertTrue(WorkflowConfigurationValidator.findRetesterOverwriteRisks(
                List.of(disabled, oos)).isEmpty());
    }

    @Test
    public void defaultTemplateHasNoSameTargetOverwriteRisk() {
        CustomProject project = CustomProject.createDefaultTemplate(
                "Project", "EA", "EURUSD", "H1");

        // Langzeittest → Results, Validierung → Final (separate tabs)
        assertTrue(WorkflowConfigurationValidator.findRetesterOverwriteRisks(
                project.getTasks()).isEmpty());
    }

    @Test
    public void acceptsNewTargetDatabanksAndMakesThemAvailableToFollowingTasks() {
        WorkflowTask optimizer = task("Optimizer", WorkflowTask.TaskType.OPTIMIZER,
                "Results", "stage_raw");
        WorkflowTask filter = task("Filter", WorkflowTask.TaskType.PRE_FILTER,
                "stage_raw", "stage_pick");
        WorkflowTask nextOptimizer = task("Next optimizer", WorkflowTask.TaskType.OPTIMIZER,
                "stage_pick", "next_raw");

        WorkflowConfigurationValidator.validateDatabankExecutionOrder(
                List.of(optimizer, filter, nextOptimizer), List.of("Results"));
    }

    @Test
    public void rejectsSourceDatabankThatIsNeitherExistingNorProducedEarlier() {
        WorkflowTask filter = task("Filter", WorkflowTask.TaskType.PRE_FILTER,
                "missing_raw", "stage_pick");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> WorkflowConfigurationValidator.validateDatabankExecutionOrder(
                        List.of(filter), List.of("Results")));

        assertTrue(error.getMessage().contains("missing_raw"));
    }

    @Test
    public void rejectsSourceProducedOnlyByALaterTask() {
        WorkflowTask prematureConsumer = task("Premature filter", WorkflowTask.TaskType.PRE_FILTER,
                "stage_raw", "stage_pick");
        WorkflowTask lateProducer = task("Late optimizer", WorkflowTask.TaskType.OPTIMIZER,
                "Results", "stage_raw");

        assertThrows(IllegalStateException.class,
                () -> WorkflowConfigurationValidator.validateDatabankExecutionOrder(
                        List.of(prematureConsumer, lateProducer), List.of("Results")));
    }

    @Test
    public void disabledProducerDoesNotMakeItsTargetAvailable() {
        WorkflowTask disabledProducer = task("Disabled", WorkflowTask.TaskType.OPTIMIZER,
                "Results", "stage_raw");
        disabledProducer.setEnabled(false);
        WorkflowTask consumer = task("Consumer", WorkflowTask.TaskType.PRE_FILTER,
                "stage_raw", "stage_pick");

        assertThrows(IllegalStateException.class,
                () -> WorkflowConfigurationValidator.validateDatabankExecutionOrder(
                        List.of(disabledProducer, consumer), List.of("Results")));
    }

    @Test
    public void optimizerDoesNotRequireAnExistingSourceAndProducesItsTarget() {
        WorkflowTask optimizer = task("Optimizer", WorkflowTask.TaskType.OPTIMIZER,
                "irrelevant_missing_source", "stage_raw");
        WorkflowTask consumer = task("Consumer", WorkflowTask.TaskType.PRE_FILTER,
                "stage_raw", "stage_pick");

        WorkflowConfigurationValidator.validateDatabankExecutionOrder(
                List.of(optimizer, consumer), List.of("Results"));
    }

    @Test
    public void strategySelectionAndPortfolioExportDoNotProducePhantomTargets() {
        WorkflowTask selection = task("Selection", WorkflowTask.TaskType.STRATEGY_SELECTION,
                "missing_source", "selection_phantom");
        WorkflowTask export = task("Export", WorkflowTask.TaskType.PORTFOLIO_EXPORT,
                "Results", "export_phantom");
        WorkflowTask selectionConsumer = task("Selection consumer", WorkflowTask.TaskType.PRE_FILTER,
                "selection_phantom", "output");
        WorkflowTask exportConsumer = task("Export consumer", WorkflowTask.TaskType.PRE_FILTER,
                "export_phantom", "output");

        assertThrows(IllegalStateException.class,
                () -> WorkflowConfigurationValidator.validateDatabankExecutionOrder(
                        List.of(selection, selectionConsumer), List.of("Results")));
        assertThrows(IllegalStateException.class,
                () -> WorkflowConfigurationValidator.validateDatabankExecutionOrder(
                        List.of(export, exportConsumer), List.of("Results")));
    }

    @Test
    public void portfolioExportStillRequiresItsSource() {
        WorkflowTask export = task("Export", WorkflowTask.TaskType.PORTFOLIO_EXPORT,
                "missing_source", "ignored_target");

        assertThrows(IllegalStateException.class,
                () -> WorkflowConfigurationValidator.validateDatabankExecutionOrder(
                        List.of(export), List.of("Results")));
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
