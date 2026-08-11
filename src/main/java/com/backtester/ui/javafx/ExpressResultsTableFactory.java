package com.backtester.ui.javafx;

import com.backtester.engine.WorkflowEngine;
import com.backtester.report.OptimizationResult.CombinedPass;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Builds the Express Workflow CombinedPass results TableView (columns + row interactions).
 */
public final class ExpressResultsTableFactory {
    private ExpressResultsTableFactory() {}

    public static TableView<CombinedPass> createResultsTable(
            WorkflowEngine engine,
            Consumer<CombinedPass> onShowDetail,
            BiConsumer<CombinedPass, Boolean> onRunSingleBacktest) {

        TableView<CombinedPass> resultsTable = new TableView<>();
        resultsTable.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(resultsTable, Priority.ALWAYS);
        resultsTable.setRowFactory(tv -> {
            TableRow<CombinedPass> row = new TableRow<>();
            
            ContextMenu rowMenu = new ContextMenu();
            MenuItem normalBtItem = new MenuItem("▶ Backtest starten (Normal)");
            normalBtItem.setOnAction(e -> {
                CombinedPass selected = row.getItem();
                if (selected != null) {
                    onRunSingleBacktest.accept(selected, false);
                }
            });
            MenuItem visualBtItem = new MenuItem("👁 Backtest starten (Visuell)");
            visualBtItem.setOnAction(e -> {
                CombinedPass selected = row.getItem();
                if (selected != null) {
                    onRunSingleBacktest.accept(selected, true);
                }
            });
            rowMenu.getItems().addAll(normalBtItem, visualBtItem);

            // Bind context menu only to non-empty rows
            row.contextMenuProperty().bind(
                javafx.beans.binding.Bindings.when(row.emptyProperty())
                    .then((ContextMenu) null)
                    .otherwise(rowMenu)
            );

            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && row.getItem() != null && event.getButton() == MouseButton.PRIMARY) {
                    CombinedPass selected = row.getItem();
                    onShowDetail.accept(selected);
                }
            });
            row.hoverProperty().addListener((obs, wasHovered, isNowHovered) -> {
                if (isNowHovered && !row.isEmpty()) {
                    row.setCursor(javafx.scene.Cursor.HAND);
                } else {
                    row.setCursor(javafx.scene.Cursor.DEFAULT);
                }
            });
            return row;
        });

        TableColumn<CombinedPass, Integer> passCol = new TableColumn<>("Pass");
        passCol.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getPassNumber()));
        passCol.setPrefWidth(65);
        passCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<CombinedPass, Double> scoreCol = new TableColumn<>();
        scoreCol.setGraphic(DocHelper.createHeaderWithTooltip("Score", 
            "Unified Score (0-100):\nGewichteter Gesamtwert aus 10 Kriterien (Profit, DD, PF etc.). Konfigurierbar über das Regler-Symbol. Zeigt die beste Gesamtperformance."));
        scoreCol.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getScore()));
        scoreCol.setPrefWidth(75);
        scoreCol.setStyle("-fx-alignment: CENTER;");
        scoreCol.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.1f", item));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<CombinedPass, String> robScoreCol = new TableColumn<>();
        robScoreCol.setGraphic(DocHelper.createHeaderWithTooltip("Rob. Scorecard", 
            "Robustness Scorecard (0-100):\nErgebnis des Monte-Carlo-Stresstests und systematischen Parameter-Shifting. Simuliert Rauschen (Slippage, Spread, Execution) und bewertet die Geradlinigkeit (R²-Stabilität) der Equity-Kurve."));
        robScoreCol.setCellValueFactory(c -> {
            String fromDateStr = engine.getFromDate() != null ? engine.getFromDate().toString() : "Unbekannt";
            String toDateStr = engine.getToDate() != null ? engine.getToDate().toString() : "Unbekannt";
            double score = c.getValue().getCachedOverallScore(fromDateStr, toDateStr);
            return new javafx.beans.property.SimpleStringProperty(String.format(Locale.US, "%.0f", score));
        });
        robScoreCol.setPrefWidth(115);
        robScoreCol.setCellFactory(col -> new TableCell<CombinedPass, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    try {
                        double score = Double.parseDouble(item);
                        String color;
                        if (score >= 70) {
                            color = "#00e676"; // Green
                        } else if (score >= 55) {
                            color = "#ffd740"; // Yellow
                        } else {
                            color = "#ff5252"; // Red
                        }
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
                    } catch (Exception e) {
                        setStyle("-fx-alignment: CENTER;");
                    }
                }
            }
        });

        TableColumn<CombinedPass, Double> btProf = new TableColumn<>("BT Profit");
        btProf.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getBtProfit()));
        btProf.setPrefWidth(95);
        btProf.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f", item));
                    if (item >= 0) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #00e676; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ff5252; -fx-font-weight: bold;");
                    }
                }
            }
        });

        TableColumn<CombinedPass, Integer> btTr = new TableColumn<>("BT Trades");
        btTr.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getBtTrades()));
        btTr.setPrefWidth(85);
        btTr.setCellFactory(col -> new TableCell<CombinedPass, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.valueOf(item));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<CombinedPass, Double> btPf = new TableColumn<>("BT PF");
        btPf.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getBtPf()));
        btPf.setPrefWidth(75);
        btPf.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f", item));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<CombinedPass, Double> btDd = new TableColumn<>("BT DD%");
        btDd.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getBtDd()));
        btDd.setPrefWidth(85);
        btDd.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f %%", item));
                    if (item > 25) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ff5252;");
                    } else if (item > 15) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ffd740;");
                    } else {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #00e676;");
                    }
                }
            }
        });

        TableColumn<CombinedPass, Double> btRec = new TableColumn<>("BT RF");
        btRec.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getBtRecovery()));
        btRec.setPrefWidth(75);
        btRec.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f", item));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<CombinedPass, Double> fwProf = new TableColumn<>("FW Profit");
        fwProf.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getFwProfit()));
        fwProf.setPrefWidth(95);
        fwProf.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f", item));
                    if (item >= 0) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #00e676; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ff5252; -fx-font-weight: bold;");
                    }
                }
            }
        });

        TableColumn<CombinedPass, Integer> fwTr = new TableColumn<>("FW Trades");
        fwTr.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getFwTrades()));
        fwTr.setPrefWidth(85);
        fwTr.setCellFactory(col -> new TableCell<CombinedPass, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.valueOf(item));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<CombinedPass, Double> fwPf = new TableColumn<>("FW PF");
        fwPf.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getFwPf()));
        fwPf.setPrefWidth(75);
        fwPf.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f", item));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<CombinedPass, Double> fwDd = new TableColumn<>("FW DD%");
        fwDd.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getFwDd()));
        fwDd.setPrefWidth(85);
        fwDd.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f %%", item));
                    if (item > 25) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ff5252;");
                    } else if (item > 15) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ffd740;");
                    } else {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #00e676;");
                    }
                }
            }
        });

        TableColumn<CombinedPass, Double> fwRec = new TableColumn<>("FW RF");
        fwRec.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getFwRecovery()));
        fwRec.setPrefWidth(75);
        fwRec.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f", item));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<CombinedPass, Double> ltProf = new TableColumn<>("LT Profit (5-10J)");
        ltProf.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getLtProfit()));
        ltProf.setPrefWidth(115);
        ltProf.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f", item));
                    if (item >= 0) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #00e676; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ff5252; -fx-font-weight: bold;");
                    }
                }
            }
        });

        TableColumn<CombinedPass, Integer> ltTr = new TableColumn<>("LT Trades");
        ltTr.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getLtTrades()));
        ltTr.setPrefWidth(85);
        ltTr.setCellFactory(col -> new TableCell<CombinedPass, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item == 0) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.valueOf(item));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<CombinedPass, Double> ltPf = new TableColumn<>("LT PF");
        ltPf.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getLtPf()));
        ltPf.setPrefWidth(75);
        ltPf.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f", item));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<CombinedPass, Double> ltDd = new TableColumn<>("LT DD%");
        ltDd.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getLtDd()));
        ltDd.setPrefWidth(85);
        ltDd.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f %%", item));
                    if (item > 35) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ff5252;");
                    } else if (item > 20) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ffd740;");
                    } else {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #00e676;");
                    }
                }
            }
        });

        TableColumn<CombinedPass, Double> ltRec = new TableColumn<>("LT RF");
        ltRec.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getLtRecovery()));
        ltRec.setPrefWidth(75);
        ltRec.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f", item));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<CombinedPass, String> btCvCol = new TableColumn<>("Worst BT CV");
        btCvCol.setCellValueFactory(cellData -> {
            CombinedPass cp = cellData.getValue();
            double cv = engine.getWorstCvForPass(cp, false);
            return new javafx.beans.property.SimpleStringProperty(Double.isNaN(cv) || cv == 0 ? "-" : String.format(Locale.US, "%.2f %%", cv));
        });
        btCvCol.setPrefWidth(110);
        btCvCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<CombinedPass, String> fwCvCol = new TableColumn<>("Worst FW CV");
        fwCvCol.setCellValueFactory(cellData -> {
            CombinedPass cp = cellData.getValue();
            double cv = engine.getWorstCvForPass(cp, true);
            return new javafx.beans.property.SimpleStringProperty(Double.isNaN(cv) || cv == 0 ? "-" : String.format(Locale.US, "%.2f %%", cv));
        });
        fwCvCol.setPrefWidth(110);
        fwCvCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<CombinedPass, String> kiRatingCol = new TableColumn<>("KI Stabilität");
        kiRatingCol.setCellValueFactory(cellData -> {
            CombinedPass cp = cellData.getValue();
            int score = engine.getKiScoreForPass(cp);
            return new javafx.beans.property.SimpleStringProperty(score < 0 ? "-" : String.valueOf(score) + " / 100");
        });
        kiRatingCol.setPrefWidth(110);
        kiRatingCol.setCellFactory(col -> new TableCell<CombinedPass, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.equals("-")) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(item);
                    try {
                        int score = Integer.parseInt(item.split(" ")[0]);
                        String color;
                        if (score >= 80) color = "#00e676";
                        else if (score >= 70) color = "#66bb6a";
                        else if (score >= 50) color = "#ffd740";
                        else color = "#ff3b30";
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
                    } catch (Exception e) {
                        setStyle("-fx-alignment: CENTER;");
                    }
                }
            }
        });

        TableColumn<CombinedPass, String> paramsCol = new TableColumn<>("Parameter");
        paramsCol.setCellValueFactory(cellData -> {
            CombinedPass cp = cellData.getValue();
            if (cp.getBacktestPass() == null) return new javafx.beans.property.SimpleStringProperty("-");
            Map<String, String> pVals = cp.getBacktestPass().getParameterValues();
            StringBuilder sb = new StringBuilder();
            pVals.forEach((k, v) -> sb.append(k).append("=").append(v).append(", "));
            if (sb.length() > 2) sb.setLength(sb.length() - 2);
            return new javafx.beans.property.SimpleStringProperty(sb.toString());
        });
        paramsCol.setPrefWidth(300);

        resultsTable.getColumns().addAll(passCol, scoreCol, robScoreCol, btProf, btTr, btPf, btDd, btRec, fwProf, fwTr, fwPf, fwDd, fwRec, ltProf, ltTr, ltPf, ltDd, ltRec, btCvCol, fwCvCol, kiRatingCol, paramsCol);

        return resultsTable;
    }
}
