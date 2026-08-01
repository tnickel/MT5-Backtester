package com.backtester.ui.javafx;

import com.backtester.database.DatabaseManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.*;

/**
 * Modal dialog allowing users to customize which columns are visible in Databank strategy tables.
 */
public class DatabankColumnChooserDialog {

    public enum DatabankColumn {
        NAME("NAME", "Strategy Name", true),
        PASS("PASS", "Pass #", true),
        SCORE("SCORE", "Score", true),
        BT_PROFIT("BT_PROFIT", "BT Profit ($)", true),
        FW_PROFIT("FW_PROFIT", "FW Profit ($)", true),
        LT_PROFIT("LT_PROFIT", "LT Profit ($)", true),
        BT_PF("BT_PF", "BT Profit Factor", true),
        FW_PF("FW_PF", "FW Profit Factor", true),
        LT_PF("LT_PF", "LT Profit Factor", true),
        BT_DD("BT_DD", "BT Max DD %", true),
        FW_DD("FW_DD", "FW Max DD %", true),
        LT_DD("LT_DD", "LT Max DD %", true),
        BT_TRADES("BT_TRADES", "BT Trades", false),
        FW_TRADES("FW_TRADES", "FW Trades", false),
        LT_TRADES("LT_TRADES", "LT Trades", false),
        BT_SHARPE("BT_SHARPE", "BT Sharpe Ratio", false),
        FW_SHARPE("FW_SHARPE", "FW Sharpe Ratio", false),
        BT_RECOVERY("BT_RECOVERY", "BT Recovery Factor", false),
        FW_RECOVERY("FW_RECOVERY", "FW Recovery Factor", false);

        private final String code;
        private final String label;
        private final boolean defaultVisible;

        DatabankColumn(String code, String label, boolean defaultVisible) {
            this.code = code;
            this.label = label;
            this.defaultVisible = defaultVisible;
        }

        public String getCode() { return code; }
        public String getLabel() { return label; }
        public boolean isDefaultVisible() { return defaultVisible; }
    }

    private static final String PREF_KEY = "databank_visible_columns";

    public static Set<DatabankColumn> getVisibleColumns() {
        String saved = DatabaseManager.getInstance().getSetting(PREF_KEY, null);
        Set<DatabankColumn> visible = new LinkedHashSet<>();
        if (saved != null && !saved.trim().isEmpty()) {
            String[] tokens = saved.split(",");
            for (String t : tokens) {
                try {
                    visible.add(DatabankColumn.valueOf(t.trim()));
                } catch (Exception ignored) {}
            }
        }
        if (visible.isEmpty()) {
            for (DatabankColumn col : DatabankColumn.values()) {
                if (col.isDefaultVisible()) visible.add(col);
            }
        }
        return visible;
    }

    public static void saveVisibleColumns(Set<DatabankColumn> visible) {
        StringJoiner sj = new StringJoiner(",");
        for (DatabankColumn col : visible) {
            sj.add(col.name());
        }
        DatabaseManager.getInstance().saveSetting(PREF_KEY, sj.toString());
    }

    public static boolean show(Window owner, Runnable onApplied) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Configure Databank Columns");

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #0b0d13;");
        root.setPrefSize(540, 480);

        Label heading = new Label("⚙ Select Visible Databank Columns");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        heading.setTextFill(Color.web("#00e5ff"));

        Label subLabel = new Label("Choose which metrics & columns to display in strategy databank tables:");
        subLabel.setStyle("-fx-text-fill: #7e889a;");

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);
        grid.setPadding(new Insets(10, 0, 10, 0));

        Set<DatabankColumn> currentVisible = getVisibleColumns();
        Map<DatabankColumn, CheckBox> checkMap = new LinkedHashMap<>();

        int colIdx = 0;
        int rowIdx = 0;
        for (DatabankColumn col : DatabankColumn.values()) {
            CheckBox cb = new CheckBox(col.getLabel());
            cb.setStyle("-fx-text-fill: #e6e9f0; -fx-font-weight: bold;");
            cb.setSelected(currentVisible.contains(col));
            checkMap.put(col, cb);

            grid.add(cb, colIdx, rowIdx);
            rowIdx++;
            if (rowIdx >= 10) {
                rowIdx = 0;
                colIdx++;
            }
        }

        HBox toolBar = new HBox(10);
        Button selectAllBtn = new Button("Select All");
        selectAllBtn.getStyleClass().add("button");
        selectAllBtn.setOnAction(e -> checkMap.values().forEach(cb -> cb.setSelected(true)));

        Button resetBtn = new Button("Reset Defaults");
        resetBtn.getStyleClass().add("button");
        resetBtn.setOnAction(e -> {
            for (Map.Entry<DatabankColumn, CheckBox> entry : checkMap.entrySet()) {
                entry.getValue().setSelected(entry.getKey().isDefaultVisible());
            }
        });

        toolBar.getChildren().addAll(selectAllBtn, resetBtn);

        HBox btnBox = new HBox(12);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        Button applyBtn = new Button("💾 Save & Apply");
        applyBtn.getStyleClass().add("button-start");
        applyBtn.setOnAction(e -> {
            Set<DatabankColumn> newVisible = new LinkedHashSet<>();
            for (Map.Entry<DatabankColumn, CheckBox> entry : checkMap.entrySet()) {
                if (entry.getValue().isSelected()) {
                    newVisible.add(entry.getKey());
                }
            }
            if (newVisible.isEmpty()) {
                newVisible.add(DatabankColumn.NAME);
                newVisible.add(DatabankColumn.PASS);
            }
            saveVisibleColumns(newVisible);
            stage.close();
            if (onApplied != null) onApplied.run();
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("button-cancel");
        cancelBtn.setOnAction(e -> stage.close());

        btnBox.getChildren().addAll(cancelBtn, applyBtn);

        root.getChildren().addAll(heading, subLabel, toolBar, new Separator(), grid, new Separator(), btnBox);

        Scene scene = new Scene(root);
        if (owner != null && owner.getScene() != null && !owner.getScene().getStylesheets().isEmpty()) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        stage.setScene(scene);
        stage.showAndWait();
        return true;
    }
}
