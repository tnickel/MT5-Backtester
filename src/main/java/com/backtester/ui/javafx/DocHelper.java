package com.backtester.ui.javafx;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class DocHelper {
    public static javafx.scene.Node createHeaderWithTooltip(String title, String tooltipText) {
        return createHeaderWithTooltip(title, tooltipText, null);
    }

    public static javafx.scene.Node createHeaderWithTooltip(String title, String tooltipText, Runnable clickAction) {
        javafx.scene.layout.HBox hbox = new javafx.scene.layout.HBox(4);
        hbox.setAlignment(javafx.geometry.Pos.CENTER);

        javafx.scene.control.Label label = new javafx.scene.control.Label(title);
        if (title.equals("Score")) {
            label.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold;");
        } else {
            label.setStyle("-fx-text-fill: #e6e9f0;");
        }

        javafx.scene.control.Label infoLabel = new javafx.scene.control.Label("ⓘ");
        infoLabel.setStyle("-fx-text-fill: #7e889a; -fx-cursor: hand; -fx-font-size: 11px;");

        if (clickAction != null) {
            infoLabel.setOnMouseClicked(e -> {
                clickAction.run();
                e.consume(); // Prevents triggering column sorting
            });
        }

        javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(tooltipText);
        tooltip.setShowDelay(javafx.util.Duration.millis(100));
        tooltip.setHideDelay(javafx.util.Duration.millis(5000));
        tooltip.setMaxWidth(350);
        tooltip.setWrapText(true);
        tooltip.setStyle("-fx-font-size: 12px; -fx-background-color: #1e293b; -fx-text-fill: #f8fafc; -fx-border-color: #475569; -fx-border-width: 1px; -fx-border-radius: 4px;");
        javafx.scene.control.Tooltip.install(infoLabel, tooltip);

        hbox.getChildren().addAll(label, infoLabel);
        return hbox;
    }

    public static Button createInfoButton(String tabName, String overview, String details) {
        Button infoBtn = new Button("ℹ");
        String normalStyle = "-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-radius: 50%; -fx-min-width: 28px; -fx-min-height: 28px; -fx-text-fill: #00e5ff; -fx-background-color: transparent; -fx-border-color: #00e5ff; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0;";
        String hoverStyle = "-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-radius: 50%; -fx-min-width: 28px; -fx-min-height: 28px; -fx-text-fill: #111111; -fx-background-color: #00e5ff; -fx-border-color: #00e5ff; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0;";

        infoBtn.setStyle(normalStyle);
        infoBtn.setOnAction(e -> showDoc(tabName, overview, details));

        infoBtn.setOnMouseEntered(e -> infoBtn.setStyle(hoverStyle));
        infoBtn.setOnMouseExited(e -> infoBtn.setStyle(normalStyle));

        return infoBtn;
    }

    public static Button createSmallInfoButton(String tabName, String overview, String details) {
        Button infoBtn = new Button("ℹ");
        String normalStyle = "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 50%; -fx-min-width: 18px; -fx-min-height: 18px; -fx-max-width: 18px; -fx-max-height: 18px; -fx-text-fill: #00e5ff; -fx-background-color: transparent; -fx-border-color: #00e5ff; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0;";
        String hoverStyle = "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 50%; -fx-min-width: 18px; -fx-min-height: 18px; -fx-max-width: 18px; -fx-max-height: 18px; -fx-text-fill: #111111; -fx-background-color: #00e5ff; -fx-border-color: #00e5ff; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0;";

        infoBtn.setStyle(normalStyle);
        infoBtn.setOnAction(e -> showDoc(tabName, overview, details));

        infoBtn.setOnMouseEntered(e -> infoBtn.setStyle(hoverStyle));
        infoBtn.setOnMouseExited(e -> infoBtn.setStyle(normalStyle));

        return infoBtn;
    }

    public static void showDoc(String tabName, String overview, String details) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle(tabName + " - Dokumentation");
        stage.initModality(javafx.stage.Modality.NONE); // Allow interaction with main window
        stage.setMinWidth(600);
        stage.setMinHeight(400);

        Label overviewTitle = new Label(tabName + " - Übersicht");
        overviewTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 0 0 10 0; -fx-text-fill: white;");

        Label overviewLabel = new Label(overview);
        overviewLabel.setWrapText(true);
        overviewLabel.setStyle("-fx-font-size: 14px; -fx-padding: 0 0 15 0; -fx-text-fill: #e2e8f0;");

        Label detailLabel = new Label("Details:");
        detailLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-padding: 5 0 5 0; -fx-text-fill: white;");

        TextArea textArea = new TextArea(details);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setStyle("-fx-font-size: 14px; -fx-font-family: 'Segoe UI', sans-serif;");
        javafx.scene.layout.VBox.setVgrow(textArea, Priority.ALWAYS);

        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(5);
        root.setPadding(new javafx.geometry.Insets(20));
        root.setStyle("-fx-background-color: #1a1d27;");
        root.getChildren().addAll(overviewTitle, overviewLabel, detailLabel, textArea);

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 900, 700);
        try {
            java.net.URL css = DocHelper.class.getResource("/css/antigravity.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            } else {
                java.net.URL css2 = DocHelper.class.getResource("/style.css");
                if (css2 != null) scene.getStylesheets().add(css2.toExternalForm());
            }
        } catch(Exception ignored) {}

        stage.setScene(scene);
        stage.show();
    }

    public static Button createSmallInfoButton(Runnable action) {
        Button infoBtn = new Button("ℹ");
        String normalStyle = "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 50%; -fx-min-width: 18px; -fx-min-height: 18px; -fx-max-width: 18px; -fx-max-height: 18px; -fx-text-fill: #00e5ff; -fx-background-color: transparent; -fx-border-color: #00e5ff; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0;";
        String hoverStyle = "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 50%; -fx-min-width: 18px; -fx-min-height: 18px; -fx-max-width: 18px; -fx-max-height: 18px; -fx-text-fill: #111111; -fx-background-color: #00e5ff; -fx-border-color: #00e5ff; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0;";

        infoBtn.setStyle(normalStyle);
        infoBtn.setOnAction(e -> action.run());

        infoBtn.setOnMouseEntered(e -> infoBtn.setStyle(hoverStyle));
        infoBtn.setOnMouseExited(e -> infoBtn.setStyle(normalStyle));

        return infoBtn;
    }

    public static Button createSmallInfoButton(String tooltip, Runnable action) {
        Button btn = createSmallInfoButton(action);
        btn.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        return btn;
    }

    public static void showScoreDocDialog(javafx.stage.Window owner) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Strategie-Score - Dokumentation");
        stage.initModality(javafx.stage.Modality.NONE);
        if (owner != null) {
            stage.initOwner(owner);
        }

        javafx.scene.layout.VBox mainBox = new javafx.scene.layout.VBox(15);
        mainBox.setPadding(new javafx.geometry.Insets(20));
        mainBox.setStyle("-fx-background-color: #0d0f17;");

        Label titleLabel = new Label("🏆 Strategie-Score (Kombinierter Filter)");
        titleLabel.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 22));
        titleLabel.setTextFill(javafx.scene.paint.Color.web("#00e5ff"));

        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        webView.setPrefSize(950, 600);
        webView.setStyle("-fx-background-color: #161821;");

        webView.getEngine().loadContent(getScoreDocHtml());

        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().add("button");
        closeBtn.setOnAction(e -> stage.close());

        javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(closeBtn);
        btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        mainBox.getChildren().addAll(titleLabel, webView, btnRow);
        javafx.scene.layout.VBox.setVgrow(webView, javafx.scene.layout.Priority.ALWAYS);

        javafx.scene.Scene scene = new javafx.scene.Scene(mainBox, 1000, 750);
        try {
            java.net.URL css = DocHelper.class.getResource("/css/antigravity.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
        } catch (Exception ignored) {}

        stage.setScene(scene);
        stage.show();
    }

    public static void showConsistencyDocDialog(javafx.stage.Window owner) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Konsistenz - Dokumentation");
        stage.initModality(javafx.stage.Modality.NONE);
        if (owner != null) {
            stage.initOwner(owner);
        }

        javafx.scene.layout.VBox mainBox = new javafx.scene.layout.VBox(15);
        mainBox.setPadding(new javafx.geometry.Insets(20));
        mainBox.setStyle("-fx-background-color: #0d0f17;");

        Label titleLabel = new Label("⚖️ Konsistenz (FW/BT-Verhältnis)");
        titleLabel.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 22));
        titleLabel.setTextFill(javafx.scene.paint.Color.web("#00e5ff"));

        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        webView.setPrefSize(950, 600);
        webView.setStyle("-fx-background-color: #161821;");

        webView.getEngine().loadContent(getConsistencyDocHtml());

        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().add("button");
        closeBtn.setOnAction(e -> stage.close());

        javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(closeBtn);
        btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        mainBox.getChildren().addAll(titleLabel, webView, btnRow);
        javafx.scene.layout.VBox.setVgrow(webView, javafx.scene.layout.Priority.ALWAYS);

        javafx.scene.Scene scene = new javafx.scene.Scene(mainBox, 1000, 750);
        try {
            java.net.URL css = DocHelper.class.getResource("/css/antigravity.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
        } catch (Exception ignored) {}

        stage.setScene(scene);
        stage.show();
    }

    public static String getScoreDocHtml() {
        return "<html><head><style>"
            + "body { background-color:#161821; color:#c8cddc; font-family:\"Segoe UI\", sans-serif; font-size:16px; line-height:1.7; margin:20px; }"
            + "h3 { color:#00e5ff; font-size:20px; margin-top:20px; border-bottom: 1px solid #3e4555; padding-bottom: 5px; font-weight: bold; }"
            + "h4 { color:#e2e8f0; font-size:17px; margin-top:15px; font-weight: bold; }"
            + "table { width:100%; border-collapse: collapse; margin: 15px 0; color:#c8cddc; font-size:15px; }"
            + "th { background-color: #1f2937; color: #00e5ff; font-weight: bold; padding: 10px; text-align: left; border: 1px solid #3e4555; }"
            + "td { padding: 10px; border: 1px solid #3e4555; }"
            + "tr:nth-child(even) { background-color: #1d202f; }"
            + "code { background-color:#1f2937; padding:4px 8px; border-radius:4px; color:#38bdf8; font-family:Consolas, monospace; font-size:14px; }"
            + "ul, ol { margin-left: 20px; padding-left: 0; }"
            + "li { margin-bottom: 8px; }"
            + "</style></head><body>"
            + "<h3>Wie wird der Strategie-Score berechnet? (Absolutes Bewertungssystem)</h3>"
            + "<p>Der <b>kombinierte Score (0 - 100)</b> bewertet die Qualität und Robustheit einer Strategie anhand von festen, absoluten Qualitätskriterien. Dadurch wird verhindert, dass eine mittelmäßige Strategie nur deshalb gut bewertet wird, weil alle anderen in diesem Durchlauf noch schlechter sind. Gleichzeitig können exzellente Strategien problemlos hohe, grüne Scores (70 bis 100) erreichen.</p>"
            + "<div style='background-color:#1e293b; border-left:4px solid #ffd740; padding:12px; margin:15px 0; border-radius:4px;'>"
            + "  <strong style='color:#ffd740;'>⚠️ WICHTIGER UNTERSCHIED ZUM ROBUSTNESS SCORE:</strong><br>"
            + "  Der hier berechnete <b>Gesamt-Score</b> bewertet ausschließlich die endgültigen Kennzahlen am Schluss des Testzeitraums (Gewinn, Drawdown, Profit Factor etc.). Er ist blind für den eigentlichen Verlauf der Equity-Kurve (Kapitallinie). "
            + "  <b>Nur der Robustness Score</b> analysiert per linearer Regression (R²-Stabilität) die tatsächliche Struktur und Linearität der Kennlinie, um Glückstreffer, Ausreißer oder instabile Verläufe aufzudecken."
            + "</div>"
            + "<table>"
            + "  <tr>"
            + "    <th>Kriterium</th>"
            + "    <th>Bewertung & absolute Grenzen für den Teil-Score (0.0 bis 1.0)</th>"
            + "  </tr>"
            + "  <tr>"
            + "    <td><b>BT Profit (ROI)</b></td>"
            + "    <td>Netto-Rendite bezogen auf das Startguthaben im Backtest-Zeitraum.<br>"
            + "        Formel: <code>BT ROI = Gewinn / Startguthaben</code><br>"
            + "        <span style='color:#00e676; font-weight:bold;'>ROI &ge; 30% &rarr; volle 1.0 Punkte</span>. ROI &le; 0% &rarr; 0.0 Punkte.<br>"
            + "        Lineare Skalierung dazwischen: <code>Teil-Score = BT ROI / 0.30</code>.</td>"
            + "  </tr>"
            + "  <tr>"
            + "    <td><b>FW Profit (ROI)</b></td>"
            + "    <td>Netto-Rendite bezogen auf das Startguthaben im Out-of-Sample Forward-Zeitraum.<br>"
            + "        Formel: <code>FW ROI = Gewinn / Startguthaben</code><br>"
            + "        <span style='color:#00e676; font-weight:bold;'>ROI &ge; 10% &rarr; volle 1.0 Punkte</span>. ROI &le; 0% &rarr; 0.0 Punkte.<br>"
            + "        Lineare Skalierung dazwischen: <code>Teil-Score = FW ROI / 0.10</code>.</td>"
            + "  </tr>"
            + "  <tr>"
            + "    <td><b>Konsistenz FW/BT</b></td>"
            + "    <td>Das Verhältnis des Forward-Gewinns zum Backtest-Gewinn.<br>"
            + "        Formel: <code>Konsistenz = Forward-Gewinn / Backtest-Gewinn</code> (begrenzt auf max. 2.0).<br>"
            + "        <span style='color:#00e676; font-weight:bold;'>Verhältnis &ge; 1.0 &rarr; volle 1.0 Punkte</span>. Verhältnis &le; 0.2 &rarr; 0.0 Punkte.<br>"
            + "        Lineare Skalierung dazwischen: <code>Teil-Score = (Konsistenz - 0.2) / 0.8</code>.</td>"
            + "  </tr>"
            + "  <tr>"
            + "    <td><b>Profit Factor (PF)</b></td>"
            + "    <td>Verhältnis von Bruttogewinn zu Bruttoverlust (separat für Backtest und Forward).<br>"
            + "        <span style='color:#00e676; font-weight:bold;'>PF &ge; 2.0 &rarr; volle 1.0 Punkte</span>. PF &le; 1.0 (Verlustzone) &rarr; 0.0 Punkte.<br>"
            + "        Lineare Skalierung dazwischen: <code>Teil-Score = (PF - 1.0) / 1.0</code>.</td>"
            + "  </tr>"
            + "  <tr>"
            + "    <td><b>Drawdown-Strafe (DD)</b></td>"
            + "    <td>Maximaler prozentualer Kontorückgang (separat für Backtest und Forward).<br>"
            + "        <span style='color:#00e676; font-weight:bold;'>DD &le; 5% &rarr; keine Abzüge (1.0 Punkte)</span>. DD &ge; 25% &rarr; maximaler Abzug (0.0 Punkte).<br>"
            + "        Lineare Skalierung dazwischen: <code>Teil-Score = 1.0 - (DD% - 5.0) / 20.0</code>.</td>"
            + "  </tr>"
            + "  <tr>"
            + "    <td><b>Forward Trade Count</b></td>"
            + "    <td>Anzahl ausgeführter Trades im Forward-Testzeitraum.<br>"
            + "        <span style='color:#00e676; font-weight:bold;'>&ge; 30 Trades &rarr; volle 1.0 Punkte</span>. &le; 5 Trades &rarr; 0.0 Punkte.<br>"
            + "        Lineare Skalierung dazwischen: <code>Teil-Score = (Trades - 5.0) / 25.0</code>.</td>"
            + "  </tr>"
            + "</table>"
            + "<h3>Gewichtete Zusammenführung & Formel</h3>"
            + "<p>Der Gesamt-Score (0 bis 100) berechnet sich als gewichteter Durchschnitt dieser einzelnen Teil-Scores:</p>"
            + "<div style='background-color:#1f2937; padding:12px; border-radius:6px; color:#38bdf8; font-family:Consolas, monospace; font-size:14px; line-height:1.5; border: 1px solid #3e4555;'>"
            + "Raw Score = (w_BT_Profit * BT_Profit_Score + w_FW_Profit * FW_Profit_Score + w_Konsistenz * Konsistenz_Score<br>"
            + "            + w_BT_PF * BT_PF_Score + w_FW_PF * FW_PF_Score + w_BT_DD * BT_DD_Score + w_FW_DD * FW_DD_Score<br>"
            + "            + w_FW_Trades * FW_Trades_Score) / Gesamtgewicht * 100"
            + "</div>"
            + "<p><i>Hinweis: Die Gewichtungen können Sie im Hauptfenster unter 'Parameter & Optimierungs-Gewichte' individuell einstellen.</i></p>"
            + "<h3>Konkretes Rechenbeispiel</h3>"
            + "<p>Nehmen wir eine Strategie mit folgenden Werten bei einem Startguthaben von 10.000 €:</p>"
            + "<ul>"
            + "  <li><b>BT Gewinn:</b> 2.000 € (ROI = 20%) &rarr; Teil-Score: 20% / 30% = <b>0.67</b></li>"
            + "  <li><b>FW Gewinn:</b> 800 € (ROI = 8%) &rarr; Teil-Score: 8% / 10% = <b>0.80</b></li>"
            + "  <li><b>Konsistenz:</b> 800 € / 2.000 € = 0.40 &rarr; Teil-Score: (0.40 - 0.2) / 0.8 = <b>0.25</b></li>"
            + "  <li><b>BT Profit Factor:</b> 1.80 &rarr; Teil-Score: (1.8 - 1.0) / 1.0 = <b>0.80</b></li>"
            + "  <li><b>FW Profit Factor:</b> 1.50 &rarr; Teil-Score: (1.5 - 1.0) / 1.0 = <b>0.50</b></li>"
            + "  <li><b>BT Drawdown:</b> 8.0% &rarr; Teil-Score: 1.0 - (8.0 - 5.0) / 20.0 = <b>0.85</b></li>"
            + "  <li><b>FW Drawdown:</b> 12.0% &rarr; Teil-Score: 1.0 - (12.0 - 5.0) / 20.0 = <b>0.65</b></li>"
            + "  <li><b>FW Trades:</b> 20 &rarr; Teil-Score: (20 - 5) / 25 = <b>0.60</b></li>"
            + "</ul>"
            + "<p>Unter Annahme der Standard-Gewichte (BT Profit: 25%, FW Profit: 35%, Konsistenz: 20%, BT PF: 5%, FW PF: 10%, BT DD: 2.5%, FW DD: 2.5%, FW Trades: 5% &rarr; Gesamtgewicht = 105%):</p>"
            + "<div style='background-color:#1f2937; padding:12px; border-radius:6px; color:#38bdf8; font-family:Consolas, monospace; font-size:14px; border: 1px solid #3e4555;'>"
            + "Zähler = 0.25*0.67 + 0.35*0.80 + 0.20*0.25 + 0.05*0.80 + 0.10*0.50 + 0.025*0.85 + 0.025*0.65 + 0.05*0.60<br>"
            + "       = 0.1675 + 0.2800 + 0.0500 + 0.0400 + 0.0500 + 0.02125 + 0.01625 + 0.0300 = 0.655<br>"
            + "Raw Score = (0.655 / 1.05) * 100 = <b>62.4</b>"
            + "</div>"
            + "<h3>Dämpfung bei zu geringer Tradeanzahl (Malus)</h3>"
            + "<p>Um zufällige Ausreißer auszuschließen, erhält jede Strategie, deren Forward-Trade-Anzahl unter einer dynamischen Schwelle liegt (definiert als <b>die Hälfte des Medians aller Forward-Trades dieser Optimierung</b>), eine Strafe (Malus):</p>"
            + "<div style='background-color:#1f2937; padding:12px; border-radius:6px; color:#38bdf8; font-family:Consolas, monospace; font-size:14px; border: 1px solid #3e4555;'>"
            + "Dämpfungsfaktor = Max(0.5, Forward Trades / Schwelle)"
            + "</div>"
            + "<p>Der berechnete Score wird mit diesem Faktor multipliziert. Ein minimaler Dämpfungsfaktor von 0.50 sorgt dafür, dass die anderen Qualitätsmetriken bei extrem trade-armen, aber ansonsten guten Strategien nicht vollständig entwertet werden.</p>"
            + "</body></html>";
    }

    public static String getConsistencyDocHtml() {
        return "<html><head><style>"
            + "body { background-color:#161821; color:#c8cddc; font-family:\"Segoe UI\", sans-serif; font-size:16px; line-height:1.7; margin:20px; }"
            + "h3 { color:#00e5ff; font-size:20px; margin-top:20px; border-bottom: 1px solid #3e4555; padding-bottom: 5px; font-weight: bold; }"
            + "h4 { color:#e2e8f0; font-size:17px; margin-top:15px; font-weight: bold; }"
            + "table { width:100%; border-collapse: collapse; margin: 15px 0; color:#c8cddc; font-size:15px; }"
            + "th { background-color: #1f2937; color: #00e5ff; font-weight: bold; padding: 10px; text-align: left; border: 1px solid #3e4555; }"
            + "td { padding: 10px; border: 1px solid #3e4555; }"
            + "tr:nth-child(even) { background-color: #1d202f; }"
            + "code { background-color:#1f2937; padding:4px 8px; border-radius:4px; color:#38bdf8; font-family:Consolas, monospace; font-size:14px; }"
            + "ul, ol { margin-left: 20px; padding-left: 0; }"
            + "li { margin-bottom: 8px; }"
            + "</style></head><body>"
            + "<h3>Was bedeutet Konsistenz?</h3>"
            + "<p>Die <b>Konsistenz (0.0 - 1.0+)</b> misst die Stabilität deiner Strategie beim Übergang von bekannten historischen Daten (Backtest / In-Sample) auf ungesehene Zukunft-Daten (Forward / Out-of-Sample). Sie ist einer der wichtigsten Indikatoren für den Schutz vor Überoptimierung (Curve-Fitting).</p>"
            + "<div style='background-color:#1e293b; border-left:4px solid #ffd740; padding:12px; margin:15px 0; border-radius:4px;'>"
            + "  <strong style='color:#ffd740;'>⚠️ HINWEIS ZUR KENNLINIE:</strong><br>"
            + "  Die Konsistenz misst ausschließlich das Gewinnverhältnis zwischen Forward und Backtest. Sie analysiert <b>nicht</b> die Form oder Stetigkeit der Equity-Kurve. Dies übernimmt der <b>Robustness Score</b> per linearer Regression (R²-Stabilität)."
            + "</div>"
            + "<h3>Berechnung & Bewertung:</h3>"
            + "<div style='background-color:#1f2937; padding:12px; border-radius:6px; color:#38bdf8; font-family:Consolas, monospace; font-size:14px; border: 1px solid #3e4555;'>"
            + "Konsistenz = Forward Net Profit / Backtest Net Profit"
            + "</div>"
            + "<p>Der berechnete Konsistenzwert wird auf den Bereich [0.0, 2.0] begrenzt. Daraus ergibt sich der normierte Teil-Score für die Gesamtbewertung:</p>"
            + "<ul>"
            + "  <li><span style='color:#00e676; font-weight:bold;'>Konsistenz &ge; 1.0 (Teil-Score = 1.0):</span> Der Gewinn in der Forward-Phase ist mindestens so hoch wie im Backtest. Exzellentes Ergebnis!</li>"
            + "  <li><span style='color:#ffd740; font-weight:bold;'>Konsistenz 0.2 bis 1.0 (Linear abfallend):</span> Typisches Verhalten. Die Performance schwächt sich auf ungesehenen Daten ab. Ein Konsistenzwert von 0.60 ergibt z.B. einen Teil-Score von <code>(0.60 - 0.2) / 0.8 = 0.50</code>.</li>"
            + "  <li><span style='color:#ff5252; font-weight:bold;'>Konsistenz &le; 0.2 (Teil-Score = 0.0):</span> Die Strategie bricht im Forward-Test massiv ein oder erzeugt sogar Verluste (Konsistenz &le; 0.0).</li>"
            + "</ul>"
            + "<h3>Konkrete Anwendungsbeispiele:</h3>"
            + "<table>"
            + "  <tr>"
            + "    <th>Szenario</th>"
            + "    <th>Backtest Profit</th>"
            + "    <th>Forward Profit</th>"
            + "    <th>Konsistenz (Ratio)</th>"
            + "    <th>Normierter Teil-Score</th>"
            + "    <th>Bedeutung</th>"
            + "  </tr>"
            + "  <tr>"
            + "    <td><b>A (Stabil)</b></td>"
            + "    <td>2.000 €</td>"
            + "    <td>1.600 €</td>"
            + "    <td>1.600 / 2.000 = <b>0.80</b></td>"
            + "    <td>(0.80-0.2)/0.8 = <b>0.75</b></td>"
            + "    <td><span style='color:#00e676;'>Geringer Rückgang. Sehr solide!</span></td>"
            + "  </tr>"
            + "  <tr>"
            + "    <td><b>B (Überoptimiert)</b></td>"
            + "    <td>4.000 €</td>"
            + "    <td>600 €</td>"
            + "    <td>600 / 4.000 = <b>0.15</b></td>"
            + "    <td>Unter Grenze 0.2 = <b>0.00</b></td>"
            + "    <td><span style='color:#ff5252;'>Achtung: Curve-Fitting! Bricht live ein.</span></td>"
            + "  </tr>"
            + "  <tr>"
            + "    <td><b>C (Ausnahmslos gut)</b></td>"
            + "    <td>1.500 €</td>"
            + "    <td>1.800 €</td>"
            + "    <td>1.800 / 1.500 = <b>1.20</b></td>"
            + "    <td>Gedeckelt bei 1.0 = <b>1.00</b></td>"
            + "    <td><span style='color:#00e676;'>Mehr Profit live als im Test! Perfekt.</span></td>"
            + "  </tr>"
            + "  <tr>"
            + "    <td><b>D (Verlustreich)</b></td>"
            + "    <td>2.500 €</td>"
            + "    <td>-300 €</td>"
            + "    <td>Unter Grenze 0.0 = <b>0.00</b></td>"
            + "    <td>Verlust live = <b>0.00</b></td>"
            + "    <td><span style='color:#ff5252;'>Unbrauchbar. Erzeugt Verluste.</span></td>"
            + "  </tr>"
            + "</table>"
            + "<h3>Wichtige Faustregel für Ihre Optimierung:</h3>"
            + "<p><b>Robustheit schlägt Maximalprofit!</b> Bevorzugen Sie im Zweifel eine Strategie mit einem moderaten Backtest-Profit (z. B. 1.500 €) und hoher Konsistenz (z. B. 0.85) gegenüber einer extrem profitablen Backtest-Strategie (z. B. 5.000 €) mit mangelhafter Konsistenz (z. B. 0.15). Letztere wird im Live-Handel mit hoher Wahrscheinlichkeit scheitern.</p>"
            + "</body></html>";
    }

    public static Button createThickCircularInfoButton(String tooltip, Runnable action) {
        Button infoBtn = new Button("ℹ");
        String normalStyle = "-fx-font-size: 13px; -fx-font-weight: 900; -fx-background-radius: 50%; -fx-min-width: 22px; -fx-min-height: 22px; -fx-max-width: 22px; -fx-max-height: 22px; -fx-text-fill: #ffd740; -fx-background-color: transparent; -fx-border-color: #ffd740; -fx-border-width: 2px; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0; -fx-alignment: center;";
        String hoverStyle = "-fx-font-size: 13px; -fx-font-weight: 900; -fx-background-radius: 50%; -fx-min-width: 22px; -fx-min-height: 22px; -fx-max-width: 22px; -fx-max-height: 22px; -fx-text-fill: #111111; -fx-background-color: #ffd740; -fx-border-color: #ffd740; -fx-border-width: 2px; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0; -fx-alignment: center;";

        infoBtn.setStyle(normalStyle);
        infoBtn.setOnAction(e -> action.run());
        infoBtn.setTooltip(new javafx.scene.control.Tooltip(tooltip));

        infoBtn.setOnMouseEntered(e -> infoBtn.setStyle(hoverStyle));
        infoBtn.setOnMouseExited(e -> infoBtn.setStyle(normalStyle));

        return infoBtn;
    }

    public static Button createThickCircularCyanInfoButton(String tooltip, Runnable action) {
        Button infoBtn = new Button("ℹ");
        String normalStyle = "-fx-font-size: 13px; -fx-font-weight: 900; -fx-background-radius: 50%; -fx-min-width: 22px; -fx-min-height: 22px; -fx-max-width: 22px; -fx-max-height: 22px; -fx-text-fill: #00e5ff; -fx-background-color: transparent; -fx-border-color: #00e5ff; -fx-border-width: 2px; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0; -fx-alignment: center;";
        String hoverStyle = "-fx-font-size: 13px; -fx-font-weight: 900; -fx-background-radius: 50%; -fx-min-width: 22px; -fx-min-height: 22px; -fx-max-width: 22px; -fx-max-height: 22px; -fx-text-fill: #111111; -fx-background-color: #00e5ff; -fx-border-color: #00e5ff; -fx-border-width: 2px; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0; -fx-alignment: center;";

        infoBtn.setStyle(normalStyle);
        infoBtn.setOnAction(e -> action.run());
        infoBtn.setTooltip(new javafx.scene.control.Tooltip(tooltip));

        infoBtn.setOnMouseEntered(e -> infoBtn.setStyle(hoverStyle));
        infoBtn.setOnMouseExited(e -> infoBtn.setStyle(normalStyle));

        return infoBtn;
    }

    public static void showAllIndicesDocDialog(javafx.stage.Window owner) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Erklärung der Stabilitäts- & Qualitäts-Indizes");
        stage.initModality(javafx.stage.Modality.NONE);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setMinWidth(1000);
        stage.setMinHeight(750);

        javafx.scene.layout.VBox mainBox = new javafx.scene.layout.VBox(15);
        mainBox.setPadding(new javafx.geometry.Insets(20));
        mainBox.setStyle("-fx-background-color: #0d0f17;");

        Label titleLabel = new Label("🛡️ Erklärung der Stabilitäts- & Qualitäts-Indizes");
        titleLabel.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 22));
        titleLabel.setTextFill(javafx.scene.paint.Color.web("#ffd740"));

        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        webView.setPrefSize(950, 600);
        webView.setStyle("-fx-background-color: #161821;");

        webView.getEngine().loadContent(getAllIndicesDocHtml());

        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().add("button");
        closeBtn.setOnAction(e -> stage.close());

        javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(closeBtn);
        btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        mainBox.getChildren().addAll(titleLabel, webView, btnRow);
        javafx.scene.layout.VBox.setVgrow(webView, javafx.scene.layout.Priority.ALWAYS);

        javafx.scene.Scene scene = new javafx.scene.Scene(mainBox, 1024, 768);
        try {
            java.net.URL css = DocHelper.class.getResource("/css/antigravity.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
        } catch (Exception ignored) {}

        stage.setScene(scene);
        stage.show();
    }

    public static String getAllIndicesDocHtml() {
        return "<html><head><style>"
            + "body { background-color:#161821; color:#c8cddc; font-family:\"Segoe UI\", sans-serif; font-size:16px; line-height:1.7; margin:20px; }"
            + "h2 { color:#ffd740; font-size:22px; margin-top:20px; border-bottom: 2px solid #3e4555; padding-bottom: 8px; font-weight: bold; }"
            + "h3 { color:#00e5ff; font-size:18px; margin-top:20px; border-bottom: 1px solid #3e4555; padding-bottom: 5px; font-weight: bold; }"
            + "h4 { color:#e2e8f0; font-size:16px; margin-top:15px; font-weight: bold; }"
            + "table { width:100%; border-collapse: collapse; margin: 15px 0; color:#c8cddc; font-size:15px; }"
            + "th { background-color: #1f2937; color: #00e5ff; font-weight: bold; padding: 10px; text-align: left; border: 1px solid #3e4555; }"
            + "td { padding: 10px; border: 1px solid #3e4555; }"
            + "tr:nth-child(even) { background-color: #1d202f; }"
            + "code { background-color:#1f2937; padding:4px 8px; border-radius:4px; color:#38bdf8; font-family:Consolas, monospace; font-size:14px; }"
            + "ul, ol { margin-left: 20px; padding-left: 0; }"
            + "li { margin-bottom: 8px; }"
            + ".warning-box { background-color:#1e293b; border-left:4px solid #ffd740; padding:12px; margin:15px 0; border-radius:4px; }"
            + ".info-box { background-color:#1e293b; border-left:4px solid #00e5ff; padding:12px; margin:15px 0; border-radius:4px; }"
            + "</style></head><body>"
            + "<h2>1. Die Erklärung der 5 Kennzahlen (Warum so viele?)</h2>"
            + "<p>Im algorithmischen Handel ist der <b>nackte Backtest-Profit leider die unzuverlässigste Zahl überhaupt</b>. Durch die Rechenleistung heutiger PCs neigt man dazu, Strategien perfekt an die Vergangenheit anzupassen (<b>Curve-Fitting</b>). Live verliert das System dann sofort Geld.</p>"
            + "<p>Deshalb beleuchten diese 5 Werte deine Strategien aus 5 völlig unabhängigen Blickwinkeln, um Glückstreffer von robusten Systemen zu trennen:</p>"
            + "<table>"
            + "  <thead>"
            + "    <tr>"
            + "      <th style='width: 15%;'>Spalte / Begriff</th>"
            + "      <th style='width: 15%;'>Name im Detail</th>"
            + "      <th style='width: 35%;'>Was misst dieser Wert?</th>"
            + "      <th style='width: 35%;'>Wie wird er berechnet?</th>"
            + "    </tr>"
            + "  </thead>"
            + "  <tbody>"
            + "    <tr>"
            + "      <td><b>Score</b></td>"
            + "      <td>Unified Score</td>"
            + "      <td><b>Performance &amp; Allround-Qualität</b><br>Bewertet die nackten Endergebnisse (Gewinn, Drawdown, Profit-Faktor) am Ende des Tests.</td>"
            + "      <td>Gewichteter Durchschnitt aus 10 Kriterien, den du im Regler-Symbol (Score-Gewichtung) einstellen kannst.</td>"
            + "    </tr>"
            + "    <tr>"
            + "      <td><b>Konsistenz</b></td>"
            + "      <td>Forward-Konsistenz</td>"
            + "      <td><b>Zukunftsträchtigkeit</b><br>Prüft, ob die Strategie auf unbekannten Live-Daten einbricht.</td>"
            + "      <td>Forward-Gewinn / Backtest-Gewinn. Ein Wert von 1.0 bedeutet: Live läuft es genauso gut wie im Test.</td>"
            + "    </tr>"
            + "    <tr>"
            + "      <td><b>Rob. Scorecard</b></td>"
            + "      <td>Robustheitsscore</td>"
            + "      <td><b>Robustheit aus echten Messdaten</b><br>Gewichtet Profitabilität (BT+FW), FW/BT-Konsistenz, Risiko, Sharpe Ratio, Stichprobe und Recovery.</td>"
            + "      <td>8 Säulen aus echten MT5-Kennzahlen — keine simulierten oder geschätzten Werte.</td>"
            + "    </tr>"
            + "    <tr>"
            + "      <td><b>KI</b></td>"
            + "      <td>KI-Stabilitätsscore</td>"
            + "      <td><b>Mustererkennung (AI)</b><br>Erkennt optische Risiken (wie Martingale, Grid-Risiken oder plötzliche Abstürze).</td>"
            + "      <td>Die künstliche Intelligenz (LLM) bewertet den Kurvenverlauf visuell auf einer Skala von 0-100.</td>"
            + "      </tr>"
            + "    <tr>"
            + "      <td><b>RI</b></td>"
            + "      <td>Robustness Index</td>"
            + "      <td><b>Mathematischer Baseline-Vergleich</b><br>Ein starrer, ungewichteter Index, bei dem es fast nie zu Gleichständen kommt.</td>"
            + "      <td>BT Recovery Factor * Trades-Gewichtung * Forward-Konsistenz. Dient als objektiver Tie-Breaker.</td>"
            + "    </tr>"
            + "  </tbody>"
            + "</table>"
            + "<h2>2. Score (Gesamt-Score)</h2>"
            + "<p>Der kombinierte Gesamt-Score (0–100) berechnet sich aus einem gewichteten Durchschnitt verschiedener Teilbewertungen des Backtests und Forward-Tests (Profit, Profit Factor, Drawdown-Abzüge, Tradeanzahl). Die genauen Gewichtungen können Sie im Tab unter 'Score-Gewichtung' anpassen.</p>"
            + "<div class=\"warning-box\">"
            + "  <strong>⚠️ WICHTIGER UNTERSCHIED:</strong> Der normale Gesamt-Score bewertet ausschließlich die endgültigen Kennzahlen am Ende des Testzeitraums. Er betrachtet <b>nicht</b> den genauen Verlauf der Equity-Kurve!"
            + "</div>"
            + "<h2>3. Konsistenz</h2>"
            + "<p>Das Verhältnis des Forward-Gewinns zum Backtest-Gewinn (<code>Forward-Gewinn / Backtest-Gewinn</code>). Ein Wert von 1.0 bedeutet, dass die Strategie im ungesehenen Forward-Zeitraum genauso profitabel war wie im optimierten Backtest. Werte unter 0.2 deuten auf massives Curve-Fitting hin.</p>"
            + "<h2>4. Rob. Scorecard (Robustheitsscore)</h2>"
            + "<p>Der Robustheitsscore (0–100) gewichtet 8 Säulen aus echten MT5-Messdaten: Profitabilität (BT+FW), FW/BT-Konsistenz, Risiko-Verhältnis (Return/DD, Calmar), Sharpe Ratio, Stichprobengröße (Trades + reale Testjahre), FW-Trade-Anzahl und Erholungsfaktor. Er enthält bewusst <b>keine</b> simulierten oder geschätzten Kennzahlen. Den tatsächlichen Kennlinien-Verlauf bewertet die Sensitivitätsanalyse (Schritt 4) zusammen mit der KI-Auswertung (Schritt 5); die finale Absicherung gegen Curve-Fitting liefert Schritt 7 (Out-of-Sample-Validierung auf unberührten Daten).</p>"
            + "<h2>5. KI Rating (AI Rating) &amp; Gewichtung</h2>"
            + "<p>Ein lokales Large Language Model (KI) analysiert die rohe Equity-Kurve auf Anomalien. Es sucht nach versteckten Risiken, plötzlichen Abstürzen oder Phasen unnatürlicher Gewinne, die auf fehlerhafte Logik (z.B. Martingale-Verhalten) hindeuten könnten, und vergibt eine Stabilitätsbewertung von 0 bis 100.</p>"
            + "<h3>Gewichtung im Gesamtwert (Weighted Final Score):</h3>"
            + "<p>Im finalen Portfolio (Schritt 6) wird das KI Rating gewichtet mit dem Unified Score zusammengeführt, um den endgültigen <b>Gesamtwert</b> einer Strategie zu ermitteln:</p>"
            + "<div style=\"background-color:#1f2937; padding:12px; border-radius:6px; color:#38bdf8; font-family:Consolas, monospace; font-size:14px; border: 1px solid #3e4555;\">"
            + "Gesamtwert = (Performance-Gewicht * Unified Score) + (KI-Stabilitäts-Gewicht * KI-Score)"
            + "</div>"
            + "<ul>"
            + "  <li><b>Standard-Verhältnis:</b> <code>0.6</code> (60% Performance / Unified Score) und <code>0.4</code> (40% KI-Stabilität).</li>"
            + "  <li><b>Konfiguration:</b> Das Gewichtungsverhältnis lässt sich im Hauptfenster über den Button <b>'KI-Einstellungen'</b> (im Optimierungs-Tab) or in <b>Schritt 5 des Workflows</b> anpassen.</li>"
            + "  <li><b>Fallback:</b> Liegt für eine Strategie noch kein KI-Stabilitätswert vor, wird als Gesamtwert automatisch der rohe Unified Score (100% Gewichtung) verwendet.</li>"
            + "</ul>"
            + "<h2>6. RI (Robustness-Index)</h2>"
            + "<p>Der Robustness-Index (RI) ist ein fixierter Standard-Wert, bei dem es fast nie zu Gleichständen kommt. Er ist wie folgt definiert:</p>"
            + "<div style=\"background-color:#1f2937; padding:12px; border-radius:6px; color:#38bdf8; font-family:Consolas, monospace; font-size:14px; border: 1px solid #3e4555;\">"
            + "RI = BT Recovery Factor * Trades-Gewichtung * Forward-Konsistenz"
            + "</div>"
            + "<p>Dabei ist der <i>BT Recovery Factor</i> das Verhältnis von Backtest-Nettoprofit zu Backtest-MaxDrawdown, die <i>Trades-Gewichtung</i> bestraft extrem trade-arme Strategien (unter dem Referenzwert von 80 Trades), und die <i>Forward-Konsistenz</i> stellt sicher, dass die Strategie im Out-of-Sample-Forward-Test nicht eingebrochen ist. Er dient als objektiver, ungewichteter Tie-Breaker bei der Strategieauswahl.</p>"
            + "</body></html>";
    }

    public static void showDiversityDocDialog(javafx.stage.Window owner) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Strategie-Auswahl & Diversität - Dokumentation");
        stage.initModality(javafx.stage.Modality.NONE);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setMinWidth(950);
        stage.setMinHeight(700);

        javafx.scene.layout.VBox mainBox = new javafx.scene.layout.VBox(15);
        mainBox.setPadding(new javafx.geometry.Insets(20));
        mainBox.setStyle("-fx-background-color: #0d0f17;");

        Label titleLabel = new Label("🧬 Strategie-Auswahl & Diversitäts-Filter");
        titleLabel.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 22));
        titleLabel.setTextFill(javafx.scene.paint.Color.web("#00e5ff"));

        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        webView.setPrefSize(900, 550);
        webView.setStyle("-fx-background-color: #161821;");

        webView.getEngine().loadContent(getDiversityDocHtml());

        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().add("button");
        closeBtn.setOnAction(e -> stage.close());

        javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(closeBtn);
        btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        mainBox.getChildren().addAll(titleLabel, webView, btnRow);
        javafx.scene.layout.VBox.setVgrow(webView, javafx.scene.layout.Priority.ALWAYS);

        javafx.scene.Scene scene = new javafx.scene.Scene(mainBox, 980, 720);
        try {
            java.net.URL css = DocHelper.class.getResource("/css/antigravity.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
        } catch (Exception ignored) {}

        stage.setScene(scene);
        stage.show();
    }

    public static String getDiversityDocHtml() {
        return "<html><head><style>"
            + "body { background-color:#161821; color:#c8cddc; font-family:\"Segoe UI\", sans-serif; font-size:16px; line-height:1.7; margin:20px; }"
            + "h3 { color:#00e5ff; font-size:20px; margin-top:20px; border-bottom: 1px solid #3e4555; padding-bottom: 5px; font-weight: bold; }"
            + "h4 { color:#e2e8f0; font-size:17px; margin-top:15px; font-weight: bold; }"
            + "code { background-color:#1f2937; padding:4px 8px; border-radius:4px; color:#38bdf8; font-family:Consolas, monospace; font-size:14px; }"
            + "ul, ol { margin-left: 20px; padding-left: 0; }"
            + "li { margin-bottom: 8px; }"
            + ".info-box { background-color:#1e293b; border-left:4px solid #00e5ff; padding:12px; margin:15px 0; border-radius:4px; }"
            + "</style></head><body>"
            + "<h3>Wie werden die Strategien im Diversitäts-Filter ausgewählt?</h3>"
            + "<p>Der Diversitäts-Filter wählt aus allen profitablen Durchgängen des Optimierers maximal 5 Strategien (oder eine frei wählbare Anzahl) aus, die sich in ihren Einstellungen und ihrem Verhalten möglichst stark voneinander unterscheiden. Dies schützt Ihr Portfolio vor Klumpenrisiken und Überoptimierung (Curve-Fitting).</p>"
            + "<h4>1. Mindestanforderungen (Pre-Filtering)</h4>"
            + "<p>Zuerst werden alle Durchgänge aussortiert, die vordefinierte Mindestanforderungen nicht erfüllen:</p>"
            + "<ul>"
            + "  <li><b>Mindestprofit</b> im Backtest & Forward-Zeitraum</li>"
            + "  <li><b>Mindestanzahl an Trades</b> im Backtest & Forward (Schutz vor Zufallstreffern)</li>"
            + "  <li><b>Maximaler Drawdown %</b> im Backtest & Forward-Zeitraum</li>"
            + "</ul>"
            + "<h4>2. Sortierung nach Score (Ranking)</h4>"
            + "<p>Die verbleibenden stabilen Strategien werden nach ihrem kombinierten Performance-Score absteigend sortiert. Die profitabelste und stabilste Strategie steht somit auf Platz 1 und wird automatisch als erste in das Portfolio aufgenommen.</p>"
            + "<h4>3. Gierige Diversitäts-Filterung (Greedy Selection)</h4>"
            + "<p>Der Algorithmus geht die sortierte Liste von oben nach unten durch. Für jeden Kandidaten wird geprüft, ob er zu ähnlich zu einer bereits ausgewählten Strategie ist. Ist er zu ähnlich, wird er übersprungen.</p>"
            + "<div class=\"info-box\">"
            + "  <strong>Ähnlichkeit (Similarity) wird anhand von zwei Kriterien gemessen:</strong>"
            + "  <ul>"
            + "    <li><b>Handelsverhalten (Trades-Abweichung):</b> Liegt der Unterschied der ausgeführten Trades unter dem eingestellten Prozentsatz (z. B. 15 % Trades-Differenz), gilt das Verhalten als ähnlich.</li>"
            + "    <li><b>Parameter-Struktur:</b> Die Abweichungen der Parameterwerte werden anhand des konfigurierten Suchraums (Bereich zwischen Start- und Endwert) normalisiert. Ist die durchschnittliche normalisierte Parameter-Abweichung geringer als der eingestellte Prozentsatz (z. B. 10 % Param-Differenz), gelten die Einstellungen als ähnlich.</li>"
            + "  </ul>"
            + "</div>"
            + "<p>Zwei Strategien gelten als <b>zu ähnlich (und der Kandidat fliegt raus)</b>, wenn ihr Handelsverhalten ähnlich ist <b>UND</b> die Anzahl der signifikant unterschiedlichen Parameter unter dem eingestellten Minimum liegt.</p>"
            + "</body></html>";
    }

    public static Button createControllingInfoButton(javafx.scene.Node ownerNode) {
        Button infoBtn = new Button("ℹ");
        String normalStyle = "-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-radius: 50%; -fx-min-width: 28px; -fx-min-height: 28px; -fx-text-fill: #00e5ff; -fx-background-color: transparent; -fx-border-color: #00e5ff; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0;";
        String hoverStyle = "-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-radius: 50%; -fx-min-width: 28px; -fx-min-height: 28px; -fx-text-fill: #111111; -fx-background-color: #00e5ff; -fx-border-color: #00e5ff; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0;";

        infoBtn.setStyle(normalStyle);
        infoBtn.setOnAction(e -> {
            javafx.stage.Window owner = null;
            if (ownerNode != null && ownerNode.getScene() != null) {
                owner = ownerNode.getScene().getWindow();
            }
            showControllingDocDialog(owner);
        });

        infoBtn.setOnMouseEntered(e -> infoBtn.setStyle(hoverStyle));
        infoBtn.setOnMouseExited(e -> infoBtn.setStyle(normalStyle));

        return infoBtn;
    }

    public static void showControllingDocDialog(javafx.stage.Window owner) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Controlling - Dokumentation");
        stage.initModality(javafx.stage.Modality.NONE);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setMinWidth(1000);
        stage.setMinHeight(750);

        javafx.scene.layout.VBox mainBox = new javafx.scene.layout.VBox(15);
        mainBox.setPadding(new javafx.geometry.Insets(20));
        mainBox.setStyle("-fx-background-color: #0d0f17;");

        Label titleLabel = new Label("📊 Strategie Controlling-System");
        titleLabel.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 22));
        titleLabel.setTextFill(javafx.scene.paint.Color.web("#00e5ff"));

        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        webView.setPrefSize(950, 600);
        webView.setStyle("-fx-background-color: #161821;");

        webView.getEngine().loadContent(getControllingDocHtml());

        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().add("button");
        closeBtn.setOnAction(e -> stage.close());

        javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(closeBtn);
        btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        mainBox.getChildren().addAll(titleLabel, webView, btnRow);
        javafx.scene.layout.VBox.setVgrow(webView, javafx.scene.layout.Priority.ALWAYS);

        javafx.scene.Scene scene = new javafx.scene.Scene(mainBox, 1024, 768);
        try {
            java.net.URL css = DocHelper.class.getResource("/css/antigravity.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
        } catch (Exception ignored) {}

        stage.setScene(scene);
        stage.show();
    }

    public static String getControllingDocHtml() {
        return "<html><head><style>"
            + "body { background-color:#161821; color:#c8cddc; font-family:\"Segoe UI\", sans-serif; font-size:16px; line-height:1.7; margin:20px; }"
            + "h2 { color:#ffd740; font-size:22px; margin-top:20px; border-bottom: 2px solid #3e4555; padding-bottom: 8px; font-weight: bold; }"
            + "h3 { color:#00e5ff; font-size:18px; margin-top:20px; border-bottom: 1px solid #3e4555; padding-bottom: 5px; font-weight: bold; }"
            + "h4 { color:#e2e8f0; font-size:16px; margin-top:15px; font-weight: bold; }"
            + "table { width:100%; border-collapse: collapse; margin: 15px 0; color:#c8cddc; font-size:15px; }"
            + "th { background-color: #1f2937; color: #00e5ff; font-weight: bold; padding: 10px; text-align: left; border: 1px solid #3e4555; }"
            + "td { padding: 10px; border: 1px solid #3e4555; }"
            + "tr:nth-child(even) { background-color: #1d202f; }"
            + "code { background-color:#1f2937; padding:4px 8px; border-radius:4px; color:#38bdf8; font-family:Consolas, monospace; font-size:14px; }"
            + "ul, ol { margin-left: 20px; padding-left: 0; }"
            + "li { margin-bottom: 8px; }"
            + ".warning-box { background-color:#1e293b; border-left:4px solid #ffd740; padding:12px; margin:15px 0; border-radius:4px; }"
            + ".info-box { background-color:#1e293b; border-left:4px solid #00e5ff; padding:12px; margin:15px 0; border-radius:4px; }"
            + "</style></head><body>"
            + "<h2>📊 Das Controlling-Modul im Detail</h2>"
            + "<p>Das Controlling-Modul dient als analytische Schnittstelle zur Überprüfung, Bewertung und Re-Verifikation optimierter Handelssysteme. "
            + "Hier werden alle Portfolios, Parameter-Setups und Strategie-Durchläufe aus abgeschlossenen Workflows zentral zusammengeführt, um sie vor dem Live-Einsatz auf Herz und Nieren zu prüfen.</p>"
            + "<h3>Warum ist dieses Modul so wichtig?</h3>"
            + "<p>Beim Optimieren von Handelssystemen besteht immer die Gefahr von <b>Curve-Fitting</b> (Überoptimierung an historische Daten). Das Controlling-System hilft Ihnen dabei:</p>"
            + "<ul>"
            + "  <li>Strategien über einen längeren, ungesehenen Zeitraum (Out-of-Sample) nachzutesten.</li>"
            + "  <li>Parameter-Stabilität und Leistungsabfall (Performance Decay) zu analysieren.</li>"
            + "  <li>Versteckte Risiken wie Martingale- oder Grid-Taktiken mithilfe von KI-Analysen aufzudecken.</li>"
            + "  <li>Die qualitativ besten Parameter-Sets (.set) gesammelt zu exportieren.</li>"
            + "</ul>"
            + "<div class=\"info-box\">"
            + "  <strong>💡 TIPP:</strong> Nutzen Sie den <i>Auto-Review</i> Button, um vollautomatisiert für jede gelistete Strategie einen 1-Jahres- und 2-Jahres-Nachtest im Hintergrund durchführen zu lassen."
            + "</div>"
            + "<h2>1. Benutzeroberfläche & Tabellen-Funktionen</h2>"
            + "<table>"
            + "  <thead>"
            + "    <tr>"
            + "      <th style='width: 25%;'>Element / Spalte</th>"
            + "      <th style='width: 75%;'>Beschreibung und Nutzen</th>"
            + "    </tr>"
            + "  </thead>"
            + "  <tbody>"
            + "    <tr>"
            + "      <td><b>Filter: Alle anzeigen</b></td>"
            + "      <td>Zeigt ausnahmslos alle jemals abgeschlossenen Workflow-Ergebnisse in der Tabelle an.</td>"
            + "    </tr>"
            + "    <tr>"
            + "      <td><b>Filter: Nur die Besten</b></td>"
            + "      <td>Filtert die Liste dynamisch. Es werden nur die qualifiziertesten Strategien angezeigt, die einen <b>KI-Score von mindestens 70</b> erreicht haben und am Ende des Workflows (Schritt 6) selektiert wurden.</td>"
            + "    </tr>"
            + "    <tr>"
            + "      <td><b>Suche & Datum</b></td>"
            + "      <td>Ermöglicht das schnelle Durchsuchen nach EA-Namen, Symbolen (z. B. EURUSD), Zeitrahmen oder dem Erstellungsdatum des Optimierungslaufs.</td>"
            + "    </tr>"
            + "    <tr>"
            + "      <td><b>KI Score (Stabilität)</b></td>"
            + "      <td>Der in Schritt 5 durch ein LLM ermittelte Wert (0-100). Er bewertet die Form der Equity-Kurve (Glocken- und Plateauformen vs. Peaks und Cliffs) und Beständigkeit der Parametervariationen.</td>"
            + "    </tr>"
            + "    <tr>"
            + "      <td><b>Review-Spalte</b></td>"
            + "      <td>Zeigt Ihre manuell verfassten Notizen an. Per Rechtsklick auf eine Tabellenzeile können Sie ein Review verfassen und eine Farbbewertung vergeben.</td>"
            + "    </tr>"
            + "  </tbody>"
            + "</table>"
            + "<h2>2. Die Farbbewertungen (Zeilenhintergrund)</h2>"
            + "<p>Um die Stabilität von Systemen schnell erfassbar zu machen, werden die Strategien farblich klassifiziert:</p>"
            + "<ul>"
            + "  <li><span style='color:#00e676; font-weight:bold;'>Dunkelgrün / Grün (Top/Good Choice):</span> Ausgezeichnete Stabilität. Hoher KI-Score (&ge; 70) und hervorragende Kennzahlen im Nachtest.</li>"
            + "  <li><span style='color:#ffd740; font-weight:bold;'>Gelb (Average Choice):</span> Akzeptable Ergebnisse, aber erhöhte Wachsamkeit geboten (z. B. leichter Leistungsabfall).</li>"
            + "  <li><span style='color:#ff9100; font-weight:bold;'>Orange / Rot (Weak/Poor Choice):</span> Instabil oder Curve-Fitted. Hohe Drawdowns, zu geringe Tradeanzahl oder Verluste im Out-of-Sample Test.</li>"
            + "</ul>"
            + "<h2>3. Automatisches Review (Auto-Review)</h2>"
            + "<p>Klicken Sie auf <b>'Automatisches Review'</b>, um für alle ausgewählten Strategien vollautomatische Nachtests zu starten:</p>"
            + "<ul>"
            + "  <li><b>1-Jahres Nachtest:</b> Simuliert das letzte Jahr ab dem heutigen Datum auf 'Every Tick' Basis mit realem Spread.</li>"
            + "  <li><b>2-Jahres Nachtest:</b> Dehnt den Testzeitraum auf die letzten zwei Jahre aus.</li>"
            + "  <li><b>Kennzahlen-Vergleich:</b> Im Reiter <i>'Auto-Review'</i> auf der rechten Seite sehen Sie den direkten Vergleich aller Performance-Metriken (Gewinn, Drawdown, Profit-Faktor, Sharpe Ratio, Recovery-Faktor) zwischen der Originaloptimierung und den Nachtests.</li>"
            + "</ul>"
            + "<div class=\"warning-box\">"
            + "  <strong>⚠️ WICHTIGER HINWEIS ZU METATRADER:</strong> Das automatische Review startet MT5-Instanzen im Hintergrund. Währenddessen darf kein aktiver Workflow laufen, da MetaTrader nur einen Port-Zugriff gleichzeitig erlaubt."
            + "</div>"
            + "<h2>4. Manueller Nachtest & Preset-Export</h2>"
            + "<p>In der Detailansicht rechts können Sie folgende Aktionen ausführen:</p>"
            + "<ul>"
            + "  <li><b>Einzeltest starten:</b> Führt einen direkten, visuellen Test der ausgewählten Strategie im MetaTrader aus (entweder 'Every Tick' oder '1 minute OHLC').</li>"
            + "  <li><b>Settings exportieren:</b> Exportiert das Parameter-Preset (.set-Datei) der Strategie direkt in den konfigurierten Export-Ordner.</li>"
            + "  <li><b>Export Prompt:</b> Kopieren Sie den fertigen System-Prompt mit dem ⓘ-Symbol, um der KI den Befehl zu geben, eine automatisierte Datenbankanalyse durchzuführen, die besten Setups zu ermitteln und einen professionellen PDF-Report inkl. Equity-Kurven zu erstellen.</li>"
            + "</ul>"
            + "</body></html>";
    }
}
