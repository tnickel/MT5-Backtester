package com.backtester.engine;

import com.backtester.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

/**
 * Generates MT5 tester.ini configuration files for automated backtesting.
 * 
 * The INI file structure follows the MT5 Tester specification:
 * [Tester]
 * Expert=...
 * Symbol=...
 * etc.
 */
public class IniGenerator {

    private static final Logger log = LoggerFactory.getLogger(IniGenerator.class);
    private static final DateTimeFormatter MT5_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /**
     * Generates a tester.ini file at the given path for the specified backtest configuration.
     *
     * @param config   the backtest parameters
     * @param iniPath  where to write the .ini file
     * @param reportPath absolute or relative path for the report output
     * @return the path to the generated INI file
     */
    public Path generate(BacktestConfig config, Path iniPath, String reportPath) throws IOException {
        validate(config, iniPath, reportPath);
        Path parent = iniPath.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        try (Writer writer = Files.newBufferedWriter(iniPath, StandardCharsets.UTF_8)) {
            writer.write("[Tester]\r\n");

            if (AppConfig.getInstance().isMt4(config.getExpert())) {
                String expertName = config.getExpert();
                String expertsMarker = "mql4\\experts\\";
                int markerIdx = expertName.toLowerCase().indexOf(expertsMarker);
                if (markerIdx != -1) {
                    expertName = expertName.substring(markerIdx + expertsMarker.length());
                }
                expertName = expertName.replace('/', '\\').trim();
                if (expertName.toLowerCase().endsWith(".ex4") || expertName.toLowerCase().endsWith(".mq4")) {
                    expertName = expertName.substring(0, expertName.length() - 4).trim();
                }
                writer.write("TestExpert=" + expertName + "\r\n");
                if (config.getExpertParameters() != null && !config.getExpertParameters().isEmpty()) {
                    writer.write("TestExpertParameters=" + config.getExpertParameters() + "\r\n");
                }
                writer.write("TestSymbol=" + config.getSymbol() + "\r\n");
                writer.write("TestPeriod=" + config.getPeriod() + "\r\n");

                // Map MT5 tick models to MT4 models (0=Every tick, 1=Control points, 2=Open price only)
                int mt4Model = 0;
                if (config.getModel() == 2) {
                    mt4Model = 2; // Open price only
                } else if (config.getModel() == 1) {
                    mt4Model = 1; // Control points (corresponds to 1 min OHLC)
                } else {
                    mt4Model = 0; // Every tick / real ticks
                }
                writer.write("TestModel=" + mt4Model + "\r\n");
                writer.write("TestDateEnable=true\r\n");
                writer.write("TestFromDate=" + config.getFromDate().format(MT5_DATE_FORMAT) + "\r\n");
                writer.write("TestToDate=" + config.getToDate().format(MT5_DATE_FORMAT) + "\r\n");
                writer.write("TestOptimization=" + (config.getOptimization() > 0 ? "true" : "false") + "\r\n");
                writer.write("TestReport=" + reportPath + "\r\n");
                writer.write("TestReplaceReport=" + (config.isReplaceReport() ? "true" : "false") + "\r\n");
                writer.write("TestShutdownTerminal=" + (config.isShutdownTerminal() ? "true" : "false") + "\r\n");
                writer.write("TestVisualEnable=" + (config.isVisualMode() ? "true" : "false") + "\r\n");
            } else {
                writer.write("Expert=" + config.getExpert() + "\r\n");
                if (config.getExpertParameters() != null && !config.getExpertParameters().isEmpty()) {
                    writer.write("ExpertParameters=" + config.getExpertParameters() + "\r\n");
                }
                writer.write("Symbol=" + config.getSymbol() + "\r\n");
                writer.write("Period=" + config.getPeriod() + "\r\n");
                writer.write("Model=" + config.getModel() + "\r\n");
                writer.write("ExecutionMode=" + config.getExecutionMode() + "\r\n");
                writer.write("UseDate=1\r\n");
                writer.write("FromDate=" + config.getFromDate().format(MT5_DATE_FORMAT) + "\r\n");
                writer.write("ToDate=" + config.getToDate().format(MT5_DATE_FORMAT) + "\r\n");
                writer.write("Deposit=" + config.getDeposit() + "\r\n");
                writer.write("Currency=" + config.getCurrency() + "\r\n");
                writer.write("Leverage=" + config.getLeverage() + "\r\n");
                writer.write("Optimization=" + config.getOptimization() + "\r\n");
                writer.write("Report=" + reportPath + "\r\n");
                writer.write("ReplaceReport=" + (config.isReplaceReport() ? "1" : "0") + "\r\n");
                writer.write("ShutdownTerminal=" + (config.isShutdownTerminal() ? "1" : "0") + "\r\n");
            }
            
            // Append Experts section to enable DLL imports programmatically
            writer.write("\r\n[Experts]\r\n");
            writer.write("AllowDllImport=1\r\n");
            writer.write("Enabled=1\r\n");
            writer.write("ExpertsEnable=true\r\n");
            writer.write("ExpertsDllImport=true\r\n");
            writer.write("ExpertsTrades=true\r\n");
        }

        log.info("Generated tester.ini at: {}", iniPath);
        return iniPath;
    }

