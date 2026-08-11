package com.backtester.ui.javafx;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * KI analysis UI extracted from OptimizationView.
 */
public final class OptimizationKiPanel {

    private static final Logger log = LoggerFactory.getLogger(OptimizationKiPanel.class);

    public interface Host {
        LogView logView();
        javafx.stage.Window ownerWindow();
        List<String> stylesheets();
        TableView<com.backtester.report.SensitivityResult> sensitivityTable();
        TableView<com.backtester.report.OptimizationResult.CombinedPass> combinedTable();
        TableView<com.backtester.report.OptimizationResult.CombinedPass> selectedTable();
        TabPane resultTabs();
        Tab kiAnalysisTab();
        void saveStateToDb();
        String expertName();
        String symbol();
        String period();
        Button llmAnalyzeBtn();
    }

    private final Host host;
    private TableView<com.backtester.engine.KiReport> kiReportsTable;

    public OptimizationKiPanel(Host host) {
        this.host = host;
    }

    public TableView<com.backtester.engine.KiReport> getReportsTable() {
        return kiReportsTable;
    }

    public void updateAnalyzeButtonState(boolean enabled) {
        Button llmAnalyzeBtn = host.llmAnalyzeBtn();
        if (llmAnalyzeBtn != null) {
            llmAnalyzeBtn.setDisable(!enabled);
            if (enabled) {
                llmAnalyzeBtn.setStyle("-fx-background-color: linear-gradient(to right, #10b981, #059669); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-cursor: hand;");
            } else {
                llmAnalyzeBtn.setStyle("-fx-background-color: #2e3543; -fx-text-fill: #6b7280; -fx-font-weight: bold; -fx-font-size: 13px; -fx-border-color: #3e4555; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-cursor: default;");
            }
        }
    }

