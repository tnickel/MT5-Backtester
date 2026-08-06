package com.backtester.report;

import com.backtester.engine.BacktestConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/** Structured, versioned snapshot of all scalar statistics parsed from an MT report. */
public final class BacktestStatisticsArtifact {
    public static final String FILE_NAME = "statistics.json";
    public static final int SCHEMA_VERSION = 1;
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    private BacktestStatisticsArtifact() {
    }

    public static JsonObject create(BacktestResult result, BacktestConfig config) {
        JsonObject json = new JsonObject();
        json.addProperty("schemaVersion", SCHEMA_VERSION);

        json.addProperty("success", result.isSuccess());
        json.addProperty("message", safe(result.getMessage()));
        json.addProperty("expert", safe(result.getExpert()));
        json.addProperty("symbol", safe(result.getSymbol()));
        json.addProperty("period", safe(result.getPeriod()));
        json.addProperty("outputDirectory", safe(result.getOutputDirectory()));
        json.addProperty("tickModel", safe(result.getTickModel()));
        json.addProperty("usedDefaultConfig", result.isUsedDefaultConfig());
        json.addProperty("configInfo", safe(result.getConfigInfo()));

        if (config != null) {
            json.addProperty("fromDate", config.getFromDate() != null ? config.getFromDate().toString() : "");
            json.addProperty("toDate", config.getToDate() != null ? config.getToDate().toString() : "");
            json.addProperty("model", config.getModel());
            json.addProperty("modelName", config.getModelName());
            json.addProperty("executionMode", config.getExecutionMode());
            json.addProperty("deposit", config.getDeposit());
            json.addProperty("currency", safe(config.getCurrency()));
            json.addProperty("leverage", safe(config.getLeverage()));
            json.addProperty("expertParameters", safe(config.getExpertParameters()));
        }

        // Keep the original short aliases so existing History consumers remain compatible.
        addNumber(json, "profit", result.getTotalProfit());
        addNumber(json, "drawdown", result.getMaxDrawdownPercent());
        json.addProperty("trades", result.getTotalTrades());

        addNumber(json, "initialDeposit", result.getInitialDeposit());
        addNumber(json, "finalBalance", result.getFinalBalance());
        addNumber(json, "totalProfit", result.getTotalProfit());
        addNumber(json, "grossProfit", result.getGrossProfit());
        addNumber(json, "grossLoss", result.getGrossLoss());
        json.addProperty("totalTrades", result.getTotalTrades());
        json.addProperty("profitTrades", result.getProfitTrades());
        json.addProperty("lossTrades", result.getLossTrades());
        json.addProperty("shortPositions", result.getShortPositions());
        json.addProperty("longPositions", result.getLongPositions());
        addNumber(json, "winRate", result.getWinRate());

        addNumber(json, "relativeEquityDrawdownPercent", result.getMaxDrawdownPercent());
        addNumber(json, "maximalEquityDrawdownPercent", result.getMaxDrawdown());
        addNumber(json, "maximalEquityDrawdownAbsolute", result.getMaxDrawdownAbsolute());
        addNumber(json, "maximalBalanceDrawdownPercent", result.getBalanceDrawdown());
        addNumber(json, "maximalBalanceDrawdownAbsolute", result.getBalanceDrawdownAbsolute());
        // Exact BacktestResult field names keep DB deserialization compatible.
        addNumber(json, "maxDrawdown", result.getMaxDrawdown());
        addNumber(json, "maxDrawdownAbsolute", result.getMaxDrawdownAbsolute());
        addNumber(json, "maxDrawdownPercent", result.getMaxDrawdownPercent());
        addNumber(json, "balanceDrawdown", result.getBalanceDrawdown());
        addNumber(json, "balanceDrawdownAbsolute", result.getBalanceDrawdownAbsolute());

        addNumber(json, "profitFactor", result.getProfitFactor());
        addNumber(json, "sharpeRatio", result.getSharpeRatio());
        addNumber(json, "recoveryFactor", result.getRecoveryFactor());
        addNumber(json, "expectedPayoff", result.getExpectedPayoff());
        addNumber(json, "largestWin", result.getLargestWin());
        addNumber(json, "largestLoss", result.getLargestLoss());
        addNumber(json, "averageWin", result.getAverageWin());
        addNumber(json, "averageLoss", result.getAverageLoss());
        json.addProperty("equityHistoryPoints",
                result.getEquityHistory() != null ? result.getEquityHistory().size() : 0);
        JsonObject rawStatistics = new JsonObject();
        if (result.getRawStatistics() != null) {
            for (Map.Entry<String, String> entry : result.getRawStatistics().entrySet()) {
                if (entry.getKey() != null && !entry.getKey().isBlank()) {
                    rawStatistics.addProperty(entry.getKey(), safe(entry.getValue()));
                }
            }
        }
        json.add("rawStatistics", rawStatistics);
        return json;
    }

    public static void write(Path outputDirectory, BacktestResult result, BacktestConfig config)
            throws IOException {
        Path target = outputDirectory.resolve(FILE_NAME);
        Path temporary = outputDirectory.resolve(FILE_NAME + ".tmp-" + UUID.randomUUID());
        try {
            Files.writeString(temporary, PRETTY_GSON.toJson(create(result, config)),
                    StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void addNumber(JsonObject json, String name, double value) {
        if (Double.isFinite(value)) json.addProperty(name, value);
        else json.add(name, JsonNull.INSTANCE);
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