    /**
     * Generates a tester.ini file for an optimization run.
     *
     * @param config   the optimization parameters
     * @param iniPath  where to write the .ini file
     * @param reportPath absolute or relative path for the report output
     * @return the path to the generated INI file
     */
    public Path generateForOptimization(OptimizationConfig config, Path iniPath, String reportPath) throws IOException {
        validate(config, iniPath, reportPath);
        Path parent = iniPath.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        try (Writer writer = Files.newBufferedWriter(iniPath, StandardCharsets.UTF_8)) {
            writer.write("[Tester]\r\n");

            if (AppConfig.getInstance().isMt4(config.getExpert())) {
                String expertName = config.getExpert();
                String expertsMarker = "mql4\\experts\\";
                int markerIdx = expertName.toLowerCase().indexOf(expertsMarker);
                if (markerIdx != -1) {
                    expertName = expertName.substring(markerIdx + expertsMarker.length());
                }
                expertName = expertName.replace('/', '\\').trim();
                if (expertName.toLowerCase().endsWith(".ex4") || expertName.toLowerCase().endsWith(".mq4")) {
                    expertName = expertName.substring(0, expertName.length() - 4).trim();
                }
                writer.write("TestExpert=" + expertName + "\r\n");
                if (config.getExpertParameters() != null && !config.getExpertParameters().isEmpty()) {
                    writer.write("TestExpertParameters=" + config.getExpertParameters() + "\r\n");
                }
                writer.write("TestSymbol=" + config.getSymbol() + "\r\n");
                writer.write("TestPeriod=" + config.getPeriod() + "\r\n");

                int mt4Model = 0;
                if (config.getModel() == 2) {
                    mt4Model = 2; // Open price only
                } else if (config.getModel() == 1) {
                    mt4Model = 1; // Control points
                } else {
                    mt4Model = 0; // Every tick / real ticks
                }
                writer.write("TestModel=" + mt4Model + "\r\n");
                writer.write("TestDateEnable=true\r\n");
                writer.write("TestFromDate=" + config.getFromDate().format(MT5_DATE_FORMAT) + "\r\n");
                writer.write("TestToDate=" + config.getToDate().format(MT5_DATE_FORMAT) + "\r\n");
                writer.write("TestOptimization=true\r\n"); // optimization is enabled
                writer.write("TestReport=" + reportPath + "\r\n");
                writer.write("TestReplaceReport=true\r\n");
                writer.write("TestShutdownTerminal=" + (config.isShutdownTerminal() ? "true" : "false") + "\r\n");
                writer.write("TestVisualEnable=false\r\n");
            } else {
                writer.write("Expert=" + config.getExpert() + "\r\n");
                if (config.getExpertParameters() != null && !config.getExpertParameters().isEmpty()) {
                    writer.write("ExpertParameters=" + config.getExpertParameters() + "\r\n");
                }
                writer.write("Symbol=" + config.getSymbol() + "\r\n");
                writer.write("Period=" + config.getPeriod() + "\r\n");
                writer.write("Model=" + config.getModel() + "\r\n");
                writer.write("ExecutionMode=" + config.getExecutionMode() + "\r\n");
                writer.write("UseDate=1\r\n");
                writer.write("FromDate=" + config.getFromDate().format(MT5_DATE_FORMAT) + "\r\n");
                writer.write("ToDate=" + config.getToDate().format(MT5_DATE_FORMAT) + "\r\n");
                writer.write("Deposit=" + config.getDeposit() + "\r\n");
                writer.write("Currency=" + config.getCurrency() + "\r\n");
                writer.write("Leverage=" + config.getLeverage() + "\r\n");
                
                // Optimization specific settings
                writer.write("Optimization=" + config.getOptimizationMode() + "\r\n");
                writer.write("OptimizationCriterion=" + config.getOptimizationCriterion() + "\r\n");
                writer.write("ForwardMode=" + config.getForwardMode() + "\r\n");
                if (config.getForwardMode() == 4 && config.getForwardDate() != null) {
                    writer.write("ForwardDate=" + config.getForwardDate().format(MT5_DATE_FORMAT) + "\r\n");
                }
                
                writer.write("UseLocal=" + (config.isUseLocal() ? "1" : "0") + "\r\n");
                writer.write("UseRemote=" + (config.isUseRemote() ? "1" : "0") + "\r\n");
                writer.write("UseCloud=" + (config.isUseCloud() ? "1" : "0") + "\r\n");
                
                writer.write("Report=" + reportPath + "\r\n");
                writer.write("ReplaceReport=1\r\n");
                writer.write("ShutdownTerminal=" + (config.isShutdownTerminal() ? "1" : "0") + "\r\n");
            }
            
            // Append Experts section to enable DLL imports programmatically
            writer.write("\r\n[Experts]\r\n");
            writer.write("AllowDllImport=1\r\n");
            writer.write("Enabled=1\r\n");
            writer.write("ExpertsEnable=true\r\n");
            writer.write("ExpertsDllImport=true\r\n");
            writer.write("ExpertsTrades=true\r\n");
        }

        log.info("Generated optimization tester.ini at: {}", iniPath);
        return iniPath;
    }

