package com.backtester.ui.javafx;

import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Controlling module documentation HTML, dialog, and info button.
 */
public final class ControllingDocs {
    private ControllingDocs() {}

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
