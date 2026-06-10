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

import static org.junit.Assert.*;

public class AppConfigTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private String originalUserDir;
    private AppConfig originalInstance;

    @Before
    public void setUp() throws Exception {
        originalUserDir = System.getProperty("user.dir");
        // Save the original singleton instance to restore after test
        Field instanceField = AppConfig.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        originalInstance = (AppConfig) instanceField.get(null);
        // Reset singleton so constructor can run isolated if needed
        instanceField.set(null, null);
    }

    @After
    public void tearDown() throws Exception {
        System.setProperty("user.dir", originalUserDir);
        // Restore original singleton instance
        Field instanceField = AppConfig.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, originalInstance);
    }

    private AppConfig createTestConfig() throws Exception {
        System.setProperty("user.dir", tempFolder.getRoot().getAbsolutePath());
        Constructor<AppConfig> constructor = AppConfig.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    @Test
    public void testDefaultsAndDirectoriesCreated() throws Exception {
        AppConfig config = createTestConfig();

        // Verify base directories were created in the temp directory
        Path tempPath = tempFolder.getRoot().toPath();
        assertTrue(Files.exists(tempPath.resolve("config")));
        assertTrue(Files.exists(tempPath.resolve("data")));
        assertTrue(Files.exists(tempPath.resolve("backtest_reports")));

        // Verify default getters (loaded from classpath default.properties)
        assertEquals("USD", config.getDefaultCurrency());
        assertEquals("1:100", config.getDefaultLeverage());
        assertEquals(10000, config.getDefaultDeposit());
        assertEquals(0, config.getDefaultModel());
        assertEquals(2, config.getBrokerTimezoneOffset());
        assertTrue(config.isPortableMode());
    }

    @Test
    public void testGettersAndSetters() throws Exception {
        AppConfig config = createTestConfig();

        // Test custom key/value
        config.set("custom.key", "customValue");
        assertEquals("customValue", config.get("custom.key"));
        assertEquals("customValue", config.get("custom.key", "fallback"));
        assertEquals("fallback", config.get("nonexistent.key", "fallback"));

        // Test integer getters
        config.set("int.key", "42");
        assertEquals(42, config.getInt("int.key", 100));
        assertEquals(100, config.getInt("nonexistent.int.key", 100));
        config.set("invalid.int.key", "not_a_number");
        assertEquals(100, config.getInt("invalid.int.key", 100));

        // Test boolean getters
        config.set("bool.key", "true");
        assertTrue(config.getBoolean("bool.key", false));
        assertFalse(config.getBoolean("nonexistent.bool.key", false));
        assertTrue(config.getBoolean("nonexistent.bool.key", true));

        // Test specific convenience getters/setters
        config.setMt5TerminalPath("C:\\MyCustomMT5\\terminal64.exe");
        assertEquals("C:\\MyCustomMT5\\terminal64.exe", config.getMt5TerminalPath());
        assertEquals(Path.of("C:\\MyCustomMT5"), config.getMt5InstallDir());

        config.setMt4TerminalPath("D:\\MyCustomMT4\\terminal.exe");
        assertEquals("D:\\MyCustomMT4\\terminal.exe", config.getMt4TerminalPath());

        config.setDataDirectory("custom_data");
        assertEquals(tempFolder.getRoot().toPath().resolve("custom_data"), config.getDataDirectory());

        config.setReportsDirectory("custom_reports");
        assertEquals(tempFolder.getRoot().toPath().resolve("custom_reports"), config.getReportsDirectory());
    }

    @Test
    public void testSaveAndLoad() throws Exception {
        // Create config and set properties
        AppConfig config1 = createTestConfig();
        config1.set("test.persisted.key", "helloWorld");
        config1.setMt5TerminalPath("D:\\MT5\\terminal64.exe");
        config1.save();

        // Verify file was written
        Path configFile = tempFolder.getRoot().toPath().resolve("config").resolve("backtester.properties");
        assertTrue(Files.exists(configFile));

        // Create a new config instance, it should load from the persisted file
        AppConfig config2 = createTestConfig();
        assertEquals("helloWorld", config2.get("test.persisted.key"));
        assertEquals("D:\\MT5\\terminal64.exe", config2.getMt5TerminalPath());
    }

    @Test
    public void testIsMt4() throws Exception {
        AppConfig config = createTestConfig();
        
        config.setMt5TerminalPath("C:\\Program Files\\MetaTrader 5\\terminal64.exe");
        assertFalse(config.isMt4());
        
        config.setMt5TerminalPath("C:\\Program Files\\MetaTrader 4\\terminal.exe");
        assertTrue(config.isMt4());
        
        config.setMt5TerminalPath("D:\\MT4_instance\\terminal.exe");
        assertTrue(config.isMt4());

        // Test dynamic platform selection based on EA extension
        assertTrue(config.isMt4("expert.ex4"));
        assertFalse(config.isMt4("expert.ex5"));
        assertFalse(config.isMt4(""));
        assertFalse(config.isMt4(null));

        assertEquals(MetaTraderPlatform.MT4, config.getPlatform("expert.ex4"));
        assertEquals(MetaTraderPlatform.MT5, config.getPlatform("expert.ex5"));
        assertEquals(MetaTraderPlatform.MT5, config.getPlatform(null));

        // Test platform property configurations
        assertEquals("terminal.exe", MetaTraderPlatform.MT4.getExecutableName());
        assertEquals("terminal64.exe", MetaTraderPlatform.MT5.getExecutableName());
        assertEquals("tester", MetaTraderPlatform.MT4.getPresetsFolderName());
        assertEquals("MQL5/Profiles/Tester", MetaTraderPlatform.MT5.getPresetsFolderName());
    }
}
