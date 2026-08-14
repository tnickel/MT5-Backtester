package com.backtester.ui.javafx;

import com.backtester.config.EaParameter;
import javafx.geometry.Pos;
import javafx.scene.control.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to configure JavaFX TableView for EaParameter lists,
 * ensuring section header rows are rendered visually like MetaTrader Strategy Tester inputs
 * (folder icon, bold cyan text, shaded background, no checkboxes or editable values).
 */
public class EaParameterTableHelper {

    private static final Logger log = LoggerFactory.getLogger(EaParameterTableHelper.class);

    /**
     * Configures table row factory and column cell factories for MetaTrader-style parameter display.
     */
    public static void configureTable(
            TableView<EaParameter> paramTable,
            TableColumn<EaParameter, Boolean> optCol,
            TableColumn<EaParameter, String> nameCol,
            TableColumn<EaParameter, String> valCol,
            TableColumn<EaParameter, String> startCol,
            TableColumn<EaParameter, String> stepCol,
            TableColumn<EaParameter, String> stopCol,
            Runnable onParamChanged) {

        log.info("=== [SECTION-HEADER-LOG] EaParameterTableHelper.configureTable invoked! ===");

        // Custom RowFactory: highlight section header rows & invalid search space rows
        paramTable.setRowFactory(tv -> new TableRow<EaParameter>() {
            private final Tooltip errorTooltip = new Tooltip();
            @Override
            protected void updateItem(EaParameter item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                    setTooltip(null);
                    getStyleClass().remove("section-header-row");
                } else if (item.isSectionHeader()) {
                    log.info("=== [SECTION-HEADER-LOG] TableRow rendering section header: name='{}', title='{}' ===", item.getName(), item.getFormattedSectionTitle());
                    setStyle("-fx-background-color: #152238; -fx-font-weight: bold;");
                    setTooltip(null);
                    if (!getStyleClass().contains("section-header-row")) {
                        getStyleClass().add("section-header-row");
                    }
                } else {
                    String error = item.getSearchSpaceValidationError(null);
                    if (error != null) {
                        setStyle("-fx-background-color: rgba(255, 23, 68, 0.35);");
                        errorTooltip.setText("Fehler: " + error);
                        setTooltip(errorTooltip);
                        getStyleClass().remove("section-header-row");
                    } else {
                        setStyle("");
                        setTooltip(null);
                        getStyleClass().remove("section-header-row");
                    }
                }
            }
        });

        // Opt column: hide checkbox on section headers
        if (optCol != null) {
            optCol.setCellFactory(tc -> new TableCell<EaParameter, Boolean>() {
                private final CheckBox checkBox = new CheckBox();
                {
                    checkBox.setOnAction(e -> {
                        EaParameter param = getTableRow() != null ? getTableRow().getItem() : null;
                        if (param != null && !param.isSectionHeader()) {
                            param.setOptimizeEnabled(checkBox.isSelected());
                            paramTable.refresh();
                            if (onParamChanged != null) {
                                onParamChanged.run();
                            }
                        }
                    });
                    setAlignment(Pos.CENTER);
                }
                @Override
                protected void updateItem(Boolean item, boolean empty) {
                    super.updateItem(item, empty);
                    EaParameter param = getTableRow() != null ? getTableRow().getItem() : null;
                    if (empty || item == null || (param != null && param.isSectionHeader())) {
                        setGraphic(null);
                        setText(null);
                    } else {
                        checkBox.setSelected(item != null && item);
                        setGraphic(checkBox);
                    }
                }
            });
        }

        // Variable column: format section header title with folder icon and cyan text
        if (nameCol != null) {
            nameCol.setCellFactory(column -> new TableCell<EaParameter, String>() {
                private final Tooltip tooltip = new Tooltip();
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    EaParameter param = getTableRow() != null ? getTableRow().getItem() : null;
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setTooltip(null);
                        setStyle("");
                    } else if (param != null && param.isSectionHeader()) {
                        setText(param.getFormattedSectionTitle());
                        setStyle("-fx-font-weight: bold; -fx-text-fill: #00e5ff; -fx-font-size: 12px;");
                        setTooltip(null);
                        setGraphic(null);
                    } else {
                        setText(item);
                        setStyle("");
                        if (param != null) {
                            tooltip.setText("Variable: " + param.getName());
                            setTooltip(tooltip);
                        } else {
                            setTooltip(null);
                        }
                        setGraphic(null);
                    }
                }
            });
        }

        // Value & Range columns: clear text and disable editing for section headers.
        // Timeframe labels for Value/Start/Stop; Step stays numeric (1 ≠ M1).
        if (valCol != null) {
            valCol.setCellFactory(EnumAwareParamCell.forTableColumn(EnumAwareParamCell.FieldKind.VALUE));
        }
        if (startCol != null) {
            startCol.setCellFactory(EnumAwareParamCell.forTableColumn(EnumAwareParamCell.FieldKind.START));
        }
        if (stepCol != null) {
            stepCol.setCellFactory(EnumAwareParamCell.forTableColumn(EnumAwareParamCell.FieldKind.STEP));
        }
        if (stopCol != null) {
            stopCol.setCellFactory(EnumAwareParamCell.forTableColumn(EnumAwareParamCell.FieldKind.STOP));
        }
    }

    public static void configureTable(
            TableView<EaParameter> paramTable,
            TableColumn<EaParameter, Boolean> optCol,
            TableColumn<EaParameter, String> nameCol,
            TableColumn<EaParameter, String> valCol,
            TableColumn<EaParameter, String> startCol,
            TableColumn<EaParameter, String> stepCol,
            TableColumn<EaParameter, String> stopCol) {
        configureTable(paramTable, optCol, nameCol, valCol, startCol, stepCol, stopCol, null);
    }
}
