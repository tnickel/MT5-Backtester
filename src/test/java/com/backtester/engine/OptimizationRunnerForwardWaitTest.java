package com.backtester.engine;

import com.backtester.config.MetaTraderPlatform;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Guards the forward-report readiness rule used by both shutdown and keep-open
 * wait loops in {@link OptimizationRunner}.
 */
public class OptimizationRunnerForwardWaitTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void readyWithoutForwardWhenForwardModeOff() throws Exception {
        Path mt5Dir = temp.newFolder("mt5").toPath();
        Path report = mt5Dir.resolve("OptimizationReport.xml");
        Files.writeString(report, "<opt/>");

        OptimizationConfig cfg = new OptimizationConfig();
        cfg.setForwardMode(0);

        assertTrue(OptimizationRunner.areOptimizationReportsReady(
                mt5Dir, MetaTraderPlatform.MT5, cfg, report));
    }

    @Test
    public void notReadyWhenForwardModeOnButForwardXmlMissing() throws Exception {
        Path mt5Dir = temp.newFolder("mt5").toPath();
        Path report = mt5Dir.resolve("OptimizationReport.xml");
        Files.writeString(report, "<opt/>");

        OptimizationConfig cfg = new OptimizationConfig();
        cfg.setForwardMode(1);

        assertFalse(OptimizationRunner.areOptimizationReportsReady(
                mt5Dir, MetaTraderPlatform.MT5, cfg, report));
    }

    @Test
    public void readyWhenForwardModeOnAndBothReportsExist() throws Exception {
        Path mt5Dir = temp.newFolder("mt5").toPath();
        Path report = mt5Dir.resolve("OptimizationReport.xml");
        Files.writeString(report, "<opt/>");
        Files.writeString(OptimizationRunner.forwardReportPath(mt5Dir), "<fwd/>");

        OptimizationConfig cfg = new OptimizationConfig();
        cfg.setForwardMode(4);

        assertTrue(OptimizationRunner.areOptimizationReportsReady(
                mt5Dir, MetaTraderPlatform.MT5, cfg, report));
    }

    @Test
    public void notReadyWhenMainReportMissing() throws Exception {
        Path mt5Dir = temp.newFolder("mt5").toPath();
        Path report = mt5Dir.resolve("OptimizationReport.xml");

        OptimizationConfig cfg = new OptimizationConfig();
        cfg.setForwardMode(1);

        assertFalse(OptimizationRunner.areOptimizationReportsReady(
                mt5Dir, MetaTraderPlatform.MT5, cfg, report));
    }
}
