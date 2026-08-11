package com.backtester.workflow;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorkflowRunArtifactCleanupServiceTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void cacheFileMatchesExpertPrefix() {
        assertTrue(WorkflowRunArtifactCleanupService.cacheFileMatchesExpert(
                "ToTheMoon_KI_v132.AUDCAD.M5.20220801.opt", "ToTheMoon_KI_v132"));
        assertFalse(WorkflowRunArtifactCleanupService.cacheFileMatchesExpert(
                "OtherEA.AUDCAD.M5.opt", "ToTheMoon_KI_v132"));
        assertFalse(WorkflowRunArtifactCleanupService.cacheFileMatchesExpert(
                "ToTheMoon_KI_v132.AUDCAD.M5.txt", "ToTheMoon_KI_v132"));
    }

    @Test
    public void deletesMatchingTesterCacheAndReports() throws Exception {
        Path mtDir = temp.newFolder("mt5").toPath();
        Path cache = mtDir.resolve("Tester").resolve("cache");
        Files.createDirectories(cache);
        Path keep = cache.resolve("OtherEA.AUDCAD.opt");
        Path drop = cache.resolve("ToTheMoon_KI_v132.AUDCAD.M5.opt");
        Files.writeString(keep, "x");
        Files.writeString(drop, "y");
        Path report = mtDir.resolve("OptimizationReport.xml");
        Path forward = mtDir.resolve("OptimizationReport.forward.xml");
        Files.writeString(report, "<xml/>");
        Files.writeString(forward, "<xml/>");

        List<String> details = new ArrayList<>();
        int cacheDeleted = WorkflowRunArtifactCleanupService.deleteTesterCacheForExpert(
                mtDir, "ToTheMoon_KI_v132", details);
        int reportsDeleted = WorkflowRunArtifactCleanupService.deleteOptimizationReportFiles(mtDir, details);

        assertEquals(1, cacheDeleted);
        assertEquals(2, reportsDeleted);
        assertTrue(Files.exists(keep));
        assertFalse(Files.exists(drop));
        assertFalse(Files.exists(report));
        assertFalse(Files.exists(forward));
    }

    @Test
    public void clearsOptimizerOutputChildrenButKeepsRoot() throws Exception {
        Path out = temp.newFolder("g01_grid").toPath();
        Path run = out.resolve("OPT_run1");
        Files.createDirectories(run);
        Files.writeString(run.resolve("optimization_report.xml"), "<xml/>");

        CustomProject project = new CustomProject("p", "ToTheMoon_KI_v132", "AUDCAD", "M5");
        WorkflowTask opt = new WorkflowTask("opt", WorkflowTask.TaskType.OPTIMIZER);
        opt.setOptimizerOutputDirectory(out.toString());
        project.addTask(opt);

        List<String> details = new ArrayList<>();
        int trees = WorkflowRunArtifactCleanupService.deleteOptimizerOutputDirectories(project, details);

        assertEquals(1, trees);
        assertTrue(Files.isDirectory(out));
        assertFalse(Files.exists(run));
    }
}