    public void runAnalysis(Button analyzeBtn) {
        if (host.sensitivityTable().getItems().isEmpty()) {
            host.logView().log("WARN", "Keine Sensitivitätsdaten vorhanden. Bitte führe zuerst eine Analyse durch.");
            return;
        }

        analyzeBtn.setDisable(true);

        // Zeige Lade-Dialog
        javafx.stage.Stage loadingStage = new javafx.stage.Stage();
        loadingStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        loadingStage.initStyle(javafx.stage.StageStyle.UNDECORATED);

        VBox loadBox = new VBox(15);
        loadBox.setAlignment(Pos.CENTER);
        loadBox.setStyle("-fx-background-color: #1a1d27; -fx-padding: 30; -fx-border-color: #7c3aed; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8;");

        javafx.scene.control.ProgressIndicator spinner = new javafx.scene.control.ProgressIndicator();
        spinner.setStyle("-fx-progress-color: #a78bfa;");

        Label waitLabel = new Label("KI analysiert Strategien...");
        waitLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        waitLabel.setTextFill(Color.web("#e2e8f0"));

        Label subLabel = new Label("Bitte warten, das Modell verarbeitet die Daten.");
        subLabel.setTextFill(Color.web("#94a3b8"));

        loadBox.getChildren().addAll(spinner, waitLabel, subLabel);

        javafx.scene.Scene loadScene = new javafx.scene.Scene(loadBox);
        loadScene.setFill(Color.TRANSPARENT);
        loadingStage.setScene(loadScene);

        javafx.stage.Window owner = host.ownerWindow();
        if (owner != null) {
            loadingStage.initOwner(owner);
        }
        loadingStage.show();

        List<Integer> activePasses = new java.util.ArrayList<>();
        for (com.backtester.report.SensitivityResult item : host.sensitivityTable().getItems()) {
            if (item != null && item.getOriginalPass() != null) {
                activePasses.add(item.getOriginalPass().getPassNumber());
            }
        }
        String exp = host.expertName();
        String sym = host.symbol();
        String per = host.period();

        // Build performance data map from sensitivity table's CombinedPass objects
        java.util.Map<Integer, com.backtester.engine.LlmAnalysisService.PassPerformance> performanceData = new java.util.HashMap<>();
        for (com.backtester.report.SensitivityResult item : host.sensitivityTable().getItems()) {
            if (item != null && item.getOriginalPass() != null) {
                try {
                    performanceData.put(item.getOriginalPass().getPassNumber(),
                            new com.backtester.engine.LlmAnalysisService.PassPerformance(item.getOriginalPass()));
                } catch (Exception ex) {
                    log.warn("[KI] Failed to extract performance for pass {}: {}",
                            item.getOriginalPass().getPassNumber(), ex.getMessage());
                }
            }
        }
        log.info("[KI] Built performanceData map with {} entries for {} passes", performanceData.size(), activePasses.size());

        new Thread(() -> {
            try {
                com.backtester.engine.LlmAnalysisService llmService = new com.backtester.engine.LlmAnalysisService();
                String response = llmService.analyzeStrategies(activePasses, exp, sym, performanceData);

                javafx.application.Platform.runLater(() -> {
                    loadingStage.close();
                    analyzeBtn.setDisable(false);
                    // Save the report to DB so it persists across restarts
                    long ts = System.currentTimeMillis();
                    com.backtester.database.DatabaseManager.getInstance().saveKiReport(ts, exp, sym, per, response);

                    // Log response length
                    int charCount = response.length();
                    int estimatedTokens = charCount / 4; // rough estimate: ~4 chars per token
                    host.logView().log("INFO", String.format("KI-Antwort: %d Zeichen (~%d Tokens)", charCount, estimatedTokens));

                    // Parse LLM response using Regex to extract STABILITY_SCORE lines for the Sensitivity Table
                    try {
                        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("STABILITY_SCORE\\|(\\d+)\\|(\\d+)");
                        java.util.regex.Matcher matcher = pattern.matcher(response);
                        int matchCount = 0;
                        int tableSize = host.sensitivityTable().getItems().size();
                        // Log all pass numbers currently in the table for cross-reference
                        StringBuilder passNums = new StringBuilder();
                        for (com.backtester.report.SensitivityResult item : host.sensitivityTable().getItems()) {
                            passNums.append(item.getOriginalPass().getPassNumber()).append(", ");
                        }
                        log.info("[KI-Parse] Sensitivity-Tabelle: {} Eintraege. Pass-Nummern: {}", tableSize, passNums);
                        log.info("[KI-Parse] Response erste 300 Zeichen: {}", response.substring(0, Math.min(300, response.length())));
                        while (matcher.find()) {
                            matchCount++;
                            try {
                                int passNum = Integer.parseInt(matcher.group(1));
                                int score = Math.max(0, Math.min(100, Integer.parseInt(matcher.group(2))));
                                boolean found = false;
                                for (com.backtester.report.SensitivityResult item : host.sensitivityTable().getItems()) {
                                    if (item.getOriginalPass().getPassNumber() == passNum) {
                                        item.setKiResult(String.valueOf(score));
                                        found = true;
                                    }
                                }
                                log.info("[KI-Parse] STABILITY_SCORE Pass={} Score={} -> {}", passNum, score, found ? "GESETZT" : "KEIN Match!");
                            } catch (Exception ignored) {}
                        }
                        log.info("[KI-Parse] Abgeschlossen: {} Scores gefunden, {} Tabellenzeilen.", matchCount, tableSize);
                        host.sensitivityTable().refresh();
                        if (host.combinedTable() != null) host.combinedTable().refresh();
                        if (host.selectedTable() != null) host.selectedTable().refresh();
                    } catch (Exception parseEx) {
                        host.logView().log("WARN", "Konnte KI-Resultate nicht für die Tabelle parsen: " + parseEx.getMessage());
                    }

                    // Persist KI scores into the saved state so they survive restarts
                    host.saveStateToDb();

                    // Refresh KI history table and switch to KI Analysis tab
                    refreshReports();
                    host.resultTabs().getSelectionModel().select(host.kiAnalysisTab());
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    loadingStage.close();
                    analyzeBtn.setDisable(false);
                    host.logView().log("ERROR", "LLM Analyse fehlgeschlagen: " + e.getMessage());
                });
            }
        }).start();
    }

