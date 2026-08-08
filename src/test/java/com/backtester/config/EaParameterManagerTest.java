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

        assertEquals(3, readParams.size());
        assertTrue(readParams.get(0).isSectionHeader());
        assertEquals("Trading Rules", readParams.get(0).getSection());

        EaParameter r1 = readParams.stream().filter(p -> "TakeProfit".equals(p.getName())).findFirst().orElse(null);
        assertNotNull(r1);
        assertEquals("50", r1.getValue());
        assertEquals("Trading Rules", r1.getSection());
        assertTrue(r1.isOptimizeEnabled());
        assertEquals("10", r1.getOptimizeStart());
        assertEquals("10", r1.getOptimizeStep());
        assertEquals("100", r1.getOptimizeEnd());
        assertFalse(r1.isStringType());

        EaParameter r2 = readParams.stream().filter(p -> "StrategyName".equals(p.getName())).findFirst().orElse(null);
        assertNotNull(r2);
        assertEquals("SuperTrend", r2.getValue());
        assertTrue(r2.isStringType());
    }

    @Test
    public void testWriteAndReadSetFileMt4() throws IOException {
        EaParameterManager manager = new EaParameterManager();
        Path setFile = tempFolder.newFile("test_mt4.set").toPath();

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

        // Write file with MT4 expert path (ex4 suffix)
        manager.writeSetFile(setFile, params, "Experts\\TestEA.ex4");

        // Verify that it contains numerical suffixes in the file (1, 2, 3)
        List<String> fileLines = Files.readAllLines(setFile, java.nio.charset.StandardCharsets.UTF_16LE);
        boolean found1 = false, found2 = false, found3 = false;
        for (String line : fileLines) {
            if (line.contains("TakeProfit,1=10")) found1 = true;
            if (line.contains("TakeProfit,2=10")) found2 = true;
            if (line.contains("TakeProfit,3=100")) found3 = true;
        }
        assertTrue("TakeProfit,1 not found in MT4 config file", found1);
        assertTrue("TakeProfit,2 not found in MT4 config file", found2);
        assertTrue("TakeProfit,3 not found in MT4 config file", found3);

        // Read file back and check parsed optimization ranges
        List<EaParameter> readParams = manager.readSetFile(setFile);
        assertEquals(3, readParams.size());

        EaParameter r1 = readParams.stream().filter(p -> "TakeProfit".equals(p.getName())).findFirst().orElse(null);
        assertNotNull(r1);
        assertEquals("50", r1.getValue());
        assertTrue(r1.isOptimizeEnabled());
        assertEquals("10", r1.getOptimizeStart());
        assertEquals("10", r1.getOptimizeStep());
        assertEquals("100", r1.getOptimizeEnd());
        assertFalse(r1.isStringType());

        EaParameter r2 = readParams.stream().filter(p -> "StrategyName".equals(p.getName())).findFirst().orElse(null);
        assertNotNull(r2);
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

    @Test
    public void testEffectiveParametersPreservesDefaultsWhenCustomMissing() throws Exception {
        AppConfig config = AppConfig.getInstance();
        Path tempMt5Dir = tempFolder.newFolder("MockMT5_Missing").toPath();
        Path mockTerminal = tempMt5Dir.resolve("terminal64.exe");
        Files.createFile(mockTerminal);
        config.setMt5TerminalPath(mockTerminal.toAbsolutePath().toString());

        EaParameterManager manager = new EaParameterManager();
        String expertPath = "MyExpertMissingTest";

        // Write a mock default .set file with two parameters
        Path defaultTesterDir = tempMt5Dir.resolve("MQL5").resolve("Profiles").resolve("Tester");
        Files.createDirectories(defaultTesterDir);
        Path defaultSetFile = defaultTesterDir.resolve("MyExpertMissingTest.set");

        List<EaParameter> defaultParams = new ArrayList<>();
        EaParameter pDefault1 = new EaParameter("TakeProfit", "50");
        pDefault1.setStringType(true);
        defaultParams.add(pDefault1);

        EaParameter pDefault2 = new EaParameter("StopLoss", "100");
        pDefault2.setStringType(true);
        defaultParams.add(pDefault2);

        manager.writeSetFile(defaultSetFile, defaultParams, expertPath);

        // Now save a custom parameters file with ONLY TakeProfit (missing StopLoss)
        List<EaParameter> customParams = new ArrayList<>();
        EaParameter pCustom = new EaParameter("TakeProfit", "70");
        pCustom.setStringType(true);
        customParams.add(pCustom);
        manager.saveCustomParameters(expertPath, customParams);

        // Get effective parameters
        List<EaParameter> effective = manager.getEffectiveParameters(expertPath);
        assertNotNull(effective);
        assertEquals(2, effective.size());

        // Check TakeProfit
        EaParameter ep1 = effective.get(0);
        assertEquals("TakeProfit", ep1.getName());
        assertEquals("70", ep1.getValue());
        assertEquals("50", ep1.getDefaultValue());
        assertTrue(ep1.isModified());

        // Check StopLoss (preserved default)
        EaParameter ep2 = effective.get(1);
        assertEquals("StopLoss", ep2.getName());
        assertEquals("100", ep2.getValue());
        assertEquals("100", ep2.getDefaultValue());
        assertFalse(ep2.isModified());
    }

    @Test
    public void testReadIniFileWithBlocks() throws IOException {
        EaParameterManager manager = new EaParameterManager();
        Path iniFile = tempFolder.newFile("test_blocks.ini").toPath();

        List<String> lines = new ArrayList<>();
        lines.add("<common>");
        lines.add("deposit=10000");
        lines.add("currency=USD");
        lines.add("</common>");
        lines.add("<inputs>");
        lines.add("Lots=0.1");
        lines.add("Lots,F=1");
        lines.add("Lots,1=0.01");
        lines.add("Lots,2=0.01");
        lines.add("Lots,3=0.5");
        lines.add("Password=secret");
        lines.add("</inputs>");
        lines.add("<limits>");
        lines.add("balance=200");
        lines.add("</limits>");

        Files.write(iniFile, lines, java.nio.charset.StandardCharsets.UTF_8);

        List<EaParameter> parsed = manager.readSetFile(iniFile);
        // Should only contain Lots and Password from the <inputs> block
        assertEquals(2, parsed.size());

        EaParameter p1 = parsed.get(0);
        assertEquals("Lots", p1.getName());
        assertEquals("0.1", p1.getValue());
        assertTrue(p1.isOptimizeEnabled());
        assertEquals("0.01", p1.getOptimizeStart());
        assertEquals("0.01", p1.getOptimizeStep());
        assertEquals("0.5", p1.getOptimizeEnd());

        EaParameter p2 = parsed.get(1);
        assertEquals("Password", p2.getName());
        assertEquals("secret", p2.getValue());
    }

    @Test
    public void testExtractEaBaseNameEdgeCases() {
        assertEquals("", EaParameterManager.extractEaBaseName("Experts/"));
        assertEquals("", EaParameterManager.extractEaBaseName("Experts\\"));
        assertEquals("MyEA", EaParameterManager.extractEaBaseName("MyEA.ex5"));
        assertEquals("test", EaParameterManager.extractEaBaseName("test.EX5"));
    }

    @Test
    public void testCalculateTotalPassesEdgeCases() {
        EaParameterManager manager = new EaParameterManager();
        List<EaParameter> params = new ArrayList<>();
        assertEquals(1, manager.calculateTotalPasses(params));
        
        EaParameter p = new EaParameter("param", "val");
        p.setOptimizeEnabled(true);
        p.setStringType(true);
        params.add(p);
        assertEquals(1, manager.calculateTotalPasses(params));
    }

    @Test
    public void testWriteAndReadSetFileWithEmptyList() throws IOException {
        EaParameterManager manager = new EaParameterManager();
        Path file = tempFolder.newFile("empty.set").toPath();
        manager.writeSetFile(file, new ArrayList<>(), "EmptyRobot");
        List<EaParameter> read = manager.readSetFile(file);
        assertTrue(read.isEmpty());
    }

    @Test
    public void testWriteAndReadSetFileWithSpecialCharacters() throws IOException {
        EaParameterManager manager = new EaParameterManager();
        Path file = tempFolder.newFile("special.set").toPath();
        List<EaParameter> params = new ArrayList<>();
        params.add(new EaParameter("RobotName", "Super\"Robot\"=Best\tVal"));
        manager.writeSetFile(file, params, "SpecialBot");
        List<EaParameter> read = manager.readSetFile(file);
        assertEquals(1, read.size());
        assertEquals("Super\"Robot\"=Best\tVal", read.get(0).getValue());
    }

    @Test
    public void testReadSetFileWithoutBomUtf16Le() throws IOException {
        EaParameterManager manager = new EaParameterManager();
        Path file = tempFolder.newFile("nobom_utf16le.set").toPath();
        
        // Write UTF-16 LE content without BOM bytes (no FF FE prefix)
        byte[] rawBytes = "RobotName=SuperTrend\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_16LE);
        Files.write(file, rawBytes);
        
        List<EaParameter> read = manager.readSetFile(file);
        assertEquals(1, read.size());
        
        EaParameter r = read.get(0);
        assertEquals("RobotName", r.getName());
        assertEquals("SuperTrend", r.getValue());
        assertTrue(r.isStringType());
    }

    @Test
    public void testCountModifiedParametersWithNonexistentExpert() {
        EaParameterManager manager = new EaParameterManager();
        assertEquals(0, manager.countModifiedParameters("NonExistentBot"));
    }

    @Test
    public void testApplyTranslations() {
        EaParameterManager manager = new EaParameterManager();
        String expertPath = "Experts\\ToTheMoon.ex5";
        
        List<EaParameter> params = new ArrayList<>();
        params.add(new EaParameter("Inp_Maximo_Ativos_Robo", "5"));
        params.add(new EaParameter("Inp_Lucro_Alvo", "100"));
        
        manager.applyTranslations(expertPath, params);
        
        // Verify display names are exactly the raw names
        assertEquals("Inp_Maximo_Ativos_Robo", params.get(0).getDisplayName());
        assertEquals("Inp_Lucro_Alvo", params.get(1).getDisplayName());
    }
}
