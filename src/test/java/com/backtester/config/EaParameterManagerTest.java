package com.backtester.config;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class EaParameterManagerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private String originalUserDir;
    private AppConfig originalInstance;

    @Before
    public void setUp() throws Exception {
        originalUserDir = System.getProperty("user.dir");

        // Reset AppConfig singleton to prevent cross-test contamination
        Field instanceField = AppConfig.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        originalInstance = (AppConfig) instanceField.get(null);
        instanceField.set(null, null);

        // Set user.dir to the temp folder root for isolated AppConfig base directory
        System.setProperty("user.dir", tempFolder.getRoot().getAbsolutePath());
    }

    @After
    public void tearDown() throws Exception {
        System.setProperty("user.dir", originalUserDir);
        // Restore original AppConfig singleton
        Field instanceField = AppConfig.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, originalInstance);
    }

    @Test
    public void testExtractEaBaseName() {
        assertEquals("MyEA", EaParameterManager.extractEaBaseName("Experts\\Advisors\\MyEA.ex5"));
        assertEquals("SuperRobot", EaParameterManager.extractEaBaseName("SuperRobot"));
        assertEquals("Robot_v2", EaParameterManager.extractEaBaseName("Experts/MyFolder/Robot_v2.ex5"));
        assertEquals("", EaParameterManager.extractEaBaseName(""));
        assertEquals("", EaParameterManager.extractEaBaseName(null));
    }

    @Test
    public void testCalculateTotalPasses() {
        EaParameterManager manager = new EaParameterManager();
        List<EaParameter> params = new ArrayList<>();

        EaParameter p1 = new EaParameter();
        p1.setOptimizeEnabled(true);
        p1.setStringType(false);
        p1.setOptimizeStart("10");
        p1.setOptimizeStep("5");
        p1.setOptimizeEnd("30"); // 10, 15, 20, 25, 30 -> 5 passes
        params.add(p1);

        EaParameter p2 = new EaParameter();
        p2.setOptimizeEnabled(true);
        p2.setStringType(false);
        p2.setValue("true"); // Boolean: 2 passes
        params.add(p2);

        EaParameter p3 = new EaParameter();
        p3.setOptimizeEnabled(false); // Should be ignored
        p3.setStringType(false);
        p3.setOptimizeStart("1");
        p3.setOptimizeStep("1");
        p3.setOptimizeEnd("10");
        params.add(p3);

        // 5 * 2 = 10 total passes
        long total = manager.calculateTotalPasses(params);
        assertEquals(10, total);
    }

    @Test
    public void testWriteAndReadSetFile() throws IOException {
        EaParameterManager manager = new EaParameterManager();
        Path setFile = tempFolder.newFile("test.set").toPath();

        List<EaParameter> params = new ArrayList<>();
        EaParameter p1 = new EaParameter();
        p1.setName("TakeProfit");
        p1.setValue("50");
        p1.setSection("Trading Rules");
        p1.setOptimizeEnabled(true);
        p1.setOptimizeStart("10");
        p1.setOptimizeStep("10");
        p1.setOptimizeEnd("100");
        p1.setStringType(false);
        params.add(p1);

        EaParameter p2 = new EaParameter();
        p2.setName("StrategyName");
        p2.setValue("SuperTrend");
        p2.setStringType(true);
        params.add(p2);

        // Write file
        manager.writeSetFile(setFile, params, "TestEA");

        // Read file
        List<EaParameter> readParams = manager.readSetFile(setFile);

        assertEquals(2, readParams.size());

        EaParameter r1 = readParams.get(0);
        assertEquals("TakeProfit", r1.getName());
        assertEquals("50", r1.getValue());
        assertEquals("Trading Rules", r1.getSection());
        assertTrue(r1.isOptimizeEnabled());
        assertEquals("10", r1.getOptimizeStart());
        assertEquals("10", r1.getOptimizeStep());
        assertEquals("100", r1.getOptimizeEnd());
        assertFalse(r1.isStringType());

        EaParameter r2 = readParams.get(1);
        assertEquals("StrategyName", r2.getName());
        assertEquals("SuperTrend", r2.getValue());
        assertTrue(r2.isStringType());
    }

    @Test
    public void testCustomParametersLifecycle() {
        EaParameterManager manager = new EaParameterManager();
        String expertPath = "Experts\\MyAdvisors\\TestBot.ex5";

        assertFalse(manager.hasCustomConfig(expertPath));
        assertNull(manager.loadCustomParameters(expertPath));

        List<EaParameter> params = new ArrayList<>();
        EaParameter p1 = new EaParameter("Param1", "100");
        p1.setDefaultValue("50"); // default is different
        params.add(p1);

        // Save Custom Config
        manager.saveCustomParameters(expertPath, params);
        assertTrue(manager.hasCustomConfig(expertPath));

        // Load Custom Config
        List<EaParameter> custom = manager.loadCustomParameters(expertPath);
        assertNotNull(custom);
        assertEquals(1, custom.size());
        assertEquals("Param1", custom.get(0).getName());
        assertEquals("100", custom.get(0).getValue());

        // Check count modified (should be 0 because without default.set file, read parameters default value matches their value)
        assertEquals(0, manager.countModifiedParameters(expertPath));

        // Delete Custom Config
        manager.deleteCustomParameters(expertPath);
        assertFalse(manager.hasCustomConfig(expertPath));
        assertNull(manager.loadCustomParameters(expertPath));
    }

    @Test
    public void testDefaultAndEffectiveParameters() throws Exception {
        AppConfig config = AppConfig.getInstance();

        // Let's set a mock MT5 terminal path inside our temp folder
        Path tempMt5Dir = tempFolder.newFolder("MockMT5").toPath();
        Path mockTerminal = tempMt5Dir.resolve("terminal64.exe");
        Files.createFile(mockTerminal);
        config.setMt5TerminalPath(mockTerminal.toAbsolutePath().toString());

        EaParameterManager manager = new EaParameterManager();
        String expertPath = "MyExpert";

        assertFalse(manager.hasDefaultConfig(expertPath));
        assertNull(manager.loadDefaultParameters(expertPath));

        // Write a mock default .set file under MT5/MQL5/Profiles/Tester/
        Path defaultTesterDir = tempMt5Dir.resolve("MQL5").resolve("Profiles").resolve("Tester");
        Files.createDirectories(defaultTesterDir);
        Path defaultSetFile = defaultTesterDir.resolve("MyExpert.set");

        List<EaParameter> defaultParams = new ArrayList<>();
        EaParameter pDefault = new EaParameter("TakeProfit", "50");
        pDefault.setStringType(true);
        defaultParams.add(pDefault);
        manager.writeSetFile(defaultSetFile, defaultParams, "MyExpert");

        // Verify default config exists now
        assertTrue(manager.hasDefaultConfig(expertPath));
        List<EaParameter> loadedDefaults = manager.loadDefaultParameters(expertPath);
        assertNotNull(loadedDefaults);
        assertEquals(1, loadedDefaults.size());
        assertEquals("TakeProfit", loadedDefaults.get(0).getName());
        assertEquals("50", loadedDefaults.get(0).getValue());

        // Test getEffectiveParameters when only default exists
        List<EaParameter> effective = manager.getEffectiveParameters(expertPath);
        assertNotNull(effective);
        assertEquals(1, effective.size());
        assertEquals("TakeProfit", effective.get(0).getName());
        assertEquals("50", effective.get(0).getValue());
        assertFalse(effective.get(0).isModified());

        // Now save a custom parameters file with a different value
        List<EaParameter> customParams = new ArrayList<>();
        EaParameter pCustom = new EaParameter("TakeProfit", "70");
        pCustom.setStringType(true);
        customParams.add(pCustom);
        manager.saveCustomParameters(expertPath, customParams);

        // Test effective parameters when BOTH default and custom exist (should merge)
        effective = manager.getEffectiveParameters(expertPath);
        assertNotNull(effective);
        assertEquals(1, effective.size());
        assertEquals("TakeProfit", effective.get(0).getName());
        assertEquals("70", effective.get(0).getValue());
        assertEquals("50", effective.get(0).getDefaultValue()); // Merged from default
        assertTrue(effective.get(0).isModified()); // value (70) != defaultValue (50)
        assertEquals(1, manager.countModifiedParameters(expertPath));

        // Test prepareForBacktest
        String preparedName = manager.prepareForBacktest(expertPath);
        assertEquals("Backtester_MyExpert.set", preparedName);

        Path expectedPreparedFile = defaultTesterDir.resolve("Backtester_MyExpert.set");
        assertTrue(Files.exists(expectedPreparedFile));

        // Read the prepared file and verify it has custom value (70)
        List<EaParameter> preparedParams = manager.readSetFile(expectedPreparedFile);
        assertEquals(1, preparedParams.size());
        assertEquals("TakeProfit", preparedParams.get(0).getName());
        assertEquals("70", preparedParams.get(0).getValue());
    }
}
