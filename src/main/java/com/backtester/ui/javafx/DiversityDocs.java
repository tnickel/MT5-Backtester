package com.backtester.ui.javafx;

import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Diversity filter and custom-project clustering documentation.
 */
public final class DiversityDocs {
    private DiversityDocs() {}

    public static void showCustomProjectDiversityDocDialog(javafx.stage.Window owner) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Diversitäts-Clustering – ausführliche Erklärung");
        stage.initModality(javafx.stage.Modality.NONE);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setMinWidth(980);
        stage.setMinHeight(760);

        javafx.scene.layout.VBox mainBox = new javafx.scene.layout.VBox(15);
        mainBox.setPadding(new javafx.geometry.Insets(20));
        mainBox.setStyle("-fx-background-color: #0d0f17;");

        Label titleLabel = new Label("Diversitäts-Clustering im Custom Project");
        titleLabel.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 22));
        titleLabel.setTextFill(javafx.scene.paint.Color.web("#00e5ff"));

        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        webView.setPrefSize(930, 610);
        webView.setStyle("-fx-background-color: #161821;");
        webView.getEngine().loadContent(getCustomProjectDiversityDocHtml());

        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().add("button");
        closeBtn.setOnAction(e -> stage.close());

        javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(closeBtn);
        btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        mainBox.getChildren().addAll(titleLabel, webView, btnRow);
        javafx.scene.layout.VBox.setVgrow(webView, javafx.scene.layout.Priority.ALWAYS);

        javafx.scene.Scene scene = new javafx.scene.Scene(mainBox, 1020, 800);
        try {
            java.net.URL css = DocHelper.class.getResource("/css/antigravity.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
        } catch (Exception ignored) {}

        stage.setScene(scene);
        stage.show();
    }

    public static String getCustomProjectDiversityDocHtml() {
        return "<html><head><style>"
            + "body { background-color:#161821; color:#c8cddc; font-family:'Segoe UI',sans-serif; font-size:16px; line-height:1.65; margin:22px; }"
            + "h2 { color:#00e5ff; font-size:22px; border-bottom:1px solid #3e4555; padding-bottom:6px; }"
            + "h3 { color:#ffd740; font-size:18px; margin-top:24px; }"
            + "code { background:#1f2937; color:#38bdf8; padding:3px 7px; border-radius:4px; font-family:Consolas,monospace; }"
            + "li { margin-bottom:8px; }"
            + ".box { background:#1e293b; border-left:4px solid #00e5ff; padding:12px 15px; margin:14px 0; border-radius:4px; }"
            + ".warn { background:#2b2615; border-left-color:#ffd740; }"
            + "table { width:100%; border-collapse:collapse; margin:12px 0; }"
            + "th,td { border:1px solid #3e4555; padding:9px; text-align:left; vertical-align:top; }"
            + "th { color:#00e5ff; background:#1e2432; }"
            + "</style></head><body>"
            + "<h2>Ziel der Diversitätsfilterung</h2>"
            + "<p>Das Clustering reduziert eine bereits gerankte Databank auf Strategien, die sich in Parametern oder Handelsaktivität ausreichend unterscheiden. So werden nahezu identische Varianten derselben Strategie nicht mehrfach in den nächsten Workflow-Schritt übernommen.</p>"
            + "<div class='box'><b>Wichtig:</b> Dieser Custom-Project-Task prüft genau eine Quell-Databank. Er führt weder einen Retest noch einen Profit-, Drawdown-, Recovery- oder Forward-/Langzeit-Qualitätsfilter aus. Solche Prüfungen gehören in eigene Workflow-Tasks.</div>"
            + "<h3>1. Reihenfolge und Priorität</h3>"
            + "<ol>"
            + "<li>Ungültige bzw. leere Zeilen werden übersprungen.</li>"
            + "<li>Mit aktivierter Score-Sortierung werden nur Zeilen mit endlichem Score nach Score absteigend geordnet; Gleichstände löst die kleinere MT5-Passnummer reproduzierbar auf.</li>"
            + "<li>Ohne Score-Sortierung bleibt die Reihenfolge der Quell-Databank unverändert.</li>"
            + "<li>Die jeweils nächste Strategie wird mit allen bereits übernommenen Strategien verglichen. Ähnliche Kandidaten werden übersprungen, bis das Maximum erreicht oder die Quelle erschöpft ist.</li>"
            + "</ol>"
            + "<h3>2. Wann gelten zwei Strategien als ähnlich?</h3>"
            + "<p>Ein neuer Kandidat gilt nur dann als <b>zu ähnlich</b>, wenn beide Bedingungen gleichzeitig erfüllt sind:</p>"
            + "<div class='box'><code>Trades sind ähnlich</code> <b>UND</b> <code>Anzahl deutlich verschiedener Parameter &lt; Mindestzahl</code></div>"
            + "<p>Ist dagegen der Trade-Abstand groß genug <b>oder</b> unterscheiden sich genügend Parameter, gilt der Kandidat als divers und darf aufgenommen werden.</p>"
            + "<h3>3. Bedeutung der Einstellungen</h3>"
            + "<table><tr><th>Einstellung</th><th>Wirkung</th></tr>"
            + "<tr><td><b>Parameter-Differenz %</b></td><td>Schwelle, ab der ein einzelner Parameter als deutlich verschieden zählt. Mit hinterlegtem EA-Suchraum wird der Abstand auf Start-/Endwert des optimierten Parameters normiert. Ohne Suchraum wird die relative Abweichung der beiden Werte verwendet. Unterschiedliche nichtnumerische Werte zählen als verschieden.</td></tr>"
            + "<tr><td><b>Trade-Differenz %</b></td><td>Schwelle für unterschiedliches Handelsverhalten. Liegt die relative Abweichung der Trade-Anzahl unter diesem Wert, gelten die Trades als ähnlich. Ab der Schwelle gelten sie als verschieden.</td></tr>"
            + "<tr><td><b>Min. differente Parameter</b></td><td>So viele Parameter müssen mindestens die Parameter-Differenz-Schwelle erreichen, damit nahe Trade-Zahlen den Kandidaten nicht als Duplikat aussortieren.</td></tr>"
            + "<tr><td><b>Max. Strategien</b></td><td>Obergrenze der Ausgabemenge. Sobald sie erreicht ist, endet die Prüfung.</td></tr></table>"
            + "<p><b>Nach Score sortieren:</b> Aktiviert eine deterministische Top-N-Priorität vor der Diversitätsprüfung. Verwendet wird der in der Databank gespeicherte Score; bestehende Zeilen werden beim Umschalten nicht neu bewertet.</p>"
            + "<h3>4. Welche Trade-Zahl wird verwendet?</h3>"
            + "<p>Enthält jede Zeile der gewählten Databank ein Retester-Ergebnis, vergleicht das Clustering die Trade-Zahlen dieses Retests. Andernfalls verwendet es die normalen Backtest-Trade-Zahlen. Es mischt nicht zeilenweise zwischen beiden Quellen.</p>"
            + "<h3>5. Beispiel mit den Standardwerten</h3>"
            + "<p>Bei <code>10 % Parameter-Differenz</code>, <code>15 % Trade-Differenz</code> und <code>2 Mindestparametern</code> gilt:</p>"
            + "<ul>"
            + "<li>8 % Trade-Abstand und nur 1 deutlich anderer Parameter: <b>zu ähnlich – wird übersprungen.</b></li>"
            + "<li>20 % Trade-Abstand bei identischen Parametern: <b>divers – kann aufgenommen werden.</b></li>"
            + "<li>5 % Trade-Abstand, aber mindestens 2 deutlich andere Parameter: <b>divers – kann aufgenommen werden.</b></li>"
            + "</ul>"
            + "<h3>6. Kurzzeit- und Langzeit-Clustering</h3>"
            + "<p>Für getrennte Auswertungen werden zwei Tasks verwendet: Der erste clustert beispielsweise <code>Results</code>. Ein Retester schreibt anschließend in eine eigene Databank, etwa <code>Langzeit-Retest</code>. Ein zweiter Clustering-Task liest genau diese Retester-Databank und schreibt sein Ergebnis in eine weitere Ziel-Databank.</p>"
            + "<div class='box warn'><b>Faustregel:</b> Erst filtern und ranken, danach clustern. Niedrigere Schwellen bzw. eine kleinere Mindestzahl lassen mehr Strategien als verschieden gelten. Höhere Schwellen bzw. eine größere Mindestzahl entfernen aggressiver ähnliche Varianten.</div>"
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
}
