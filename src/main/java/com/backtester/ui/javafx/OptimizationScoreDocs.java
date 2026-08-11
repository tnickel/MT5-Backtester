package com.backtester.ui.javafx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Static helpers for CV / score / consistency documentation dialogs used by OptimizationView.
 */
public final class OptimizationScoreDocs {

    private OptimizationScoreDocs() {}

    private static Label styledLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #b4bac8;");
        return l;
    }

    public static void showCvExplanationDialog(javafx.stage.Window owner, String title, String mainHeading, String htmlBodyContent) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle(title);
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }

        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setStyle("-fx-background-color: #0b0d13; -fx-border-color: #3e4555; -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px;");

        Label titleLabel = new Label(mainHeading);
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        titleLabel.setTextFill(Color.web("#00e5ff"));

        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        webView.setPrefSize(800, 500);

        String fullHtml = "<html><head><style>"
                + "body { background-color:#161821; color:#c8cddc; font-family:\"Segoe UI\", sans-serif; font-size:16px; line-height:1.7; margin:20px; }"
                + "h3 { color:#00e5ff; font-size:20px; margin-top:20px; border-bottom: 1px solid #3e4555; padding-bottom: 5px; font-weight: bold; }"
                + "h4 { color:#e2e8f0; font-size:17px; margin-top:15px; font-weight: bold; }"
                + "code { background-color:#1f2937; padding:4px 8px; border-radius:4px; color:#38bdf8; font-family:Consolas, monospace; font-size:14px; display:block; margin:8px 0; }"
                + "ul, ol { margin-left: 20px; padding-left: 0; }"
                + "li { margin-bottom: 8px; }"
                + "</style></head><body>"
                + htmlBodyContent
                + "</body></html>";
        webView.getEngine().loadContent(fullHtml);
        webView.setStyle("-fx-background-color: #161821;");

        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().addAll("button");
        closeBtn.setStyle("-fx-background-color: #374151; -fx-text-fill: #d1d5db; -fx-font-weight: bold; -fx-padding: 8 16; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> stage.close());

        HBox btnBox = new HBox(closeBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        box.getChildren().addAll(titleLabel, webView, btnBox);
        VBox.setVgrow(webView, Priority.ALWAYS);

        javafx.scene.Scene scene = new javafx.scene.Scene(box, 850, 650);
        stage.setScene(scene);
        stage.show();
    }

    public static String getBtCvExplanationHtml() {
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

    public static String getFwCvExplanationHtml() {
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
             + "  <li><span style='color:#00e676; font-weight:bold;'>&lt; 30% (Robust):</span> Exzellente Stabilität auch auf unbekannten Zukunftsdaten (Forward).</li>"
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

    public static void showScoreDoc(javafx.stage.Window owner,
                                     double initialMinScore,
                                     java.util.function.DoubleConsumer setMinScore,
                                     Runnable enableFilter,
                                     Runnable applyCombinedFilter) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Strategie-Score - Dokumentation & Filter");
        stage.initModality(javafx.stage.Modality.NONE);
        if (owner != null) {
            stage.initOwner(owner);
        }
        final double[] currentMinScore = { initialMinScore };

        VBox mainBox = new VBox(15);
        mainBox.setPadding(new Insets(20));
        mainBox.setStyle("-fx-background-color: #0d0f17;");

        // Header
        Label titleLabel = new Label("🏆 Strategie-Score (Kombinierter Filter)");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        titleLabel.setTextFill(Color.web("#00e5ff"));

        // Documentation Area
        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        webView.setPrefSize(950, 500);
        webView.setStyle("-fx-background-color: #161821;");

        webView.getEngine().loadContent(DocHelper.getScoreDocHtml());

        // Filter Controls Area (Glassmorphic style panel)
        VBox filterBox = new VBox(10);
        filterBox.setPadding(new Insets(15));
        filterBox.setStyle("-fx-background-color: #1a1d27; -fx-border-color: #3e4555; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px;");

        Label sliderTitle = new Label("Score-Filter konfigurieren");
        sliderTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        sliderTitle.setTextFill(Color.web("#e2e8f0"));

        Slider slider = new Slider(0, 100, initialMinScore);
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setMajorTickUnit(20);
        slider.setMinorTickCount(5);
        slider.setBlockIncrement(5);
        slider.setStyle("-fx-control-inner-background: #2a2d3a;");

        Label valLabel = new Label(String.format(java.util.Locale.US, "Mindest-Score: %.1f", initialMinScore));
        valLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        valLabel.setTextFill(Color.web("#00e5ff"));

        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            currentMinScore[0] = Math.round(newVal.doubleValue() * 10.0) / 10.0;
            if (setMinScore != null) setMinScore.accept(currentMinScore[0]);
            valLabel.setText(String.format(java.util.Locale.US, "Mindest-Score: %.1f", currentMinScore[0]));
        });

        Button btnLow = new Button("Low / Zahm (30.0)");
        btnLow.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #a7f3d0; -fx-border-color: #10b981; -fx-border-width: 1; -fx-cursor: hand;");
        btnLow.setOnAction(e -> slider.setValue(30.0));

        Button btnMed = new Button("Med / Ausgewogen (50.0)");
        btnMed.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fde047; -fx-border-color: #eab308; -fx-border-width: 1; -fx-cursor: hand;");
        btnMed.setOnAction(e -> slider.setValue(50.0));

        Button btnHigh = new Button("High / Streng (70.0)");
        btnHigh.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fca5a5; -fx-border-color: #ef4444; -fx-border-width: 1; -fx-cursor: hand;");
        btnHigh.setOnAction(e -> slider.setValue(70.0));

        HBox presetRow = new HBox(10, styledLabel("Voreinstellungen:"), btnLow, btnMed, btnHigh);
        presetRow.setAlignment(Pos.CENTER_LEFT);

        filterBox.getChildren().addAll(sliderTitle, presetRow, slider, valLabel);

        // Buttons
        Button okBtn = new Button("✔ OK / Übernehmen");
        okBtn.setStyle("-fx-background-color: #00e5ff; -fx-text-fill: #0d0f17; -fx-font-weight: bold; -fx-padding: 8 16; -fx-cursor: hand;");
        okBtn.setOnAction(e -> stage.close());

        Button cancelBtn = new Button("Abbrechen");
        cancelBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #b4bac8; -fx-border-color: #444; -fx-border-width: 1; -fx-padding: 8 16; -fx-cursor: hand;");
        // Restore original value on cancel
        double originalValue = initialMinScore;
        cancelBtn.setOnAction(e -> {
            currentMinScore[0] = originalValue;
            if (setMinScore != null) setMinScore.accept(originalValue);
            stage.close();
        });

        HBox btnRow = new HBox(10, new Region(), cancelBtn, okBtn);
        HBox.setHgrow(btnRow.getChildren().get(0), Priority.ALWAYS);

        mainBox.getChildren().addAll(titleLabel, webView, filterBox, btnRow);
        VBox.setVgrow(webView, Priority.ALWAYS);

        stage.setOnHiding(e -> {
            if (enableFilter != null) {
                enableFilter.run();
            }
            com.backtester.database.DatabaseManager.getInstance().saveSetting("opt.filter.enabled", "true");
            com.backtester.database.DatabaseManager.getInstance().saveSetting("opt.filter.minScore", String.valueOf(currentMinScore[0]));
            if (applyCombinedFilter != null) {
                applyCombinedFilter.run();
            }
        });

        javafx.scene.Scene scene = new javafx.scene.Scene(mainBox, 1000, 800);
        try {
            java.net.URL css = DocHelper.class.getResource("/css/antigravity.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            } else {
                java.net.URL css2 = DocHelper.class.getResource("/style.css");
                if (css2 != null) scene.getStylesheets().add(css2.toExternalForm());
            }
        } catch (Exception ignored) {}

        stage.setScene(scene);
        stage.show();
    }

    public static void showConsistencyDoc(javafx.stage.Window owner,
                                            double initialMinConsistency,
                                            java.util.function.DoubleConsumer setMinConsistency,
                                            Runnable enableFilter,
                                            Runnable applyCombinedFilter) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Konsistenz - Dokumentation & Filter");
        stage.initModality(javafx.stage.Modality.NONE);
        if (owner != null) {
            stage.initOwner(owner);
        }
        final double[] currentMinConsistency = { initialMinConsistency };

        VBox mainBox = new VBox(15);
        mainBox.setPadding(new Insets(20));
        mainBox.setStyle("-fx-background-color: #0d0f17;");

        // Header
        Label titleLabel = new Label("⚖️ Konsistenz (FW/BT-Verhältnis)");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        titleLabel.setTextFill(Color.web("#00e5ff"));

        // Documentation Area
        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        webView.setPrefSize(950, 500);
        webView.setStyle("-fx-background-color: #161821;");

        webView.getEngine().loadContent(DocHelper.getConsistencyDocHtml());

        // Filter Controls Area (Glassmorphic style panel)
        VBox filterBox = new VBox(10);
        filterBox.setPadding(new Insets(15));
        filterBox.setStyle("-fx-background-color: #1a1d27; -fx-border-color: #3e4555; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px;");

        Label sliderTitle = new Label("Konsistenz-Filter konfigurieren");
        sliderTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        sliderTitle.setTextFill(Color.web("#e2e8f0"));

        Slider slider = new Slider(0.0, 2.0, initialMinConsistency);
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setMajorTickUnit(0.5);
        slider.setMinorTickCount(5);
        slider.setBlockIncrement(0.1);
        slider.setStyle("-fx-control-inner-background: #2a2d3a;");

        Label valLabel = new Label(String.format(java.util.Locale.US, "Mindest-Konsistenz: %.2f", initialMinConsistency));
        valLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        valLabel.setTextFill(Color.web("#00e5ff"));

        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            currentMinConsistency[0] = Math.round(newVal.doubleValue() * 100.0) / 100.0;
            if (setMinConsistency != null) setMinConsistency.accept(currentMinConsistency[0]);
            valLabel.setText(String.format(java.util.Locale.US, "Mindest-Konsistenz: %.2f", currentMinConsistency[0]));
        });

        Button btnLow = new Button("Low / Zahm (0.4)");
        btnLow.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #a7f3d0; -fx-border-color: #10b981; -fx-border-width: 1; -fx-cursor: hand;");
        btnLow.setOnAction(e -> slider.setValue(0.4));

        Button btnMed = new Button("Med / Ausgewogen (0.6)");
        btnMed.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fde047; -fx-border-color: #eab308; -fx-border-width: 1; -fx-cursor: hand;");
        btnMed.setOnAction(e -> slider.setValue(0.6));

        Button btnHigh = new Button("High / Streng (0.8)");
        btnHigh.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fca5a5; -fx-border-color: #ef4444; -fx-border-width: 1; -fx-cursor: hand;");
        btnHigh.setOnAction(e -> slider.setValue(0.8));

        HBox presetRow = new HBox(10, styledLabel("Voreinstellungen:"), btnLow, btnMed, btnHigh);
        presetRow.setAlignment(Pos.CENTER_LEFT);

        filterBox.getChildren().addAll(sliderTitle, presetRow, slider, valLabel);

        // Buttons
        Button okBtn = new Button("✔ OK / Übernehmen");
        okBtn.setStyle("-fx-background-color: #00e5ff; -fx-text-fill: #0d0f17; -fx-font-weight: bold; -fx-padding: 8 16; -fx-cursor: hand;");
        okBtn.setOnAction(e -> stage.close());

        Button cancelBtn = new Button("Abbrechen");
        cancelBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #b4bac8; -fx-border-color: #444; -fx-border-width: 1; -fx-padding: 8 16; -fx-cursor: hand;");
        // Restore original value on cancel
        double originalValue = initialMinConsistency;
        cancelBtn.setOnAction(e -> {
            currentMinConsistency[0] = originalValue;
            if (setMinConsistency != null) setMinConsistency.accept(originalValue);
            stage.close();
        });

        HBox btnRow = new HBox(10, new Region(), cancelBtn, okBtn);
        HBox.setHgrow(btnRow.getChildren().get(0), Priority.ALWAYS);

        mainBox.getChildren().addAll(titleLabel, webView, filterBox, btnRow);
        VBox.setVgrow(webView, Priority.ALWAYS);

        stage.setOnHiding(e -> {
            if (enableFilter != null) {
                enableFilter.run();
            }
            com.backtester.database.DatabaseManager.getInstance().saveSetting("opt.filter.enabled", "true");
            com.backtester.database.DatabaseManager.getInstance().saveSetting("opt.filter.minConsistency", String.valueOf(currentMinConsistency[0]));
            if (applyCombinedFilter != null) {
                applyCombinedFilter.run();
            }
        });

        javafx.scene.Scene scene = new javafx.scene.Scene(mainBox, 1000, 800);
        try {
            java.net.URL css = DocHelper.class.getResource("/css/antigravity.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            } else {
                java.net.URL css2 = DocHelper.class.getResource("/style.css");
                if (css2 != null) scene.getStylesheets().add(css2.toExternalForm());
            }
        } catch (Exception ignored) {}

        stage.setScene(scene);
        stage.show();
    }

}
