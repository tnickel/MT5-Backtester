package com.backtester.report;

import com.backtester.engine.BacktestConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BacktestArtifactReplayResolverTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void resolvesExactTesterConfigurationAndArchivedPreset() throws IOException {
        Path reports = tempFolder.newFolder("reports").toPath();
        Path artifact = reports.resolve("exact-run");
        Files.createDirectories(artifact);
        Files.writeString(artifact.resolve("tester.ini"),
                "[Tester]\n"
                        + "Expert=TestEA\n"
                        + "ExpertParameters=Longterm_Pass11180.set\n"
                        + "Symbol=AUDCAD\n"
                        + "Period=M5\n"
                        + "Model=4\n"
                        + "ExecutionMode=-1\n"
                        + "FromDate=2022.08.01\n"
                        + "ToDate=2026.08.01\n"
                        + "Deposit=25000\n"
                        + "Currency=EUR\n"
                        + "Leverage=1:200\n",
                StandardCharsets.UTF_8);
        Path snapshot = artifact.resolve(BacktestArtifactReplayResolver.PRESET_SNAPSHOT_FILE);
        Files.writeString(snapshot, "Inp_Preset_Factor=1200.0||||||||N\n", StandardCharsets.UTF_8);

        BacktestArtifactReplayResolver.Replay replay =
                BacktestArtifactReplayResolver.resolve(reports, "exact-run", 11180);
        BacktestConfig config = replay.config();

        assertEquals("TestEA", config.getExpert());
        assertEquals("Longterm_Pass11180.set", replay.originalPresetName());
        assertEquals("AUDCAD", config.getSymbol());
        assertEquals("M5", config.getPeriod());
        assertEquals(4, config.getModel());
        assertEquals(-1, config.getExecutionMode());
        assertEquals("2022-08-01", config.getFromDate().toString());
        assertEquals("2026-08-01", config.getToDate().toString());
        assertEquals(25000, config.getDeposit());
        assertEquals("EUR", config.getCurrency());
        assertEquals("1:200", config.getLeverage());
        assertEquals(snapshot, replay.presetSource());
        assertTrue(!config.isShutdownTerminal());
    }

    @Test
    public void rejectsArtifactPathTraversal() throws IOException {
        Path reports = tempFolder.newFolder("traversal-reports").toPath();
        assertThrows(IOException.class,
                () -> BacktestArtifactReplayResolver.resolve(reports, "..\\outside", 11180));
    }

    @Test
    public void rejectsPresetForDifferentPass() throws IOException {
        Path reports = tempFolder.newFolder("wrong-pass-reports").toPath();
        Path artifact = reports.resolve("wrong-pass");
        Files.createDirectories(artifact);
        Files.writeString(artifact.resolve("tester.ini"),
                "Expert=TestEA\nExpertParameters=Longterm_Pass7.set\n"
                        + "Symbol=AUDCAD\nPeriod=M5\nModel=0\nExecutionMode=0\n"
                        + "FromDate=2022.08.01\nToDate=2026.08.01\n"
                        + "Deposit=10000\nCurrency=USD\nLeverage=1:100\n",
                StandardCharsets.UTF_8);
        Files.writeString(artifact.resolve(BacktestArtifactReplayResolver.PRESET_SNAPSHOT_FILE),
                "x=1\n", StandardCharsets.UTF_8);

        IOException error = assertThrows(IOException.class,
                () -> BacktestArtifactReplayResolver.resolve(reports, "wrong-pass", 11180));
        assertTrue(error.getMessage().contains("Pass #11180"));
    }
}
