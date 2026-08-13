package com.backtester.ui.javafx;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.backtester.report.BacktestResult;
import javafx.application.Platform;
import javafx.scene.control.DatePicker;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class UiComponentsCoverageTest {

    @BeforeClass
    public static void setUpClass() {
        // Set software rasterizer for VM/headless environments
        System.setProperty("prism.order", "sw");
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Already initialized
        }
    }

    /**
     * These tests check that the views build without blowing up, not how fast they build.
     * The budget therefore only has to rule out a real deadlock on the JavaFX thread —
     * a tight one turns a busy build machine into a red suite, because assembling the
     * larger views takes seconds on the software rasterizer alone.
     */
    private static final long FX_ACTION_TIMEOUT_SECONDS = 90;

    private void runAndWait(Runnable r) {
        final CountDownLatch latch = new CountDownLatch(1);
        final Throwable[] error = new Throwable[1];
        Platform.runLater(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                error[0] = t;
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(FX_ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                fail("JavaFX thread timed out waiting for action to complete");
            }
        } catch (InterruptedException e) {
            fail("Test interrupted: " + e.getMessage());
        }
        if (error[0] != null) {
            if (error[0] instanceof RuntimeException) {
                throw (RuntimeException) error[0];
            } else if (error[0] instanceof Error) {
                throw (Error) error[0];
            } else {
                throw new RuntimeException(error[0]);
            }
        }
    }

    @Test
    public void testHelpView() {
        runAndWait(() -> {
            HelpView view = new HelpView();
            assertNotNull(view.getView());
        });
    }

    @Test
    public void testSettingsView() {
        runAndWait(() -> {
            SettingsView view = new SettingsView();
            try {
                assertNotNull(view.getView());
            } finally {
                view.shutdown();
            }
        });
    }

    @Test
    public void testLogView() {
        runAndWait(() -> {
            LogView view = new LogView();
            assertNotNull(view.getView());
            view.log("INFO", "Test message info");
            view.log("WARN", "Test message warn");
            view.log("ERROR", "Test message error");
            view.log("DEBUG", "Test message debug");
            view.log("MT5", "Test message mt5");
            view.logMessage("An error occurred");
            view.clearLog();
        });
    }

    @Test
    public void datePickerCommitDoesNotRecursivelyFireForEqualParsedValue() {
        runAndWait(() -> {
            try {
                java.lang.reflect.Method commit = ProjectWorkflowEditorView.class
                        .getDeclaredMethod("commitDatePicker", DatePicker.class);
                commit.setAccessible(true);

                LocalDate oldDate = LocalDate.of(2026, 1, 1);
                LocalDate newDate = LocalDate.of(2026, 8, 3);
                DatePicker picker = new DatePicker(oldDate);
                AtomicInteger changes = new AtomicInteger();
                picker.valueProperty().addListener((obs, oldValue, newValue) -> {
                    changes.incrementAndGet();
                    try {
                        commit.invoke(null, picker);
                    } catch (ReflectiveOperationException ex) {
                        throw new RuntimeException(ex);
                    }
                });
                picker.getEditor().setText(picker.getConverter().toString(newDate));

                commit.invoke(null, picker);
                commit.invoke(null, picker);

                assertEquals(newDate, picker.getValue());
                assertEquals("Only the actual date change may fire", 1, changes.get());
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    public void testDukascopyView() {
        runAndWait(() -> {
            LogView logView = new LogView();
            DukascopyView view = new DukascopyView(logView);
            assertNotNull(view.getView());
        });
    }

    @Test
    public void testMainView() {
        runAndWait(() -> {
            MainView view = new MainView();
            assertNotNull(view.getView());
        });
    }

    @Test
    public void testBacktestView() {
        runAndWait(() -> {
            LogView logView = new LogView();
            BacktestView view = new BacktestView(logView);
            assertNotNull(view.getView());
        });
    }

    @Test
    public void testMultiBacktestView() {
        runAndWait(() -> {
            LogView logView = new LogView();
            MultiBacktestView view = new MultiBacktestView(logView);
            assertNotNull(view.getView());
        });
    }

    @Test
    public void testOptimizationView() {
        runAndWait(() -> {
            LogView logView = new LogView();
            OptimizationView view = new OptimizationView(logView);
            assertNotNull(view.getView());
        });
    }

    @Test
    public void testRobustnessView() {
        runAndWait(() -> {
            LogView logView = new LogView();
            OptimizationView optView = new OptimizationView(logView);
            RobustnessView view = new RobustnessView(logView, optView);
            assertNotNull(view.getView());
        });
    }

    @Test
    public void testWorkflowView() {
        runAndWait(() -> {
            LogView logView = new LogView();
            WorkflowView view = new WorkflowView(logView);
            assertNotNull(view.getView());
        });
    }

    @Test
    public void testHistoryView() {
        runAndWait(() -> {
            LogView logView = new LogView();
            WorkflowView workflowView = new WorkflowView(logView);
            HistoryView view = new HistoryView(workflowView);
            assertNotNull(view.getView());
        });
    }

    @Test
    public void testDocHelper() {
        runAndWait(() -> {
            assertNotNull(DocHelper.createHeaderWithTooltip("Score", "Tooltip text"));
            assertNotNull(DocHelper.createHeaderWithTooltip("Other", "Tooltip text"));
            assertNotNull(DocHelper.createInfoButton("Tab", "Overview", "Details"));
            assertNotNull(DocHelper.createSmallInfoButton("Tab", "Overview", "Details"));
            assertNotNull(DocHelper.createSmallInfoButton(() -> {}));
            assertNotNull(DocHelper.createSmallInfoButton("tooltip", () -> {}));
            assertNotNull(DocHelper.createThickCircularInfoButton("tooltip", () -> {}));
            assertNotNull(DocHelper.createThickCircularCyanInfoButton("tooltip", () -> {}));
            assertNotNull(DocHelper.createControllingInfoButton(null));
            assertNotNull(DocHelper.getScoreDocHtml());
            assertNotNull(DocHelper.getConsistencyDocHtml());
            assertNotNull(DocHelper.getAllIndicesDocHtml());
            assertNotNull(DocHelper.getDiversityDocHtml());
            String customDiversityHelp = DocHelper.getCustomProjectDiversityDocHtml();
            assertTrue(customDiversityHelp.contains("genau eine Quell-Databank"));
            assertTrue(customDiversityHelp.contains("aktivierter Score-Sortierung"));
            assertTrue(customDiversityHelp.contains("Min. differente Parameter"));
            assertNotNull(DocHelper.getControllingDocHtml());
        });
    }

    @Test
    public void testStrategyEvaluatorDialog() {
        runAndWait(() -> {
            List<CombinedPass> list = new ArrayList<>();
            Pass bt = new Pass();
            bt.setPassNumber(1);
            bt.setProfit(100.0);
            bt.setTotalTrades(10);
            bt.setProfitFactor(1.5);
            bt.setDrawdownPercent(5.0);
            bt.setExpectedPayoff(10.0);
            bt.setRecoveryFactor(2.0);
            bt.setSharpeRatio(1.2);

            Pass fw = new Pass();
            fw.setPassNumber(1);
            fw.setProfit(80.0);
            fw.setTotalTrades(8);
            fw.setProfitFactor(1.4);
            fw.setDrawdownPercent(6.0);
            fw.setExpectedPayoff(10.0);
            fw.setRecoveryFactor(1.8);
            fw.setSharpeRatio(1.1);

            CombinedPass cp = new CombinedPass(bt, fw, 90.0, 1.0, "EURUSD");
            list.add(cp);

            LogView logView = new LogView();
            StrategyEvaluatorDialog dialog = new StrategyEvaluatorDialog(list, null);
            assertNotNull(dialog);
        });
    }

    @Test
    public void testProgressBarStyling() throws Exception {
        runAndWait(() -> {
            try {
                javafx.scene.control.ProgressBar pb = new javafx.scene.control.ProgressBar(0.8);
                pb.setId("workflow-progress-bar");
                pb.setPrefWidth(300);
                javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(pb);
                javafx.scene.Scene scene = new javafx.scene.Scene(root, 400, 100);
                java.net.URL cssUrl = getClass().getResource("/css/antigravity.css");
                assertNotNull("CSS URL should not be null", cssUrl);
                scene.getStylesheets().add(cssUrl.toExternalForm());
                root.applyCss();
                root.layout();
                
                System.out.println("==================================================");
                System.out.println("TESTING PROGRESSBAR:");
                System.out.println("pb width: " + pb.getWidth() + ", height: " + pb.getHeight());
                for (javafx.scene.Node node : pb.lookupAll("*")) {
                    System.out.println("Child: " + node.getTypeSelector() + ", styleClass: " + node.getStyleClass() + ", visible: " + node.isVisible() + ", bounds: " + node.getBoundsInParent());
                }
                System.out.println("==================================================");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