    private static void validate(BacktestConfig config, Path iniPath, String reportPath) {
        if (config == null) throw new IllegalArgumentException("BacktestConfig must not be null");
        validateCommon(config.getExpert(), config.getSymbol(), config.getPeriod(),
                config.getFromDate(), config.getToDate(), iniPath, reportPath);
    }

    private static void validate(OptimizationConfig config, Path iniPath, String reportPath) {
        if (config == null) throw new IllegalArgumentException("OptimizationConfig must not be null");
        validateCommon(config.getExpert(), config.getSymbol(), config.getPeriod(),
                config.getFromDate(), config.getToDate(), iniPath, reportPath);
    }

    private static void validateCommon(String expert, String symbol, String period,
                                       java.time.LocalDate fromDate, java.time.LocalDate toDate,
                                       Path iniPath, String reportPath) {
        if (iniPath == null) throw new IllegalArgumentException("INI path must not be null");
        if (reportPath == null || reportPath.isBlank()) {
            throw new IllegalArgumentException("Report path must not be blank");
        }
        if (expert == null || expert.isBlank()) throw new IllegalArgumentException("Expert must not be blank");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("Symbol must not be blank");
        if (period == null || period.isBlank()) throw new IllegalArgumentException("Period must not be blank");
        if (fromDate == null || toDate == null || !fromDate.isBefore(toDate)) {
            throw new IllegalArgumentException("Invalid test date range: " + fromDate + " to " + toDate);
        }
    }
}
