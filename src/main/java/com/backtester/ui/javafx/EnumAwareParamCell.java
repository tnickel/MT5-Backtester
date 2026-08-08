package com.backtester.ui.javafx;

import com.backtester.config.EaParameter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.util.Callback;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom TableCell that commits edited text immediately when focus is lost or Enter is pressed.
 * It also supports dropdown (ComboBox) selection for boolean fields and the "typeposition" parameter enum.
 */
public class EnumAwareParamCell extends TableCell<EaParameter, String> {
    private static final Map<String, List<String>> KNOWN_ENUMS = new HashMap<>();
    static {
        KNOWN_ENUMS.put("typeposition", Arrays.asList("Buy & Sell", "Buy Only", "Sell Only"));
    }

    private ComboBox<String> comboBox;
    private TextField textField;

    public static Callback<TableColumn<EaParameter, String>, TableCell<EaParameter, String>> forTableColumn() {
        return col -> new EnumAwareParamCell();
    }

    @Override
    public void startEdit() {
        EaParameter param = getTableRow() != null ? getTableRow().getItem() : null;
        if (param != null && param.isSectionHeader()) {
            return;
        }
        if (!isEmpty()) {
            super.startEdit();
            String lowerName = param != null && param.getName() != null ? param.getName().toLowerCase() : "";
            
            String currentValue = getItem() != null ? getItem().toLowerCase().trim() : "";
            boolean isBool = "true".equals(currentValue) || "false".equals(currentValue);
            
            if (KNOWN_ENUMS.containsKey(lowerName) || isBool) {
                List<String> options = KNOWN_ENUMS.containsKey(lowerName) ? 
                    KNOWN_ENUMS.get(lowerName) : Arrays.asList("false", "true");
                    
                comboBox = new ComboBox<>(FXCollections.observableArrayList(options));
                
                String v = getItem();
                if (isBool) {
                    comboBox.setValue(v != null ? v.toLowerCase() : "false");
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
                // Ensure ComboBox shows up open
                Platform.runLater(() -> {
                    if (isEditing() && comboBox != null) {
                        comboBox.show();
                    }
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
        } else {
            if (isEditing()) {
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
    }
    
    private String getDisplayText(String val) {
        if (getTableRow() != null && getTableRow().getItem() != null) {
            String lowerName = getTableRow().getItem().getName().toLowerCase();
            if (KNOWN_ENUMS.containsKey(lowerName)) {
                try {
                    int idx = Integer.parseInt(val != null ? val.trim() : "0");
                    List<String> options = KNOWN_ENUMS.get(lowerName);
                    if (idx >= 0 && idx < options.size()) {
                        return options.get(idx);
                    }
                } catch (Exception e) {}
            }
        }
        return val;
    }
}
