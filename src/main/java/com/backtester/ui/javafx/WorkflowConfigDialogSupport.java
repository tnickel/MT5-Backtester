package com.backtester.ui.javafx;

import com.backtester.config.EaParameter;
import com.backtester.config.AppConfig;
import com.backtester.config.Preset;
import com.backtester.config.PresetManager;
import javafx.scene.web.WebView;
import com.backtester.config.EaParameterManager;
import com.backtester.engine.BacktestConfig;
import com.backtester.engine.OptimizationConfig;
import com.backtester.engine.WorkflowEngine;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.SensitivityResult;
import com.backtester.workflow.WorkflowTask;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared helpers for workflow config dialogs.
 */
final class WorkflowConfigDialogSupport {

    private WorkflowConfigDialogSupport() {}

    static void applyTheme(Stage stage, Window owner) {
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        if (owner != null && owner.getScene() != null && !owner.getScene().getStylesheets().isEmpty()) {
            stage.getScene().getStylesheets().addAll(owner.getScene().getStylesheets());
        }
    }

    static StringConverter<LocalDate> createDateConverter() {
        return new StringConverter<LocalDate>() {
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            @Override
            public String toString(LocalDate date) {
                return (date != null) ? dateFormatter.format(date) : "";
            }
            @Override
            public LocalDate fromString(String string) {
                return (string != null && !string.isEmpty()) ? LocalDate.parse(string, dateFormatter) : null;
            }
        };
    }

    static double parseFiniteDecimal(String text, String fieldName, double minimum, double maximum) {
        String normalized = text == null ? "" : text.trim().replace(',', '.');
        final double value;
        try {
            value = Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " muss eine gültige Zahl sein.");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " muss eine endliche Zahl sein.");
        }
        if (value < minimum) {
            throw new IllegalArgumentException(fieldName + " muss mindestens " + minimum + " sein.");
        }
        if (value > maximum) {
            throw new IllegalArgumentException(fieldName + " darf höchstens " + maximum + " sein.");
        }
        return value;
    }

    static int parsePositiveInteger(String text, String fieldName) {
        final int value;
        try {
            value = Integer.parseInt(text == null ? "" : text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " muss eine ganze Zahl sein.");
        }
        if (value < 1) {
            throw new IllegalArgumentException(fieldName + " muss mindestens 1 sein.");
        }
        return value;
    }

    // ─── Custom Project: Diversity Clustering ───────────────────────────────────

    static void applyDiversityTaskSettings(WorkflowTask task,
                                            String moduleName,
                                            String sourceDatabank,
                                            String targetDatabank,
                                            String parameterDifferencePercent,
                                            String tradeDifferencePercent,
                                            String minimumDifferentParameters,
                                            String maximumStrategies) {
        applyDiversityTaskSettings(task, moduleName, sourceDatabank, targetDatabank,
                parameterDifferencePercent, tradeDifferencePercent,
                minimumDifferentParameters, maximumStrategies,
                task != null && task.isDiversityRankByScore());
    }

    static void applyDiversityTaskSettings(WorkflowTask task,
                                            String moduleName,
                                            String sourceDatabank,
                                            String targetDatabank,
                                            String parameterDifferencePercent,
                                            String tradeDifferencePercent,
                                            String minimumDifferentParameters,
                                            String maximumStrategies,
                                            boolean rankByScore) {
        if (task == null) throw new IllegalArgumentException("Kein Clustering-Task ausgewählt.");

        String cleanName = moduleName != null ? moduleName.trim() : "";
        String cleanSource = sourceDatabank != null ? sourceDatabank.trim() : "";
        String cleanTarget = targetDatabank != null ? targetDatabank.trim() : "";
        if (cleanName.isEmpty()) throw new IllegalArgumentException("Der Modulname darf nicht leer sein.");
        if (cleanSource.isEmpty()) throw new IllegalArgumentException("Eine Quell-Databank muss ausgewählt werden.");
        if (cleanTarget.isEmpty()) throw new IllegalArgumentException("Eine Ziel-Databank muss ausgewählt werden.");

        double parameterDifference = parseFiniteDecimal(
                parameterDifferencePercent, "Parameter-Differenz", 0.0, 100.0) / 100.0;
        double tradeDifference = parseFiniteDecimal(
                tradeDifferencePercent, "Trade-Differenz", 0.0, 100.0) / 100.0;
        int differentParameters = parsePositiveInteger(
                minimumDifferentParameters, "Min. differente Parameter");
        int strategyLimit = parsePositiveInteger(maximumStrategies, "Max. Strategien");

        task.setName(cleanName);
        task.setSourceDatabank(cleanSource);
        task.setTargetDatabank(cleanTarget);
        task.setDiversityParamDiffPct(parameterDifference);
        task.setDiversityTradeDiffPct(tradeDifference);
        task.setDiversityMinDifferentParams(differentParameters);
        task.setDiversityMaxStrategies(strategyLimit);
        task.setDiversityRankByScore(rankByScore);
    }

}
