package com.backtester.engine;

import com.backtester.config.AppConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.*;

public class IniGeneratorTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testGenerateBacktestIni() throws IOException {
        IniGenerator generator = new IniGenerator();
        BacktestConfig config = new BacktestConfig();
        config.setExpert("MyEA.ex5");
        config.setSymbol("GBPUSD");
        config.setPeriod("M15");
        config.setFromDate(LocalDate.of(2023, 1, 1));
        config.setToDate(LocalDate.of(2023, 1, 31));
        
        Path iniPath = tempFolder.newFile("tester.ini").toPath();
        
        generator.generate(config, iniPath, "report_path.xml");
        
        assertTrue(Files.exists(iniPath));
        List<String> lines = Files.readAllLines(iniPath);
        
        assertTrue(lines.contains("[Tester]"));
        assertTrue(lines.contains("Expert=MyEA.ex5"));
        assertTrue(lines.contains("Symbol=GBPUSD"));
        assertTrue(lines.contains("Period=M15"));
        assertTrue(lines.contains("UseDate=1"));
        assertTrue(lines.contains("FromDate=2023.01.01"));
        assertTrue(lines.contains("ToDate=2023.01.31"));
        assertTrue(lines.contains("Report=report_path.xml"));
    }

    @Test
    public void testGenerateOptimizationIni() throws IOException {
        IniGenerator generator = new IniGenerator();
        OptimizationConfig config = new OptimizationConfig();
        config.setExpert("OptimEA.ex5");
        config.setOptimizationMode(2); // Genetic
        config.setOptimizationCriterion(1); // Profit Factor
        config.setForwardMode(2); // 1/3
        
        Path iniPath = tempFolder.newFile("opt_tester.ini").toPath();
        
        generator.generateForOptimization(config, iniPath, "opt_report");
        
        assertTrue(Files.exists(iniPath));
        List<String> lines = Files.readAllLines(iniPath);
        
        assertTrue(lines.contains("Expert=OptimEA.ex5"));
        assertTrue(lines.contains("Optimization=2"));
        assertTrue(lines.contains("OptimizationCriterion=1"));
        assertTrue(lines.contains("ForwardMode=2"));
        assertTrue(lines.contains("UseLocal=1"));
    }

    @Test
    public void testGenerateBacktestIniMt4() throws IOException {
        String originalPath = AppConfig.getInstance().getMt5TerminalPath();
        try {
            AppConfig.getInstance().setMt5TerminalPath("C:\\MT4\\terminal.exe");
            
            IniGenerator generator = new IniGenerator();
            BacktestConfig config = new BacktestConfig();
            config.setExpert("ScraperTemp\\MyEA.ex4");
            config.setSymbol("GBPUSD");
            config.setPeriod("M15");
            config.setFromDate(LocalDate.of(2023, 1, 1));
            config.setToDate(LocalDate.of(2023, 1, 31));
            
            Path iniPath = tempFolder.newFile("tester_mt4.ini").toPath();
            generator.generate(config, iniPath, "report_path");
            
            assertTrue(Files.exists(iniPath));
            List<String> lines = Files.readAllLines(iniPath);
            
            assertTrue(lines.contains("[Tester]"));
            assertTrue(lines.contains("TestExpert=ScraperTemp\\MyEA"));
            assertTrue(lines.contains("TestSymbol=GBPUSD"));
            assertTrue(lines.contains("TestPeriod=M15"));
            assertTrue(lines.contains("TestFromDate=2023.01.01"));
            assertTrue(lines.contains("TestToDate=2023.01.31"));
            assertTrue(lines.contains("TestReport=report_path"));
            assertTrue(lines.contains("TestOptimization=false"));
        } finally {
            AppConfig.getInstance().setMt5TerminalPath(originalPath);
        }
    }

    @Test
    public void rejectsLineBreaksInIniValues() throws IOException {
        BacktestConfig config = new BacktestConfig();
        config.setExpert("MyEA.ex5");
        config.setSymbol("EURUSD\r\nReport=redirected.html");
        config.setPeriod("M5");
        config.setFromDate(LocalDate.of(2025, 1, 1));
        config.setToDate(LocalDate.of(2025, 1, 2));
        Path ini = tempFolder.getRoot().toPath().resolve("injected.ini");

        try {
            new IniGenerator().generate(config, ini, "safe-report.html");
            fail("Expected INI line injection to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("line breaks"));
        }
        assertFalse(Files.exists(ini));
    }
}
