package com.backtester.ui.javafx;

import com.backtester.config.EaParameter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.Callback;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Custom TableCell that commits edited text immediately when focus is lost or Enter is pressed.
 * Supports dropdowns for booleans, known enums, and ENUM_TIMEFRAMES (MT5-style labels).
 */
public class EnumAwareParamCell extends TableCell<EaParameter, String> {

    /**
     * Which EA-parameter field this cell edits. Step must stay numeric even on
     * timeframe rows (step {@code 1} is not {@code M1}).
     */
    public enum FieldKind {
        VALUE,
        START,
        STEP,
        STOP,
        /** Value-only tables (setfile preview, etc.). */
        GENERIC
    }

    private static final Map<String, List<String>> KNOWN_ENUMS = new HashMap<>();
    static {
        KNOWN_ENUMS.put("typeposition", Arrays.asList("Buy & Sell", "Buy Only", "Sell Only"));
    }

    private final FieldKind fieldKind;
    private ComboBox<String> comboBox;
    private TextField textField;

    public EnumAwareParamCell() {
        this(FieldKind.GENERIC);
    }

    public EnumAwareParamCell(FieldKind fieldKind) {
        this.fieldKind = fieldKind != null ? fieldKind : FieldKind.GENERIC;
    }

    /**
     * Resolves the chart period for PERIOD_CURRENT display: the owning table's
     * pinned value (see {@link EaParameterUiContext#CHART_PERIOD_TABLE_KEY})
     * wins; the global context is only the fallback.
     */
    private String resolveChartPeriod() {
        TableView<EaParameter> table = getTableView();
        if (table != null) {
            Object perTable = table.getProperties().get(EaParameterUiContext.CHART_PERIOD_TABLE_KEY);
            if (perTable instanceof String period) {
                return period;
            }
        }
        return EaParameterUiContext.getChartPeriod();
    }

    public static Callback<TableColumn<EaParameter, String>, TableCell<EaParameter, String>> forTableColumn() {
        return forTableColumn(FieldKind.GENERIC);
    }

    public static Callback<TableColumn<EaParameter, String>, TableCell<EaParameter, String>> forTableColumn(
            FieldKind fieldKind) {
        return col -> new EnumAwareParamCell(fieldKind);
    }

    @Override
    public void startEdit() {
        EaParameter param = getTableRow() != null ? getTableRow().getItem() : null;
        if (param != null && param.isSectionHeader()) {
            return;
        }
        if (isEmpty()) return;

        super.startEdit();
        String lowerName = param != null && param.getName() != null
                ? param.getName().toLowerCase(Locale.ROOT) : "";
        String currentValue = getItem() != null ? getItem().toLowerCase(Locale.ROOT).trim() : "";
        boolean isBool = "true".equals(currentValue) || "false".equals(currentValue);
        boolean isTimeframe = usesTimeframeLabels(param);

        if (isTimeframe) {
            String chartPeriod = resolveChartPeriod();
            List<String> options = EaParameter.timeframeDisplayOptions(chartPeriod);
            comboBox = new ComboBox<>(FXCollections.observableArrayList(options));
            comboBox.setValue(EaParameter.toTimeframeDisplay(getItem(), chartPeriod));
            comboBox.valueProperty().addListener((obs, old, newVal) -> {
                if (newVal != null) commitEdit(EaParameter.fromTimeframeDisplay(newVal));
            });
            comboBox.focusedProperty().addListener((obs, old, newVal) -> {
                if (!newVal && isEditing()) {
                    String selected = comboBox.getValue();
                    if (selected != null) commitEdit(EaParameter.fromTimeframeDisplay(selected));
                    else cancelEdit();
                }
            });
            setText(null);
            setGraphic(comboBox);
            comboBox.requestFocus();
            Platform.runLater(() -> {
                if (isEditing() && comboBox != null) comboBox.show();
            });
            return;
        }

        if (KNOWN_ENUMS.containsKey(lowerName) || isBool) {
            List<String> options = KNOWN_ENUMS.containsKey(lowerName)
                    ? KNOWN_ENUMS.get(lowerName) : Arrays.asList("false", "true");

            comboBox = new ComboBox<>(FXCollections.observableArrayList(options));

            String v = getItem();
            if (isBool) {
                comboBox.setValue(v != null ? v.toLowerCase(Locale.ROOT) : "false");
            } else {
                try {
                    int idx = Integer.parseInt(v != null ? v.trim() : "0");
                    if (idx >= 0 && idx < options.size()) {
                        comboBox.setValue(options.get(idx));
                    } else {
                        comboBox.setValue(options.get(0));
                    }
                } catch (Exception e) {
                    comboBox.setValue(options.get(0));
                }
            }

            comboBox.valueProperty().addListener((obs, old, newVal) -> {
                if (newVal != null) {
                    if (isBool) {
                        commitEdit(newVal);
                    } else {
                        int idx = options.indexOf(newVal);
                        if (idx >= 0) commitEdit(String.valueOf(idx));
                    }
                }
            });

            comboBox.focusedProperty().addListener((obs, old, newVal) -> {
                if (!newVal && isEditing()) {
                    if (isBool) {
                        commitEdit(comboBox.getValue());
                    } else {
                        int idx = options.indexOf(comboBox.getValue());
                        if (idx >= 0) commitEdit(String.valueOf(idx));
                        else cancelEdit();
                    }
                }
            });

            setText(null);
            setGraphic(comboBox);
            comboBox.requestFocus();
            Platform.runLater(() -> {
                if (isEditing() && comboBox != null) comboBox.show();
            });
        } else {
            textField = new TextField(getItem());
            textField.setOnAction(e -> commitEdit(textField.getText()));
            textField.focusedProperty().addListener((obs, old, newVal) -> {
                if (!newVal && isEditing()) {
                    commitEdit(textField.getText());
                }
            });
            textField.setText(getItem());
            setText(null);
            setGraphic(textField);
            textField.selectAll();
            textField.requestFocus();
        }
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        setText(getDisplayText(getItem()));
        setGraphic(null);
    }

    @Override
    public void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        EaParameter param = getTableRow() != null ? getTableRow().getItem() : null;
        if (empty || item == null || (param != null && param.isSectionHeader())) {
            setText(null);
            setGraphic(null);
            setStyle("");
        } else if (isEditing()) {
            if (comboBox != null) {
                setGraphic(comboBox);
            } else if (textField != null) {
                setGraphic(textField);
            }
            setText(null);
        } else {
            setText(getDisplayText(item));
            setGraphic(null);
        }
    }

    private boolean usesTimeframeLabels(EaParameter param) {
        if (param == null || !EaParameter.isTimeframeParameterName(param.getName())) {
            return false;
        }
        // Step stays a numeric increment (1), never a PERIOD label.
        return fieldKind != FieldKind.STEP;
    }

    private String getDisplayText(String val) {
        EaParameter param = getTableRow() != null ? getTableRow().getItem() : null;
        if (param == null) return val;

        if (usesTimeframeLabels(param)) {
            return EaParameter.toTimeframeDisplay(val, resolveChartPeriod());
        }

        String lowerName = param.getName() != null ? param.getName().toLowerCase(Locale.ROOT) : "";
        if (KNOWN_ENUMS.containsKey(lowerName)) {
            try {
                int idx = Integer.parseInt(val != null ? val.trim() : "0");
                List<String> options = KNOWN_ENUMS.get(lowerName);
                if (idx >= 0 && idx < options.size()) {
                    return options.get(idx);
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return val;
    }
}
