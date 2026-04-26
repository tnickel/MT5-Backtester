package com.backtester.ui.javafx;

import com.backtester.config.AppConfig;
import com.backtester.engine.BacktestConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;

public class DukascopyView {

    private final BorderPane root;
    private final LogView logView;
    private final AppConfig config;

    // Download section
    private DatePicker downloadFromDate;
    private DatePicker downloadToDate;
    private ProgressBar downloadProgress;

    // Import section
    private TableView<Object> dataTable;
    private ProgressBar importProgress;

    public DukascopyView(LogView logView) {
        this.logView = logView;
        this.config = AppConfig.getInstance();

        root = new BorderPane();
        root.setPadding(new Insets(15));

        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        splitPane.setStyle("-fx-background-color: transparent; -fx-box-border: transparent;");

        VBox downloadBox = createDownloadBox();
        VBox importBox = createImportBox();

        splitPane.getItems().addAll(downloadBox, importBox);
        splitPane.setDividerPositions(0.45);

        root.setCenter(splitPane);
    }

    private VBox createDownloadBox() {
        VBox box = new VBox(15);
        box.getStyleClass().add("sci-fi-panel");

        Label title = new Label("Step 1: Download Dukascopy Data");
        title.getStyleClass().add("sci-fi-panel-title");

        // Symbols
        VBox symbolBox = new VBox(10);
        Label symbolLabel = new Label("Select Currency Pairs:");
        
        GridPane cbGrid = new GridPane();
        cbGrid.setHgap(15);
        cbGrid.setVgap(10);
        int col = 0;
        int row = 0;
        for (String symbol : BacktestConfig.SYMBOLS) {
            CheckBox cb = new CheckBox(symbol);
            if (symbol.equals("EURUSD")) cb.setSelected(true);
            cbGrid.add(cb, col, row);
            col++;
            if (col > 3) {
                col = 0;
                row++;
            }
        }
        
        HBox selectBtns = new HBox(10);
        Button selectAll = new Button("Select All");
        selectAll.getStyleClass().add("button");
        Button selectNone = new Button("Select None");
        selectNone.getStyleClass().add("button");
        selectBtns.getChildren().addAll(selectAll, selectNone);

        symbolBox.getChildren().addAll(symbolLabel, cbGrid, selectBtns);

        // Dates
        HBox dateBox = new HBox(15);
        dateBox.setAlignment(Pos.CENTER_LEFT);
        
        downloadFromDate = new DatePicker(LocalDate.now().minusMonths(6));
        downloadToDate = new DatePicker(LocalDate.now().minusDays(1));
        
        dateBox.getChildren().addAll(
                new Label("From:"), downloadFromDate,
                new Label("To:"), downloadToDate
        );

        // Controls
        HBox controlBox = new HBox(15);
        controlBox.setAlignment(Pos.CENTER_LEFT);
        
        Button downloadBtn = new Button("⬇ Download Data");
        downloadBtn.getStyleClass().addAll("button", "button-start");
        downloadBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #28648c, #143246);");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("button");
        cancelBtn.setDisable(true);

        downloadProgress = new ProgressBar(0);
        downloadProgress.setPrefWidth(300);

        controlBox.getChildren().addAll(downloadBtn, cancelBtn, downloadProgress);

        box.getChildren().addAll(title, symbolBox, dateBox, controlBox);
        return box;
    }

    private VBox createImportBox() {
        VBox box = new VBox(10);
        box.getStyleClass().add("sci-fi-panel");

        Label title = new Label("Step 2: Convert & Import to MetaTrader 5");
        title.getStyleClass().add("sci-fi-panel-title");

        dataTable = new TableView<>();
        dataTable.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(dataTable, Priority.ALWAYS);

        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        Button scanBtn = new Button("Scan Downloaded Data");
        scanBtn.getStyleClass().add("button");

        Button convertBtn = new Button("Convert to CSV");
        convertBtn.getStyleClass().add("button");
        convertBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #786428, #3c3214);");

        Button importBtn = new Button("Import to MT5");
        importBtn.getStyleClass().addAll("button", "button-start");
        importBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #287846, #143c23);");

        Button exportBtn = new Button("\uD83D\uDCCA Export CSV");
        exportBtn.getStyleClass().add("button");

        importProgress = new ProgressBar(0);
        importProgress.setPrefWidth(200);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnBox.getChildren().addAll(scanBtn, convertBtn, importBtn, exportBtn, spacer, importProgress);

        box.getChildren().addAll(title, dataTable, btnBox);
        return box;
    }

    public BorderPane getView() {
        return root;
    }
}