    public VBox createPane() {
        VBox box = new VBox(10);
        box.setStyle("-fx-background-color: #0b0d13;");

        kiReportsTable = new TableView<>();
        kiReportsTable.getStyleClass().add("table-view");
        VBox.setVgrow(kiReportsTable, Priority.ALWAYS);

        javafx.scene.control.TableColumn<com.backtester.engine.KiReport, String> dateCol = new javafx.scene.control.TableColumn<>("Datum");
        dateCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getCreatedAt()));
        dateCol.setPrefWidth(150);

        javafx.scene.control.TableColumn<com.backtester.engine.KiReport, String> expertCol = new javafx.scene.control.TableColumn<>("Expert");
        expertCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getExpertName()));
        expertCol.setPrefWidth(200);

        javafx.scene.control.TableColumn<com.backtester.engine.KiReport, String> symbolCol = new javafx.scene.control.TableColumn<>("Symbol");
        symbolCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getSymbol()));
        symbolCol.setPrefWidth(100);

        javafx.scene.control.TableColumn<com.backtester.engine.KiReport, String> periodCol = new javafx.scene.control.TableColumn<>("Periode");
        periodCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getPeriod()));
        periodCol.setPrefWidth(100);

        kiReportsTable.getColumns().addAll(dateCol, expertCol, symbolCol, periodCol);

        box.getChildren().add(kiReportsTable);

        // Listener to open report in a new window when a row is clicked
        kiReportsTable.setOnMouseClicked(event -> {
            if (kiReportsTable.getSelectionModel().getSelectedItem() == null) return;

            // Only perform action on double click to prevent popup from stealing focus on single click
            if (event.getClickCount() == 2) {
                com.backtester.engine.KiReport selected = kiReportsTable.getSelectionModel().getSelectedItem();
                try {
                    // Parse LLM response using Regex to extract STABILITY_SCORE lines
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("STABILITY_SCORE\\|(\\d+)\\|(\\d+)");
                    java.util.regex.Matcher matcher = pattern.matcher(selected.getReportMarkdown());
                    int matchCount = 0;

                    // First clear old KI scores
                    for (com.backtester.report.SensitivityResult item : host.sensitivityTable().getItems()) {
                        item.setKiResult("");
                    }

                    while (matcher.find()) {
                        matchCount++;
                        try {
                            int passNum = Integer.parseInt(matcher.group(1));
                            int score = Math.max(0, Math.min(100, Integer.parseInt(matcher.group(2))));
                            for (com.backtester.report.SensitivityResult item : host.sensitivityTable().getItems()) {
                                if (item.getOriginalPass().getPassNumber() == passNum) {
                                    item.setKiResult(String.valueOf(score));
                                }
                            }
                        } catch (Exception e) {}
                    }

                    host.sensitivityTable().refresh();
                    if (host.combinedTable() != null) host.combinedTable().refresh();
                    if (host.selectedTable() != null) host.selectedTable().refresh();
                    host.logView().log("INFO", "Geparste KI-Werte aus Historie: " + matchCount + " Einträge gefunden.");

                    // Save state so it survives restart
                    host.saveStateToDb();
                } catch (Exception parseEx) {
                    host.logView().log("WARN", "Konnte KI-Resultate aus Historie nicht parsen: " + parseEx.getMessage());
                }

                // Show the report window
                showKiReportWindow(selected);
            }
        });

        refreshReports();

        return box;
    }

    public void refreshReports() {
        java.util.List<com.backtester.engine.KiReport> reports = com.backtester.database.DatabaseManager.getInstance().getAllKiReports();
        kiReportsTable.getItems().setAll(reports);
    }

    private void showKiReportWindow(com.backtester.engine.KiReport report) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("\uD83E\uDD16 KI Strategie-Analyse: " + report.getExpertName() + " | " + report.getSymbol());
        stage.initModality(javafx.stage.Modality.NONE);

        // Convert Markdown to HTML using commonmark
        org.commonmark.parser.Parser parser = org.commonmark.parser.Parser.builder().extensions(java.util.Collections.singletonList(org.commonmark.ext.gfm.tables.TablesExtension.create())).build();
        org.commonmark.node.Node document = parser.parse(report.getReportMarkdown());
        org.commonmark.renderer.html.HtmlRenderer renderer = org.commonmark.renderer.html.HtmlRenderer.builder()
                .extensions(java.util.Collections.singletonList(org.commonmark.ext.gfm.tables.TablesExtension.create()))
                .escapeHtml(true)
                .softbreak("<br />")
                .build();
        String htmlBody = renderer.render(document);

        // Wrap in styled HTML
        String fullHtml = "<!DOCTYPE html><html><head><style>" +
                "body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #0b0d13; color: #e2e8f0; line-height: 1.6; padding: 20px; }" +
                "h1, h2, h3 { color: #a78bfa; margin-top: 1.5em; }" +
                "h1 { font-size: 24px; border-bottom: 1px solid #2a2d3a; padding-bottom: 10px; }" +
                "h2 { font-size: 20px; color: #60a5fa; }" +
                "ul, ol { padding-left: 20px; }" +
                "li { margin-bottom: 5px; }" +
                "code { background-color: #1a1d27; padding: 2px 5px; border-radius: 3px; font-family: 'Consolas', monospace; }" +
                "strong { color: #f8fafc; font-weight: 600; }" +
                "blockquote { border-left: 4px solid #3b82f6; padding: 10px 15px; margin-left: 0; color: #94a3b8; background-color: #131620; border-radius: 0 4px 4px 0; font-style: italic; }" +
                "table { width: 100%; border-collapse: collapse; margin-top: 15px; margin-bottom: 15px; background-color: #131620; border-radius: 8px; overflow: hidden; }" +
                "th { background-color: #1e293b; color: #e2e8f0; padding: 12px 15px; text-align: left; font-weight: 600; border-bottom: 2px solid #334155; }" +
                "td { padding: 12px 15px; border-bottom: 1px solid #1e293b; color: #cbd5e1; }" +
                "tr:last-child td { border-bottom: none; }" +
                "tr:nth-child(even) { background-color: rgba(255, 255, 255, 0.02); }" +
                "</style></head><body>" + htmlBody + "</body></html>";

        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        webView.setContextMenuEnabled(false);
        webView.getEngine().loadContent(fullHtml);

        javafx.scene.Scene scene = new javafx.scene.Scene(webView, 900, 700);
        stage.setScene(scene);
        stage.show();
    }

    public void showSettings() {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("\u2699 KI-Einstellungen");
        stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
        javafx.stage.Window owner = host.ownerWindow();
        if (owner != null) {
            stage.initOwner(owner);
        }

        VBox box = new VBox(18);
        box.setStyle("-fx-background-color: #0b0d13; -fx-padding: 30;");

        Label titleLabel = new Label("OpenRouter API Konfiguration");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 23));
        titleLabel.setTextFill(Color.web("#a78bfa"));

        // API Key
        Label keyLabel = new Label("API Key:");
        keyLabel.setTextFill(Color.web("#c8cddc"));
        keyLabel.setFont(Font.font("Segoe UI", 16));
        javafx.scene.control.PasswordField keyField = new javafx.scene.control.PasswordField();
        keyField.setStyle("-fx-control-inner-background: #1a1d27; -fx-text-fill: white; -fx-prompt-text-fill: #6b7280; -fx-font-size: 15px;");
        keyField.setPromptText("sk-or-v1-...");

        com.backtester.database.DatabaseManager db = com.backtester.database.DatabaseManager.getInstance();
        String savedKey = db.getSetting(com.backtester.engine.LlmAnalysisService.SETTING_API_KEY);
        if (savedKey != null) keyField.setText(savedKey);

        // Model
        Label modelLabel = new Label("Modell:");
        modelLabel.setTextFill(Color.web("#c8cddc"));
        modelLabel.setFont(Font.font("Segoe UI", 16));
        javafx.scene.control.ComboBox<String> modelCombo = new javafx.scene.control.ComboBox<>();
        modelCombo.getItems().addAll(
            "openai/gpt-4o-mini  (max 16k output)",
            "moonshotai/kimi-k2.6  (max 64k output)",
            "anthropic/claude-3-haiku  (max 4k output)",
            "google/gemini-2.5-flash  (max 65k output)",
            "google/gemini-3-flash-preview  (max 65k output)"
        );
        modelCombo.setEditable(true);
        modelCombo.setStyle("-fx-control-inner-background: #1a1d27; -fx-text-fill: white; -fx-font-size: 15px;");
        String savedModel = db.getSetting(com.backtester.engine.LlmAnalysisService.SETTING_MODEL, com.backtester.engine.LlmAnalysisService.DEFAULT_MODEL);
        if (savedModel != null && (savedModel.contains("1.5-flash") || savedModel.contains("flash-1.5"))) {
            savedModel = "google/gemini-2.5-flash";
        }
        // Match saved model ID to display entry
        String matchedEntry = savedModel;
        if (savedModel != null) {
            for (String entry : modelCombo.getItems()) {
                if (entry.startsWith(savedModel)) {
                    matchedEntry = entry;
                    break;
                }
            }
        }
        modelCombo.setValue(matchedEntry);

        Label hintLabel = new Label("Standard: openai/gpt-4o-mini — eigene Modell-IDs sind auch möglich");
        hintLabel.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 14px;");

        // Max Tokens
        Label maxTokensLabel = new Label("Max Tokens:");
        maxTokensLabel.setTextFill(Color.web("#c8cddc"));
        maxTokensLabel.setFont(Font.font("Segoe UI", 16));
        javafx.scene.control.TextField maxTokensField = new javafx.scene.control.TextField();
        maxTokensField.setStyle("-fx-control-inner-background: #1a1d27; -fx-text-fill: white; -fx-font-size: 15px;");
        maxTokensField.setPromptText(String.valueOf(com.backtester.engine.LlmAnalysisService.DEFAULT_MAX_TOKENS));
        String savedMaxTokens = db.getSetting(com.backtester.engine.LlmAnalysisService.SETTING_MAX_TOKENS);
        maxTokensField.setText(savedMaxTokens != null && !savedMaxTokens.isBlank() ? savedMaxTokens : String.valueOf(com.backtester.engine.LlmAnalysisService.DEFAULT_MAX_TOKENS));
        Label maxTokensHint = new Label("Standard: 16384 — Erhöhen bei abgeschnittenen Antworten");
        maxTokensHint.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 14px;");

        // Prompt
        Label promptLabel = new Label("System Prompt:");
        promptLabel.setTextFill(Color.web("#c8cddc"));
        promptLabel.setFont(Font.font("Segoe UI", 16));
        javafx.scene.control.TextArea promptField = new javafx.scene.control.TextArea();
        promptField.setStyle("-fx-control-inner-background: #1a1d27; -fx-text-fill: white; -fx-font-family: 'Consolas'; -fx-font-size: 15px;");
        promptField.setWrapText(true);
        promptField.setPrefRowCount(14);
        VBox.setVgrow(promptField, Priority.ALWAYS);
        String savedPrompt = db.getSetting(com.backtester.engine.LlmAnalysisService.SETTING_PROMPT, com.backtester.engine.LlmAnalysisService.DEFAULT_PROMPT);
        promptField.setText(savedPrompt);

        // Performance & Stability Weights
        Label perfWeightLabel = new Label("Gewichtung Performance (0.0 - 1.0):");
        perfWeightLabel.setTextFill(Color.web("#c8cddc"));
        perfWeightLabel.setFont(Font.font("Segoe UI", 16));
        javafx.scene.control.TextField perfWeightField = new javafx.scene.control.TextField();
        perfWeightField.setStyle("-fx-control-inner-background: #1a1d27; -fx-text-fill: white; -fx-font-size: 15px;");
        String savedPerfW = db.getSetting(com.backtester.engine.LlmAnalysisService.SETTING_PERFORMANCE_WEIGHT);
        perfWeightField.setText(savedPerfW != null && !savedPerfW.isBlank() ? savedPerfW : String.valueOf(com.backtester.engine.LlmAnalysisService.DEFAULT_PERFORMANCE_WEIGHT));

        Label stabWeightLabel = new Label("Gewichtung Stabilität (0.0 - 1.0):");
        stabWeightLabel.setTextFill(Color.web("#c8cddc"));
        stabWeightLabel.setFont(Font.font("Segoe UI", 16));
        javafx.scene.control.TextField stabWeightField = new javafx.scene.control.TextField();
        stabWeightField.setStyle("-fx-control-inner-background: #1a1d27; -fx-text-fill: white; -fx-font-size: 15px;");
        String savedStabW = db.getSetting(com.backtester.engine.LlmAnalysisService.SETTING_STABILITY_WEIGHT);
        stabWeightField.setText(savedStabW != null && !savedStabW.isBlank() ? savedStabW : String.valueOf(com.backtester.engine.LlmAnalysisService.DEFAULT_STABILITY_WEIGHT));

        // Save Button
        Button saveBtn = new Button("Speichern");
        saveBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px; -fx-padding: 8 24;");
        saveBtn.setOnAction(e -> {
            db.saveSetting(com.backtester.engine.LlmAnalysisService.SETTING_API_KEY, keyField.getText().trim());
            // Extract model ID without the "(max ...)" suffix
            String selectedModel = modelCombo.getValue().trim();
            if (selectedModel.contains("(")) {
                selectedModel = selectedModel.substring(0, selectedModel.indexOf("(")).trim();
            }
            db.saveSetting(com.backtester.engine.LlmAnalysisService.SETTING_MODEL, selectedModel);
            db.saveSetting(com.backtester.engine.LlmAnalysisService.SETTING_MAX_TOKENS, maxTokensField.getText().trim());
            db.saveSetting(com.backtester.engine.LlmAnalysisService.SETTING_PROMPT, promptField.getText().trim());

            double perfW = com.backtester.engine.LlmAnalysisService.DEFAULT_PERFORMANCE_WEIGHT;
            double stabW = com.backtester.engine.LlmAnalysisService.DEFAULT_STABILITY_WEIGHT;
            try {
                perfW = Double.parseDouble(perfWeightField.getText().trim());
                stabW = Double.parseDouble(stabWeightField.getText().trim());
            } catch (Exception ignored) {}
            db.saveSetting(com.backtester.engine.LlmAnalysisService.SETTING_PERFORMANCE_WEIGHT, String.valueOf(perfW));
            db.saveSetting(com.backtester.engine.LlmAnalysisService.SETTING_STABILITY_WEIGHT, String.valueOf(stabW));

            stage.close();
            host.logView().log("INFO", "KI-Einstellungen gespeichert.");
        });

        box.getChildren().addAll(titleLabel, keyLabel, keyField, modelLabel, modelCombo, hintLabel,
            perfWeightLabel, perfWeightField, stabWeightLabel, stabWeightField,
            maxTokensLabel, maxTokensField, maxTokensHint, promptLabel, promptField, saveBtn);

        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(box);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background: #0b0d13; -fx-background-color: #0b0d13;");

        javafx.scene.Scene scene = new javafx.scene.Scene(scrollPane, 650, 750);
        scene.setFill(Color.web("#0b0d13"));
        java.util.List<String> sheets = host.stylesheets();
        if (sheets != null && !sheets.isEmpty()) {
            scene.getStylesheets().addAll(sheets);
        }
        stage.setScene(scene);
        stage.show();
    }

}
