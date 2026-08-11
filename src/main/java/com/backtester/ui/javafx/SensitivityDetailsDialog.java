package com.backtester.ui.javafx;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Dialog showing detailed sensitivity / CV breakdown for a single pass.
 */
public final class SensitivityDetailsDialog {

    private SensitivityDetailsDialog() {}

    public static void show(javafx.stage.Window owner, com.backtester.report.SensitivityResult result) {
        if (result == null) return;
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Sensitivity Details - Pass " + result.getOriginalPass().getPassNumber());

        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.getStyleClass().add("root");
        box.setStyle("-fx-background-color: #0b0d13;"); // Ensure dark background

        Label title = new Label("Strategy Details: Pass " + result.getOriginalPass().getPassNumber());
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#00e5ff"));

        String fwSummary = result.hasForwardCV()
                ? String.format("  |  FW Profit: %.2f  |  FW CV (worst): %.2f %%",
                        result.getOriginalPass().getFwProfit(), result.getOverallCVFw())
                : "  |  FW: -";
        Label scoreLabel = new Label(String.format("BT Profit: %.2f  |  BT CV (worst): %.2f %%%s",
                result.getOriginalPass().getBtProfit(), result.getOverallCV(), fwSummary));
        scoreLabel.setTextFill(Color.web("#00e676"));

        Label btCvLabel = new Label("Backtest (in-sample) Parameter Robustness:");
        btCvLabel.setTextFill(Color.WHITE);
        btCvLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        TableView<java.util.Map.Entry<String, Double>> btCvTable = buildCvBreakdownTable(
                result.getParameterCVs(),
                result.getParameterCurves(),
                result.getOriginalPass().getBacktestPass().getParameterValues(),
                "#00e5ff");

        // All Strategy Parameters Table (from Original Pass)
        Label paramLabel = new Label("Optimized Strategy Settings:");
        paramLabel.setTextFill(Color.WHITE);
        paramLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));

        TableView<java.util.Map.Entry<String, String>> settingsTable = new TableView<>();
        TableColumn<java.util.Map.Entry<String, String>, String> sParamCol = new TableColumn<>("Parameter");
        sParamCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getKey()));
        TableColumn<java.util.Map.Entry<String, String>, String> sValCol = new TableColumn<>("Value");
        sValCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getValue()));
        settingsTable.getColumns().addAll(sParamCol, sValCol);
        settingsTable.getItems().addAll(result.getOriginalPass().getBacktestPass().getParameterValues().entrySet());
        settingsTable.setPrefHeight(250);
        settingsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        settingsTable.setSelectionModel(null); // avoid selection text-color artifacts on dark theme

        box.getChildren().addAll(title, scoreLabel, btCvLabel, btCvTable);

        if (result.hasForwardCV()) {
            Label fwCvLabel = new Label("Forward (out-of-sample) Parameter Robustness:");
            fwCvLabel.setTextFill(Color.WHITE);
            fwCvLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
            TableView<java.util.Map.Entry<String, Double>> fwCvTable = buildCvBreakdownTable(
                    result.getParameterCVsFw(),
                    result.getParameterCurvesFw(),
                    result.getOriginalPass().getBacktestPass().getParameterValues(),
                    "#ff9100");
            box.getChildren().addAll(fwCvLabel, fwCvTable);
        }

        box.getChildren().addAll(paramLabel, settingsTable);

        // --- Detailed Interpretation ---
        Label explanationTitle = new Label("Ausführliche Erklärung zur Interpretation");
        explanationTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        explanationTitle.setTextFill(Color.web("#00e5ff"));

        String interpretationText =
            "Die Sensitivitätsanalyse testet, wie 'zerbrechlich' deine Strategie ist.\n" +
            "Dazu wird jeder Parameter (z.B. StopLoss, Takeprofit) in kleinen Schritten um seinen optimierten Wert herum verschoben. " +
            "Anschließend messen wir, wie stark sich der Profit durch diese kleinen Änderungen verändert.\n\n" +
            "Der CV-Wert (Coefficient of Variation) ist das Maß für diese Schwankung:\n" +
            "• Unter 30% (Grün): Der Parameter ist extrem stabil. Wenn der Markt sich leicht ändert (Slippage, andere Spreads, leicht veränderte Volatilität), bleibt dein Profit weitgehend gleich.\n" +
            "• 30% bis 60% (Gelb): Normale Schwankung. Die Strategie reagiert auf Marktveränderungen, bleibt aber vermutlich noch profitabel.\n" +
            "• Über 60% (Rot): Gefahr! Die Strategie ist ein 'One-Hit-Wonder'. Ein winziger Unterschied im Markt, und die Strategie stürzt ab. Solche Werte bedeuten oft, dass der Backtest komplett 'curve-fitted' (überoptimiert) ist.\n\n";

        double worstCv = result.getOverallCV();
        if (result.hasForwardCV() && result.getOverallCVFw() > worstCv) {
            worstCv = result.getOverallCVFw();
        }

        String passVerdict = "";
        Color verdictColor = Color.WHITE;
        if (worstCv < 30.0) {
            passVerdict = String.format("FAZIT ZU DIESEM PASS:\nDein schlechtester CV liegt bei %.2f %%. Dies ist ein exzellenter Wert! Die Parameter sind extrem robust.", worstCv);
            verdictColor = Color.web("#00e676");
        } else if (worstCv <= 60.0) {
            passVerdict = String.format("FAZIT ZU DIESEM PASS:\nDein schlechtester CV liegt bei %.2f %%. Das ist solide. Die Strategie wird nicht sofort zusammenbrechen, wenn sich die Marktbedingungen leicht ändern.", worstCv);
            verdictColor = Color.web("#ffd740");
        } else {
            passVerdict = String.format("FAZIT ZU DIESEM PASS:\nACHTUNG! Der schlechteste CV liegt bei gigantischen %.2f %%! Dieser Pass ist zu stark überoptimiert (Curve-Fitted). Im Live-Handel wird er höchstwahrscheinlich Verluste einfahren. Bitte mit Vorsicht behandeln!", worstCv);
            verdictColor = Color.web("#ff3b30");
        }

        Label interpretationLabel = new Label(interpretationText);
        interpretationLabel.setWrapText(true);
        interpretationLabel.setStyle("-fx-text-fill: #b4bac8; -fx-font-size: 13px;");

        Label verdictLabel = new Label(passVerdict);
        verdictLabel.setWrapText(true);
        verdictLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        verdictLabel.setTextFill(verdictColor);

        VBox expBox = new VBox(10, explanationTitle, interpretationLabel, verdictLabel);
        expBox.setStyle("-fx-background-color: #1a1d27; -fx-padding: 15; -fx-background-radius: 8; -fx-border-color: #2a2d3a; -fx-border-width: 1; -fx-border-radius: 8;");

        box.getChildren().add(expBox);

        // Wrap content in a scroll pane so the dialog scales gracefully when both
        // BT and FW breakdown tables are shown.
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(box);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #0b0d13;");
        javafx.scene.Scene scene = new javafx.scene.Scene(scroll, 1000, 750);
        if (owner != null && owner.getScene() != null && !owner.getScene().getStylesheets().isEmpty()) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        stage.setScene(scene);
        stage.show();
    }

    @SuppressWarnings("unchecked")
    private static TableView<java.util.Map.Entry<String, Double>> buildCvBreakdownTable(
            java.util.Map<String, Double> cvMap,
            java.util.Map<String, java.util.List<com.backtester.report.SensitivityResult.DataPoint>> curves,
            java.util.Map<String, String> baseValues,
            String accentColor) {

        TableView<java.util.Map.Entry<String, Double>> cvTable = new TableView<>();
        TableColumn<java.util.Map.Entry<String, Double>, String> paramCol = new TableColumn<>("Parameter");
        paramCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getKey()));

        TableColumn<java.util.Map.Entry<String, Double>, VBox> valCol = new TableColumn<>("CV (%)");
        valCol.setCellValueFactory(c -> {
            String pName = c.getValue().getKey();
            double cv = c.getValue().getValue();
            java.util.List<com.backtester.report.SensitivityResult.DataPoint> curveData =
                    curves != null ? curves.get(pName) : null;

            VBox calcBox = new VBox(5);
            calcBox.setAlignment(Pos.CENTER_LEFT);
            calcBox.setPadding(new Insets(0, 0, 0, 10));

            Label cvValueLabel = new Label(String.format("%.2f %%", cv));
            cvValueLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
            cvValueLabel.setTextFill(Color.web(accentColor));

            Button infoBtn = new Button("\u2139");
            infoBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + accentColor +
                    "; -fx-cursor: hand; -fx-border-color: " + accentColor +
                    "; -fx-border-radius: 15px; -fx-font-weight: bold; -fx-padding: 0 5 0 5;");

            javafx.scene.layout.HBox topBox = new javafx.scene.layout.HBox(10, cvValueLabel, infoBtn);
            topBox.setAlignment(Pos.CENTER_LEFT);

            if (curveData != null && !curveData.isEmpty()) {
                double sum = curveData.stream().mapToDouble(d -> d.profit).sum();
                double mean = sum / curveData.size();
                double varianceSum = 0;
                for (com.backtester.report.SensitivityResult.DataPoint dp : curveData) {
                    varianceSum += Math.pow(dp.profit - mean, 2);
                }
                double stdDev = Math.sqrt(varianceSum / curveData.size());

                infoBtn.setOnAction(e -> {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.INFORMATION);
                    alert.setTitle("Erkl\u00e4rung: Parameter Robustness");
                    alert.setHeaderText("Was bedeutet der CV-Wert f\u00fcr " + pName + "?");

                    String explanation = String.format(java.util.Locale.US,
                        "Der CV-Wert (Coefficient of Variation) zeigt an, wie stark der Profit schwankt, wenn sich der Parameter '%s' leicht \u00e4ndert.\n\n" +
                        "Ein kleiner CV-Wert bedeutet, dass die Strategie sehr stabil (robust) ist.\n" +
                        "Ein hoher Wert zeigt an, dass schon winzige \u00c4nderungen am Parameter den Profit massiv einbrechen lassen k\u00f6nnen \u2013 die Strategie ist hier anf\u00e4llig und \u00fcberoptimiert!\n\n" +
                        "--- BERECHNUNG ---\n\n" +
                        "1. Durchschnittlicher Profit der Varianten (Mean):\n" +
                        "In unseren Tests lag der Profit f\u00fcr diesen Parameter im Schnitt bei %.2f USD.\n\n" +
                        "2. Schwankung (Standardabweichung / StdDev):\n" +
                        "Der Profit schwankte im Schnitt um %.2f USD.\n\n" +
                        "3. Die Formel (CV):\n" +
                        "Wir teilen die Schwankung durch den ORIGINALEN Basis-Profit der optimierten Strategie und rechnen mal 100:\n" +
                        "CV = (StdDev / |Basis-Profit|) * 100\n" +
                        "CV = (%.2f / %.2f) * 100 = %.2f %%\n\n" +
                        "Hinweis: Wir verwenden den Basis-Profit statt des Durchschnitts, weil der klassische CV bei Profiten nahe Null (wo positive und negative Ergebnisse gemischt werden) unsinnig hohe Werte liefert.\n\n" +
                        "Faustregel:\n" +
                        "\u2022 Unter 20%%: Sehr robust. Der Parameter ist stabil.\n" +
                        "\u2022 20%% - 50%%: Akzeptabel. Es gibt Schwankungen, aber im Rahmen.\n" +
                        "\u2022 \u00dcber 50%%: Gef\u00e4hrlich! Die Strategie ist hier eine 'Klippe' und extrem riskant.",
                        pName, mean, stdDev, stdDev, Math.abs(mean) > 0.01 ? Math.abs(mean) : 1.0, cv
                    );

                    Label expLabel = new Label(explanation);
                    expLabel.setWrapText(true);
                    expLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13px;");
                    alert.getDialogPane().setContent(expLabel);
                    alert.getDialogPane().setPrefWidth(550);
                    alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
                    try {
                        if (cvTable.getScene() != null && !cvTable.getScene().getStylesheets().isEmpty()) {
                            alert.getDialogPane().getStylesheets().addAll(cvTable.getScene().getStylesheets());
                        }
                    } catch (Exception ignored) {}
                    alert.getDialogPane().setStyle("-fx-base: #11141d; -fx-background-color: #11141d; -fx-text-fill: white;");
                    alert.showAndWait();
                });

                Label formulaLabel = new Label("CV = (StdDev / |Basis-Profit|) * 100");
                formulaLabel.setFont(Font.font("Segoe UI", 10));
                formulaLabel.setTextFill(Color.web("#8093a5"));

                Label calcLabel = new Label(String.format(java.util.Locale.US,
                        "= (%.2f / |Basis-Profit|) * 100 = %.2f%%", stdDev, cv));
                calcLabel.setFont(Font.font("Segoe UI", 10));
                calcLabel.setTextFill(Color.web("#8093a5"));

                calcBox.getChildren().addAll(topBox, formulaLabel, calcLabel);
            } else {
                calcBox.getChildren().add(topBox);
            }
            return new javafx.beans.property.SimpleObjectProperty<>(calcBox);
        });
        valCol.setPrefWidth(200);

        TableColumn<java.util.Map.Entry<String, Double>, VBox> chartCol = new TableColumn<>("Curve");
        chartCol.setCellValueFactory(c -> {
            String pName = c.getValue().getKey();
            java.util.List<com.backtester.report.SensitivityResult.DataPoint> curveData =
                    curves != null ? curves.get(pName) : null;
            if (curveData == null || curveData.isEmpty()) {
                return new javafx.beans.property.SimpleObjectProperty<>(null);
            }

            String baseValueStr = baseValues != null ? baseValues.get(pName) : null;
            double baseValue = 0;
            try { if (baseValueStr != null) baseValue = Double.parseDouble(baseValueStr); } catch (Exception ignored) {}
            final double finalBaseValue = baseValue;

            double minX = curveData.get(0).paramValue;
            double maxX = curveData.get(curveData.size() - 1).paramValue;
            double xPadding = (maxX - minX) * 0.05;
            if (xPadding == 0) xPadding = 1;

            javafx.scene.chart.NumberAxis xAxis = new javafx.scene.chart.NumberAxis();
            xAxis.setTickLabelsVisible(true); xAxis.setOpacity(1);
            xAxis.setTickMarkVisible(true); xAxis.setMinorTickVisible(false);
            xAxis.setTickLabelFill(Color.WHITE);
            xAxis.setAutoRanging(false);
            xAxis.setLowerBound(minX - xPadding);
            xAxis.setUpperBound(maxX + xPadding);

            javafx.scene.chart.NumberAxis yAxis = new javafx.scene.chart.NumberAxis();
            yAxis.setTickLabelsVisible(false); yAxis.setOpacity(0);
            yAxis.setTickMarkVisible(false); yAxis.setMinorTickVisible(false);

            double minY = curveData.stream().mapToDouble(d -> d.profit).min().orElse(0);
            double maxY = curveData.stream().mapToDouble(d -> d.profit).max().orElse(1);
            double yPadding = (maxY - minY) * 0.1;
            if (yPadding == 0) yPadding = 1;
            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(minY - yPadding);
            yAxis.setUpperBound(maxY + yPadding);

            javafx.scene.chart.LineChart<Number, Number> chart = new javafx.scene.chart.LineChart<>(xAxis, yAxis);
            chart.setCreateSymbols(true);
            chart.setLegendVisible(false);
            chart.setAnimated(false);
            chart.setPrefHeight(100); chart.setMinHeight(100); chart.setMaxHeight(100);
            chart.setPrefWidth(300);
            chart.setHorizontalGridLinesVisible(false);
            chart.setVerticalGridLinesVisible(false);

            javafx.scene.chart.XYChart.Series<Number, Number> series = new javafx.scene.chart.XYChart.Series<>();
            com.backtester.report.SensitivityResult.DataPoint closestToBase = null;
            double minDiff = Double.MAX_VALUE;

            for (com.backtester.report.SensitivityResult.DataPoint dp : curveData) {
                series.getData().add(new javafx.scene.chart.XYChart.Data<>(dp.paramValue, dp.profit));
                double diff = Math.abs(dp.paramValue - finalBaseValue);
                if (diff < minDiff) { minDiff = diff; closestToBase = dp; }
            }
            chart.getData().add(series);
            final com.backtester.report.SensitivityResult.DataPoint finalClosest = closestToBase;

            chart.lookup(".chart-plot-background").setStyle("-fx-background-color: transparent;");
            chart.setStyle("-fx-padding: 0; -fx-background-color: transparent;");

            javafx.application.Platform.runLater(() -> {
                if (series.getNode() != null) {
                    series.getNode().setStyle("-fx-stroke: " + accentColor + "; -fx-stroke-width: 4px;");
                }
                for (javafx.scene.chart.XYChart.Data<Number, Number> data : series.getData()) {
                    if (data.getNode() != null) {
                        boolean isBase = finalClosest != null && data.getXValue().doubleValue() == finalClosest.paramValue;
                        if (isBase) {
                            data.getNode().setStyle("-fx-background-color: #ff3d00, white; -fx-background-insets: 0, 2; -fx-background-radius: 8px; -fx-padding: 6px;");
                        } else {
                            data.getNode().setStyle("-fx-background-color: " + accentColor + ", #0b0d13; -fx-background-insets: 0, 2; -fx-background-radius: 4px; -fx-padding: 3px;");
                        }
                    }
                }
            });

            double stepVal = curveData.size() > 1 ? (maxX - minX) / (curveData.size() - 1) : 0;
            String infoTxt = String.format(java.util.Locale.US, "Start: %.4f | Step: %.4f | End: %.4f", minX, stepVal, maxX)
                                   .replaceAll("0+ \\|", " |").replaceAll("\\. \\|", " |");
            Label infoLabel = new Label(infoTxt);
            infoLabel.setTextFill(Color.web("#8093a5"));
            infoLabel.setFont(Font.font("Segoe UI", 11));

            VBox chartBox = new VBox(5, chart, infoLabel);
            chartBox.setAlignment(Pos.CENTER);
            return new javafx.beans.property.SimpleObjectProperty<>(chartBox);
        });

        chartCol.setCellFactory(col -> new TableCell<java.util.Map.Entry<String, Double>, VBox>() {
            @Override
            protected void updateItem(VBox item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    setGraphic(item);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        cvTable.getColumns().addAll(paramCol, valCol, chartCol);
        if (cvMap != null) {
            for (java.util.Map.Entry<String, Double> entry : cvMap.entrySet()) {
                cvTable.getItems().add(new java.util.AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
            }
        }
        cvTable.setPrefHeight(300);
        cvTable.setFixedCellSize(130);
        cvTable.setSelectionModel(null);
        cvTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        return cvTable;
    }

}
