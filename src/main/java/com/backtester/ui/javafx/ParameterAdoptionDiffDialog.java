package com.backtester.ui.javafx;

import com.backtester.workflow.ChampionSearchSpaceAligner;
import com.backtester.workflow.GuidedOptimizationService.AdoptionPreview;
import com.backtester.workflow.GuidedOptimizationService.ParameterValueChange;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;

/**
 * Hand-pick confirmation: shows old vs new values for the Pass/stage parameters
 * that were optimized. Automatic mode must not call this.
 */
public final class ParameterAdoptionDiffDialog {

    private ParameterAdoptionDiffDialog() {
    }

    /** @return true if the user confirmed with OK */
    public static boolean confirm(Window owner,
                                  AdoptionPreview preview,
                                  String fidelityText,
                                  String warning) {
        if (preview == null || preview.getNextOptimizer() == null) {
            return false;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Parameter-Basis übernehmen");
        dialog.setHeaderText("Pass #" + preview.getPassNumber()
                + " → " + preview.getNextOptimizer().getName());
        if (owner != null) {
            dialog.initOwner(owner);
        }

        int passChanges = preview.getPassValueChanges().size();
        int otherChanges = preview.getOtherBasisChangeCount();
        StringBuilder summary = new StringBuilder();
        summary.append("Quelle: ").append(preview.getDatabankName())
                .append("\nParameterquelle: ").append(fidelityText != null ? fidelityText : "")
                .append("\nPassparameter übernommen: ").append(preview.getAdoptedParameterCount())
                .append("  ·  Neue Optimierungsziele: ").append(preview.getEnabledTargetCount())
                .append("\n\nUnten nur die geänderten Pass-/Stufenparameter (")
                .append(passChanges).append(").");
        if (otherChanges > 0) {
            summary.append("\nZusätzlich werden ").append(otherChanges)
                    .append(" weitere Werte aus dem Lauf-Preset mitübernommen")
                    .append(" (nicht einzeln gelistet — Basis des Runs, keine Opt-Ziele).");
        }
        summary.append("\n\nMit OK übernehmen.");

        Label summaryLabel = new Label(summary.toString());
        summaryLabel.setWrapText(true);
        summaryLabel.setStyle("-fx-text-fill: #cfd8dc; -fx-font-size: 12px;");

        TableView<ParameterValueChange> table = new TableView<>(
                FXCollections.observableArrayList(preview.getPassValueChanges()));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(360);
        table.setPlaceholder(new Label(
                "Keine Änderungen an Pass-/Stufenparametern — Werte sind bereits identisch."));
        table.setStyle(
                "-fx-background-color: #141822; -fx-control-inner-background: #141822; "
                        + "-fx-table-cell-border-color: #232a3b; -fx-text-fill: #e6e9f0;");

        TableColumn<ParameterValueChange, String> nameCol = new TableColumn<>("Parameter");
        nameCol.setPrefWidth(260);
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue() != null ? c.getValue().getName() : ""));

        TableColumn<ParameterValueChange, String> oldCol = new TableColumn<>("Alter Wert");
        oldCol.setPrefWidth(140);
        oldCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue() != null ? c.getValue().getOldValue() : ""));

        TableColumn<ParameterValueChange, String> newCol = new TableColumn<>("Neuer Wert");
        newCol.setPrefWidth(140);
        newCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue() != null ? c.getValue().getNewValue() : ""));

        table.getColumns().setAll(nameCol, oldCol, newCol);

        VBox content = new VBox(10, summaryLabel);
        if (warning != null && !warning.isBlank()) {
            Label warn = new Label("WARNUNG: " + warning.trim());
            warn.setWrapText(true);
            warn.setStyle("-fx-text-fill: #ffb74d; -fx-font-size: 12px; -fx-font-weight: bold;");
            content.getChildren().add(warn);
        }
        addSearchSpaceNotes(content, preview);
        addStaleDatabankNote(content, preview);
        content.getChildren().add(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        content.setPadding(new Insets(4, 0, 0, 0));
        content.setPrefWidth(720);

        DialogPane pane = dialog.getDialogPane();
        pane.setContent(content);
        pane.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        pane.setStyle("-fx-background-color: #0b0d13;");
        styleDialogButtons(pane);

        return dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    /**
     * Corrections that keep the adopted value reachable in the next stage's grid, plus
     * the cases where that was impossible — those are the spots where the chain can
     * come back weaker than it went in.
     */
    private static void addSearchSpaceNotes(VBox content, AdoptionPreview preview) {
        List<ChampionSearchSpaceAligner.Adjustment> adjustments = preview.getSearchSpaceAdjustments();
        if (adjustments.isEmpty()) return;

        StringBuilder applied = new StringBuilder();
        StringBuilder blocked = new StringBuilder();
        for (ChampionSearchSpaceAligner.Adjustment adjustment : adjustments) {
            StringBuilder target = adjustment.isApplied() ? applied : blocked;
            if (target.length() > 0) target.append('\n');
            target.append("• ").append(adjustment.describe());
        }
        if (applied.length() > 0) {
            Label info = new Label("Suchraum an den übernommenen Wert angepasst, damit die Stufe "
                    + "ihn erreichen kann:\n" + applied);
            info.setWrapText(true);
            info.setStyle("-fx-text-fill: #80cbc4; -fx-font-size: 12px;");
            content.getChildren().add(info);
        }
        if (blocked.length() > 0) {
            Label warn = new Label("Nicht erreichbar — diese Stufe kann den aktuellen Wert nicht "
                    + "reproduzieren und darf sich verschlechtern:\n" + blocked);
            warn.setWrapText(true);
            warn.setStyle("-fx-text-fill: #ef9a9a; -fx-font-size: 12px; -fx-font-weight: bold;");
            content.getChildren().add(warn);
        }
    }

    private static void addStaleDatabankNote(VBox content, AdoptionPreview preview) {
        List<String> stale = preview.getStaleDownstreamDatabanks();
        if (stale.isEmpty()) return;
        Label info = new Label("Nachgelagerte Databanks werden geleert, damit sie nicht mit "
                + "veralteten Ergebnissen übersprungen werden: " + String.join(", ", stale));
        info.setWrapText(true);
        info.setStyle("-fx-text-fill: #90a4ae; -fx-font-size: 11px;");
        content.getChildren().add(info);
    }

    private static void styleDialogButtons(DialogPane pane) {
        Button ok = (Button) pane.lookupButton(ButtonType.OK);
        if (ok != null) {
            ok.setText("OK — Übernehmen");
            ok.setDefaultButton(true);
        }
        Button cancel = (Button) pane.lookupButton(ButtonType.CANCEL);
        if (cancel != null) {
            cancel.setCancelButton(true);
        }
    }
}
