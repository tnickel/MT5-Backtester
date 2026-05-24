package com.backtester.engine;

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
}
