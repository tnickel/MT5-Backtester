package com.backtester.ui.javafx;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProjectWorkflowProgressContextTest {

    @Test
    public void formatsStageTaskLineAndLiveCountWithoutParsingDetailText() {
        ProjectWorkflowPipelineRunner.ProgressContext context =
                ProjectWorkflowPipelineRunner.ProgressContext
                        .stage(18, 46, "05 ADX-Regime — Optimizer", 7)
                        .withLine("B1", 1, 7);

        assertEquals("Stufe 18 / 46 · 05 ADX-Regime — Optimizer"
                        + " | Linie B1 · 1 / 7 | 7 derzeit aktive Linien",
                context.displayText());
    }

    @Test
    public void formatsNonClusterStageWithoutLineSuffix() {
        ProjectWorkflowPipelineRunner.ProgressContext context =
                ProjectWorkflowPipelineRunner.ProgressContext
                        .stage(6, 46, "Tick-Gate", -1);

        assertEquals("Stufe 6 / 46 · Tick-Gate", context.displayText());
    }

    @Test
    public void formatsRetesterStrategyProgressInBanner() {
        ProjectWorkflowPipelineRunner.ProgressContext context =
                ProjectWorkflowPipelineRunner.ProgressContext
                        .stage(8, 33, "3J-OHLC-Gate", -1)
                        .withStrategies(3, 15);

        assertEquals("Stufe 8 / 33 · 3J-OHLC-Gate | Strategie 3 / 15",
                context.displayText());
    }

    @Test
    public void updatesLiveLineCountAndUsesSingular() {
        ProjectWorkflowPipelineRunner.ProgressContext context =
                ProjectWorkflowPipelineRunner.ProgressContext
                        .stage(18, 46, "ADX", 7)
                        .withLine("B7", 7, 7)
                        .withLiveLines(1);

        assertEquals("Stufe 18 / 46 · ADX | Linie B7 · 7 / 7 | 1 derzeit aktive Linie",
                context.displayText());
    }

    @Test
    public void appendsTerminalOutcomeAndClearsLineProgress() {
        ProjectWorkflowPipelineRunner.ProgressContext context =
                ProjectWorkflowPipelineRunner.ProgressContext
                        .stage(18, 46, "ADX", 7)
                        .withLine("B2", 2, 7)
                        .withOutcome("FEHLGESCHLAGEN");

        assertEquals("Stufe 18 / 46 · ADX — FEHLGESCHLAGEN | 7 lebende Linien",
                context.displayText());
    }
}
