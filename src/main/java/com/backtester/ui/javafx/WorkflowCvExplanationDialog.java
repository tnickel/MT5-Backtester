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
 * CV (worst) explanation dialog for workflow sensitivity.
 */
public final class WorkflowCvExplanationDialog {

    private WorkflowCvExplanationDialog() {}

    public static void showCvExplanationDialog(Window owner, boolean isForward) {
        Stage stage = new Stage();
        stage.setTitle(isForward ? "FW CV (worst) Erklärung" : "BT CV (worst) Erklärung");
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }

        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setStyle("-fx-background-color: #11141d; -fx-border-color: #ffd740; -fx-border-width: 1px; -fx-border-radius: 5px;");

        Label titleLabel = new Label(isForward ? "FW CV (worst) - Forward Variationskoeffizient" : "BT CV (worst) - Backtest Variationskoeffizient");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.web("#00e5ff"));

        WebView webView = new WebView();
        webView.setPrefSize(750, 480);
        
        String htmlBodyContent = isForward ? getFwCvExplanationHtml() : getBtCvExplanationHtml();
        String fullHtml = "<html><head><style>"
                + "body { background-color:#161821; color:#c8cddc; font-family:\"Segoe UI\", sans-serif; font-size:15px; line-height:1.6; margin:15px; }"
                + "h3 { color:#ffd740; font-size:18px; margin-top:15px; border-bottom: 1px solid #3e4555; padding-bottom: 5px; font-weight: bold; }"
                + "h4 { color:#00e5ff; font-size:15px; margin-top:12px; font-weight: bold; }"
                + "code { background-color:#1f2937; padding:4px 8px; border-radius:4px; color:#38bdf8; font-family:Consolas, monospace; font-size:13px; display:block; margin:8px 0; }"
                + "ul, ol { margin-left: 20px; padding-left: 0; }"
                + "li { margin-bottom: 6px; }"
                + "</style></head><body>"
                + htmlBodyContent
                + "</body></html>";
        webView.getEngine().loadContent(fullHtml);
        webView.setStyle("-fx-background-color: #161821;");

        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().addAll("button");
        closeBtn.setOnAction(e -> stage.close());

        HBox btnBox = new HBox(closeBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        box.getChildren().addAll(titleLabel, webView, btnBox);
        VBox.setVgrow(webView, Priority.ALWAYS);

        Scene scene = new Scene(box, 800, 600);
        try {
            scene.getStylesheets().add(WorkflowCvExplanationDialog.class.getResource("/css/antigravity.css").toExternalForm());
        } catch (Exception e) {
            // Ignore
        }
        stage.setScene(scene);
        stage.showAndWait();
    }

    private static String getBtCvExplanationHtml() {
        return "<h3>BT CV (worst) - Backtest Variationskoeffizient</h3>"
             + "<p>Der <b>Variationskoeffizient (CV - Coefficient of Variation)</b> im Backtest-Zeitraum (In-Sample) misst die relative Streuung der Profite, wenn einzelne Optimierungsparameter variiert werden.</p>"
             + "<h4>Berechnung:</h4>"
             + "<p>Für jeden optimierten Parameter wird ein Sweep um die engere Umgebung des Optimalwerts durchgeführt. Daraus wird berechnet:</p>"
             + "<code>"
             + "CV = (Standardabweichung des Profits / Absoluter Basis-Profit) * 100%"
             + "</code>"
             + "<p>Der <b>BT CV (worst)</b> ist der <b>schlechteste (maximale) CV-Wert</b> über alle getesteten Parameter. Eine Strategie ist nur so robust wie ihr empfindlichster Parameter.</p>"
             + "<h4>Bedeutung der Werte:</h4>"
             + "<ul>"
             + "  <li><span style='color:#00e676; font-weight:bold;'>&lt; 30% (Robust):</span> Sehr stabil. Parameteränderungen in der nahen Umgebung haben kaum Einfluss auf das Endergebnis.</li>"
             + "  <li><span style='color:#ffd740; font-weight:bold;'>30% - 60% (Akzeptabel):</span> Mäßige Empfindlichkeit. Vertretbares Risiko für Überoptimierung.</li>"
             + "  <li><span style='color:#ff3b30; font-weight:bold;'>&gt; 60% (Fragil):</span> Sehr empfindlich. Kleine Parameteränderungen führen zu massiven Unterschieden im Gewinn oder Verlust.</li>"
             + "</ul>"
             + "<h4>Warum sind die Werte manchmal so hoch (z.B. 200%)?</h4>"
             + "<ol>"
             + "  <li><b>Geringer Basis-Profit:</b> Da der Basis-Profit im Nenner steht, explodiert der CV-Wert bei profitarmen Strategien. Wenn eine Strategie z.B. nur 10 € Gewinn macht, führt eine kleine Schwankung um 20 € bereits zu einem CV von 200%.</li>"
             + "  <li><b>Harte Filterung:</b> Wir testen die Parameter isoliert durch erneutes Backtesting. Fällt der Profit bei einer kleinen Änderung eines Parameters stark ab, deutet das auf <i>Curve-Fitting</i> (Überoptimierung) hin. Ein hoher CV warnt dich vor unzuverlässigen Strategien.</li>"
             + "  <li><b>Capping:</b> Um extreme Werte übersichtlich darzustellen, deckeln wir den angezeigten CV-Wert bei maximal 200%.</li>"
             + "</ol>";
    }

    private static String getFwCvExplanationHtml() {
        return "<h3>FW CV (worst) - Forward Variationskoeffizient</h3>"
             + "<p>Der <b>Variationskoeffizient (CV - Coefficient of Variation)</b> im Forward-Zeitraum (Out-of-Sample) misst die relative Streuung der Profite im Forward-Test, wenn die Parameter variiert werden.</p>"
             + "<h4>Berechnung:</h4>"
             + "<p>Es wird derselbe Parameter-Sweep wie im Backtest durchgeführt, jedoch ausschließlich auf den Out-of-Sample Forward-Daten:</p>"
             + "<code>"
             + "CV = (Standardabweichung des Profits / Absoluter Forward-Profit) * 100%"
             + "</code>"
             + "<p>Der <b>FW CV (worst)</b> zeigt den maximalen CV-Wert aller Parameter im Forward-Test-Zeitraum.</p>"
             + "<h4>Bedeutung der Werte:</h4>"
             + "<ul>"
             + "  <li><span style='color:#00e676; font-weight:bold;'>&lt; 30% (Robust):</span> Exzellente Stabilität auch auf unbekannten Zukundfdaten (Forward).</li>"
             + "  <li><span style='color:#ffd740; font-weight:bold;'>30% - 60% (Akzeptabel):</span> Vertretbare Abweichung im Forward-Zeitraum.</li>"
             + "  <li><span style='color:#ff3b30; font-weight:bold;'>&gt; 60% (Fragil):</span> Extrem unzuverlässiges Verhalten in der Forward-Phase bei minimalen Parameterverschiebungen.</li>"
             + "</ul>"
             + "<h4>Warum sind die Werte manchmal so hoch (z.B. 200%)?</h4>"
             + "<ol>"
             + "  <li><b>Geringer Forward-Profit:</b> Im Forward-Zeitraum sind die Gewinne oft noch kleiner oder nahe null. Dadurch wird der Nenner sehr klein, was zu extrem hohen Prozentwerten führt.</li>"
             + "  <li><b>Verlustphasen im Forward:</b> Wenn der Forward-Test schlechter läuft (was oft vorkommt, da Out-of-Sample-Daten), steigt die Standardabweichung im Verhältnis zum Profit drastisch an.</li>"
             + "  <li><b>Capping:</b> Um extreme Werte übersichtlich darzustellen, deckeln wir den angezeigten CV-Wert bei maximal 200%.</li>"
             + "</ol>";
    }

}
