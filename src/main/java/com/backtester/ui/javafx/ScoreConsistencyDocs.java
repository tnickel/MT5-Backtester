package com.backtester.ui.javafx;

import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Score and consistency documentation HTML and dialogs.
 */
public final class ScoreConsistencyDocs {
    private ScoreConsistencyDocs() {}

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
}
