package com.backtester.engine;

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
        Files.createDirectories(iniPath.getParent());

        try (Writer writer = Files.newBufferedWriter(iniPath, StandardCharsets.UTF_8)) {
            writer.write("[Tester]\r\n");
            writer.write("Expert=" + config.getExpert() + "\r\n");

            if (config.getExpertParameters() != null && !config.getExpertParameters().isEmpty()) {
                writer.write("ExpertParameters=" + config.getExpertParameters() + "\r\n");
            }

            writer.write("Symbol=" + config.getSymbol() + "\r\n");
            writer.write("Period=" + config.getPeriod() + "\r\n");
            writer.write("Model=" + config.getModel() + "\r\n");
            writer.write("ExecutionMode=" + config.getExecutionMode() + "\r\n");
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

        log.info("Generated tester.ini at: {}", iniPath);
        return iniPath;
    }
}
