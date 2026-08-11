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
 * Step 7: out-of-sample validation configuration dialog.
 */
public final class WorkflowStep7ConfigDialog {

    private WorkflowStep7ConfigDialog() {}

    /**
     * Schritt 7: Konfiguration des Out-of-Sample-Validierungsfensters.
     *
     * <p>Das Fenster muss NACH dem Optimierungszeitraum liegen, damit die
     * finalen Strategien auf Daten getestet werden, die weder die Optimierung
     * (Schritt 2) noch die Selektion (Schritte 3–6, die das Forward-Fenster
     * als Auswahlkriterium verbrauchen) je gesehen haben.
     */
    public static void showStep7Dialog(WorkflowEngine engine, Window owner) {
        Stage stage = new Stage();
        stage.setTitle("Schritt 7: Out-of-Sample Validierung");

        VBox layout = new VBox(15);
        layout.setStyle("-fx-background-color: #0b0d13; -fx-padding: 20;");
        layout.setPrefWidth(620);

        Label title = new Label("VALIDIERUNG AUF UNBERÜHRTEN DATEN");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#00e5ff"));

        Label info = new Label(
            "Warum dieser Schritt? Das Forward-Fenster wird in den Schritten 3–6 bereits als " +
            "Auswahlkriterium benutzt. Wer aus tausenden Pässen die besten nach Forward-Performance " +
            "auswählt, verbraucht das Forward-Fenster (Selection Bias) — einige Pässe sehen dort " +
            "rein zufällig gut aus. Erst ein Backtest auf einem Zeitfenster, das KEIN Schritt je " +
            "gesehen hat, liefert eine ehrliche Out-of-Sample-Schätzung.\n\n" +
            "Standard: Das Fenster beginnt einen Tag nach dem Optimierungs-Enddatum und endet heute. " +
            "Es darf sich nicht mit dem Optimierungszeitraum überlappen. Bestanden ist die Validierung " +
            "nur bei positivem Profit, mindestens " +
            com.backtester.report.ValidationResult.MIN_VALIDATION_TRADES + " Trades und einem Recovery Factor von mindestens " +
            String.format(Locale.US, "%.1f", com.backtester.report.ValidationResult.MIN_RECOVERY_FACTOR) + "."
        );
        info.setWrapText(true);
        info.setStyle("-fx-text-fill: #b4bac8; -fx-font-size: 12px;");

        String optRange = (engine.getFromDate() != null ? engine.getFromDate().toString() : "?") +
                " bis " + (engine.getToDate() != null ? engine.getToDate().toString() : "?");
        Label optRangeLabel = new Label("Optimierungszeitraum (bereits verbraucht): " + optRange);
        optRangeLabel.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 11px;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);

        Label fromLabel = new Label("Validierung von:");
        fromLabel.setStyle("-fx-text-fill: #b4bac8;");
        DatePicker fromPicker = new DatePicker(engine.getEffectiveValidationFromDate());

        Label toLabel = new Label("Validierung bis:");
        toLabel.setStyle("-fx-text-fill: #b4bac8;");
        DatePicker toPicker = new DatePicker(engine.getEffectiveValidationToDate());

        grid.add(fromLabel, 0, 0);
        grid.add(fromPicker, 1, 0);
        grid.add(toLabel, 0, 1);
        grid.add(toPicker, 1, 1);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-text-fill: #ffd740; -fx-font-size: 11px;");
        Runnable refreshStatus = () -> {
            LocalDate f = fromPicker.getValue();
            LocalDate t = toPicker.getValue();
            if (f == null || t == null || !f.isBefore(t)) {
                statusLabel.setText("⚠ Ungültiges Fenster: 'von' muss vor 'bis' liegen.");
                statusLabel.setStyle("-fx-text-fill: #ff5252; -fx-font-size: 11px;");
            } else if (engine.getToDate() != null && !f.isAfter(engine.getToDate())) {
                statusLabel.setText("⚠ Fenster überlappt mit dem Optimierungszeitraum — Ergebnis wäre KEINE echte Out-of-Sample-Validierung!");
                statusLabel.setStyle("-fx-text-fill: #ff5252; -fx-font-size: 11px;");
            } else {
                long days = java.time.temporal.ChronoUnit.DAYS.between(f, t);
                statusLabel.setText("✓ Gültiges Fenster (" + days + " Tage unberührte Daten)." +
                        (days < 30 ? " Hinweis: Kurze Fenster liefern nur schwache Evidenz." : ""));
                statusLabel.setStyle("-fx-text-fill: #00e676; -fx-font-size: 11px;");
            }
        };
        fromPicker.valueProperty().addListener((o, a, b) -> refreshStatus.run());
        toPicker.valueProperty().addListener((o, a, b) -> refreshStatus.run());
        refreshStatus.run();

        // Bisherige Ergebnisse anzeigen
        VBox resultsBox = new VBox(4);
        if (engine.getValidationResults() != null && !engine.getValidationResults().isEmpty()) {
            Label resTitle = new Label("Letzte Validierungsergebnisse:");
            resTitle.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold; -fx-font-size: 12px;");
            resultsBox.getChildren().add(resTitle);
            for (com.backtester.report.ValidationResult vr : engine.getValidationResults()) {
                Label line = new Label(vr.toSummaryLine());
                boolean insufficient = com.backtester.report.ValidationResult.INSUFFICIENT_EVIDENCE.equals(vr.getVerdict());
                line.setStyle("-fx-font-size: 11px; -fx-text-fill: " +
                        (vr.isPassed() ? "#00e676" : (insufficient ? "#ffd740" : "#ff5252")) + ";");
                resultsBox.getChildren().add(line);
            }
        }

        Button saveBtn = new Button("✔ Übernehmen & Schließen");
        saveBtn.setStyle("-fx-background-color: #00e5ff; -fx-text-fill: #0d0f17; -fx-font-weight: bold;");
        saveBtn.setOnAction(e -> {
            engine.setValidationFromDate(fromPicker.getValue());
            engine.setValidationToDate(toPicker.getValue());
            engine.saveStrategyConfig(engine.getExpert());
            engine.saveState();
            stage.close();
        });

        Button cancelBtn = new Button("Abbrechen");
        cancelBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #b4bac8; -fx-border-color: #444; -fx-border-width: 1;");
        cancelBtn.setOnAction(e -> stage.close());

        HBox btnRow = new HBox(10, saveBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        layout.getChildren().addAll(title, info, optRangeLabel, grid, statusLabel, resultsBox, btnRow);

        Scene scene = new Scene(layout);
        stage.setScene(scene);
        WorkflowConfigDialogSupport.applyTheme(stage, owner);
        stage.showAndWait();
    }

}
