package com.backtester.config;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.*;

/**
 * End-to-End Test for loading EA parameter configs (.set file),
 * populating parameter tables with section headers, transporting to MetaTrader format,
 * and re-verifying exported setfile integrity.
 */
public class EaParameterSetfileTransportTest {

    private static final String SOURCE_SETFILE_PATH = "C:\\tmp\\132_AUDCAD_tnickel_set1_optimize.set";
    private static EaParameterManager manager;

    @BeforeClass
    public static void setUp() {
        manager = new EaParameterManager();
    }

    @Test
    public void testSetfileLoadDisplayTransportAndVerification() throws Exception {
        // Step 1: Load config via EaParameterManager
        List<EaParameter> loadedParams = manager.readSetFile(sourceSetFile());
        assertNotNull("Loaded parameter list must not be null", loadedParams);
        assertFalse("Loaded parameter list must not be empty", loadedParams.isEmpty());

        // Step 2: Verify parameter table contents and MetaTrader-style section headers
        long sectionHeaderCount = loadedParams.stream().filter(EaParameter::isSectionHeader).count();
        assertTrue("Config should contain section header rows", sectionHeaderCount > 0);

        // Check specific section headers
        boolean hasMoneyManageHeader = loadedParams.stream()
                .anyMatch(p -> p.isSectionHeader() && p.getFormattedSectionTitle().contains("MONEY MANAGE"));
        assertTrue("Table should contain 'MONEY MANAGE' section header row", hasMoneyManageHeader);

        boolean hasGridModeHeader = loadedParams.stream()
                .anyMatch(p -> p.isSectionHeader() && p.getFormattedSectionTitle().contains("GRID MODE"));
        assertTrue("Table should contain 'GRID MODE' section header row", hasGridModeHeader);

        // Check regular parameter values and optimization settings
        EaParameter initialLot = loadedParams.stream()
                .filter(p -> "Inp_Initial_Lot".equals(p.getName()))
                .findFirst().orElse(null);
        assertNotNull("Parameter Inp_Initial_Lot must exist", initialLot);
        assertEquals("0.01", initialLot.getValue());
        assertFalse("Inp_Initial_Lot optimization should be N/disabled", initialLot.isOptimizeEnabled());

        EaParameter stepMult = loadedParams.stream()
                .filter(p -> "Inp_Step_Multiplier".equals(p.getName()))
                .findFirst().orElse(null);
        assertNotNull("Parameter Inp_Step_Multiplier must exist", stepMult);
        assertEquals("1.1", stepMult.getValue());
        assertEquals("1.15", stepMult.getOptimizeStart());
        assertEquals("0.115000", stepMult.getOptimizeStep());
        assertEquals("11.500000", stepMult.getOptimizeEnd());
        assertTrue("Inp_Step_Multiplier optimization should be Y/enabled", stepMult.isOptimizeEnabled());

        // Step 3: Transport / Export to MetaTrader format (.set file)
        Path tempExport = Files.createTempFile("MT5_Transport_Test_", ".set");
        manager.writeSetFile(tempExport, loadedParams, "ToTheMoon_KI_v132");

        assertTrue("Exported MT5 setfile must exist", Files.exists(tempExport));
        assertTrue("Exported MT5 setfile size must be greater than 0", Files.size(tempExport) > 0);

        // Step 4: Re-read exported setfile and verify full table integrity
        List<EaParameter> reloadedParams = manager.readSetFile(tempExport);
        assertNotNull("Reloaded exported parameters must not be null", reloadedParams);
        assertEquals("Reloaded parameter count must match original table size", loadedParams.size(), reloadedParams.size());

        // Verify section header continuity
        long reloadedSectionHeaderCount = reloadedParams.stream().filter(EaParameter::isSectionHeader).count();
        assertEquals("Reloaded section header count must match original", sectionHeaderCount, reloadedSectionHeaderCount);

        // Verify parameter value matching
        EaParameter reloadedInitialLot = reloadedParams.stream()
                .filter(p -> "Inp_Initial_Lot".equals(p.getName()))
                .findFirst().orElse(null);
        assertNotNull(reloadedInitialLot);
        assertEquals(initialLot.getValue(), reloadedInitialLot.getValue());

        EaParameter reloadedStepMult = reloadedParams.stream()
                .filter(p -> "Inp_Step_Multiplier".equals(p.getName()))
                .findFirst().orElse(null);
        assertNotNull(reloadedStepMult);
        assertEquals(stepMult.getValue(), reloadedStepMult.getValue());
        assertEquals(stepMult.getOptimizeStart(), reloadedStepMult.getOptimizeStart());
        assertEquals(stepMult.getOptimizeStep(), reloadedStepMult.getOptimizeStep());
        assertEquals(stepMult.getOptimizeEnd(), reloadedStepMult.getOptimizeEnd());
        assertEquals(stepMult.isOptimizeEnabled(), reloadedStepMult.isOptimizeEnabled());

        // Clean up temp file
        Files.deleteIfExists(tempExport);
    }

