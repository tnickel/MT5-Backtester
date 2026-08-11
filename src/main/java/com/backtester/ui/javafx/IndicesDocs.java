package com.backtester.ui.javafx;

import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Stability and quality indices documentation HTML and dialog.
 */
public final class IndicesDocs {
    private IndicesDocs() {}

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
}