    /**
     * Writes the parameter set into the real MetaTrader profile directory and opens a
     * terminal for visual inspection. Overwrites {@code config/ea_params/*.set} and
     * leaves the terminal running, so it stays opt-in:
     * {@code mvn test -Dbacktester.liveMt5=true}.
     */
    @Test
    public void liveTransportIntoTheRealTerminal() throws Exception {
        Assume.assumeTrue("Opt-in via -Dbacktester.liveMt5=true — starts a real MT5 terminal",
                Boolean.getBoolean("backtester.liveMt5"));

        List<EaParameter> loadedParams = manager.readSetFile(sourceSetFile());
        String expertPath = "ToTheMoon_KI_v132";
        manager.saveCustomParameters(expertPath, loadedParams);
        String preparedSetFileName = manager.prepareForBacktest(expertPath);

        assertNotNull("Prepared setfile name should not be null", preparedSetFileName);

        AppConfig config = AppConfig.getInstance();
        String terminalPath = config.getTerminalPath(expertPath);
        if (terminalPath == null || !new File(terminalPath).exists()) return;

        java.util.List<String> mt5Args = new java.util.ArrayList<>();
        if (config.isPortableMode()) {
            mt5Args.add("/portable");
        }
        Process liveMt5Process = com.backtester.engine.VirtualDesktopHelper.startOnDesktop2(
                terminalPath, mt5Args, Paths.get(terminalPath).getParent());
        if (liveMt5Process != null) {
            System.out.println("Live MT5 process launched with PID: " + liveMt5Process.pid());
        }
    }

    /** The developer's local setfile when present, a temporary equivalent otherwise. */
    private static Path sourceSetFile() throws Exception {
        File setFile = new File(SOURCE_SETFILE_PATH);
        if (setFile.exists()) return setFile.toPath();

        Path tempSource = Files.createTempFile("test_132_AUDCAD_", ".set");
        String sampleContent =
            "; saved on 2026.08.08 14:57:54\n" +
            "; ---- INITIAL DATA ----\n" +
            "Inp_Version=132||132||1||1320||N\n" +
            "Inp_Order_Comment=1proz_Pass11429\n" +
            "; ---- MONEY MANAGE ----\n" +
            "Inp_Initial_Lot=0.01||0.01||0.001000||0.100000||N\n" +
            "Inp_Min_Lot=0.01||0.01||0.001000||0.100000||N\n" +
            "Inp_Max_Lot=0.1||0.1||0.010000||1.000000||N\n" +
            "; ---- GRID MODE ----\n" +
            "Inp_Grid_Step=725||600||1||6000||N\n" +
            "Inp_Step_Multiplier=1.1||1.15||0.115000||11.500000||Y\n" +
            "; ---- INDICATOR ENVELOPES (UPPER) ----\n" +
            "TimeFrame_Envelopes=1||1||0||49153||Y\n" +
            "Inp_Envelopes_Period=5||5||1||20||Y\n";
        Files.write(tempSource, sampleContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return tempSource;
    }
}
