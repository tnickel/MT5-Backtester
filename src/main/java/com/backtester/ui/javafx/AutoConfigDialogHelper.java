package com.backtester.ui.javafx;

import com.backtester.config.EaParameter;
import javafx.scene.control.Alert;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Window;
import java.util.Optional;

/**
 * Helper class for asking the user for a percentage variation,
 * and performing AutoConfig updates symmetrically around the current parameter values.
 */
public class AutoConfigDialogHelper {

    public static void showAutoConfigDialog(
            TableView<EaParameter> paramTable,
            LogView logView,
            Window owner,
            Runnable postAction
    ) {
        if (paramTable.getItems().isEmpty()) {
            logView.log("WARN", "No parameters loaded. Please select an EA first.");
            return;
        }

        TextInputDialog inputDialog = new TextInputDialog("10");
        inputDialog.setTitle("AutoConfig Variation");
        inputDialog.setHeaderText("Um wie viel Prozent sollen die Werte variieren?");
        inputDialog.setContentText("Prozentsatz (%):");
        if (owner != null) {
            inputDialog.initOwner(owner);
        }

        // Style the dialog to match the sci-fi dark theme
        try {
            inputDialog.getDialogPane().setStyle("-fx-background-color: #0b0d13;");
            if (inputDialog.getDialogPane().lookup(".content.label") != null) {
                inputDialog.getDialogPane().lookup(".content.label").setStyle("-fx-text-fill: #e6e9f0;");
            }
            if (inputDialog.getDialogPane().lookup(".header-panel") != null) {
                inputDialog.getDialogPane().lookup(".header-panel").setStyle("-fx-background-color: #0b0d13;");
            }
            if (inputDialog.getDialogPane().lookup(".header-panel") != null &&
                inputDialog.getDialogPane().lookup(".header-panel").lookup(".label") != null) {
                inputDialog.getDialogPane().lookup(".header-panel").lookup(".label").setStyle("-fx-text-fill: #00e5ff;");
            }
            if (owner != null && owner.getScene() != null && !owner.getScene().getStylesheets().isEmpty()) {
                inputDialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
            }
        } catch (Exception e) {
            // Ignore styling errors to prevent crashes if CSS classes change
        }

        Optional<String> result = inputDialog.showAndWait();
        if (!result.isPresent()) {
            return; // User cancelled
        }

        String inputVal = result.get().trim().replace("%", "");
        double percent;
        try {
            percent = Double.parseDouble(inputVal);
            if (percent <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Ungültiger Prozentsatz. Bitte geben Sie eine positive Zahl ein.");
            if (owner != null) {
                alert.initOwner(owner);
            }
            alert.show();
            return;
        }

        int activated = 0;
        int skipped = 0;

        for (EaParameter param : paramTable.getItems()) {
            String name = param.getName();
            String value = param.getValue();

            if (isExcludedParameterName(name) || !isNumericValue(value)) {
                param.setOptimizeEnabled(false);
                skipped++;
                continue;
            }

            double[] range = calculateOptRangeWithPercent(name, value, percent);
            if (range == null) {
                param.setOptimizeEnabled(false);
                skipped++;
                continue;
            }

            param.setOptimizeEnabled(true);
            param.setOptimizeStart(formatNumber(range[0]));
            param.setOptimizeStep(formatNumber(range[1]));
            param.setOptimizeEnd(formatNumber(range[2]));
            activated++;
        }

        paramTable.refresh();
        logView.log("INFO", "AutoConfig applied (variation: " + percent + "%): " + activated + " enabled, " + skipped + " skipped.");

        if (postAction != null) {
            postAction.run();
        }
    }

    private static boolean isExcludedParameterName(String name) {
        String lower = name.toLowerCase();
        return lower.contains("magic") || lower.contains("slippage") || lower.contains("comment") || lower.contains("color");
    }

    private static boolean isNumericValue(String value) {
        if (value == null || value.isEmpty() || value.contains(":") || value.contains(",")) return false;
        try { Double.parseDouble(value); return true; } catch (NumberFormatException e) { return false; }
    }

    private static double[] calculateOptRangeWithPercent(String name, String currentValue, double percent) {
        double current;
        try { current = Double.parseDouble(currentValue); } catch (NumberFormatException e) { return null; }

        if (current == 0) {
            return null; // Percentage variation of 0 is not defined / useful here
        }

        int decimalPlaces = 0;
        if (currentValue.contains(".")) {
            decimalPlaces = currentValue.length() - currentValue.indexOf('.') - 1;
        }

        double variation = Math.abs(current * (percent / 100.0));
        double start = current - variation;
        double end = current + variation;

        // We want about 10 steps total across the full range (i.e. step = variation / 5.0)
        double rawStep = variation / 5.0;

        double step;
        if (decimalPlaces == 0) {
            // Integer parameter: keep everything as integers, minimum step size of 1
            step = Math.max(1.0, Math.round(rawStep));
            start = Math.round(start);
            end = Math.round(end);
        } else {
            // Double parameter: keep precision to the same decimal places
            double minStep = Math.pow(10, -decimalPlaces);
            step = Math.max(minStep, roundTo(rawStep, decimalPlaces));
            start = roundTo(start, decimalPlaces);
            end = roundTo(end, decimalPlaces);
        }

        if (start > end) {
            double temp = start;
            start = end;
            end = temp;
        }

        // --- Safety bounds to prevent MT5 EA OnInit failures ---
        String lowerName = name.toLowerCase();
        if (lowerName.contains("timeframe")) {
            return null; // Skip raw ENUM_TIMEFRAMES integers
        }

        if (lowerName.contains("multiplier") || lowerName.contains("step_multiplier") || lowerName.contains("next_lot_multiplier")) {
            if (start < 1.0) start = 1.0;
        }

        if (lowerName.contains("lot") && !lowerName.contains("multiplier") && !lowerName.contains("wait")) {
            if (start < 0.01) start = roundTo(0.01, decimalPlaces);
        }

        if (start < 0 && (lowerName.contains("step") || lowerName.contains("points") || lowerName.contains("period") || lowerName.contains("profit") || lowerName.contains("bar") || lowerName.contains("hour") || lowerName.contains("level"))) {
            start = 0;
        }

        return new double[]{start, step, end};
    }

    private static double roundTo(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        java.math.BigDecimal bd;
        try {
            bd = new java.math.BigDecimal(Double.toString(value));
        } catch (NumberFormatException e) {
            bd = new java.math.BigDecimal(value);
        }
        bd = bd.setScale(places, java.math.RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private static String formatNumber(double value) {
        if (value == (long) value) return String.format(java.util.Locale.US, "%d", (long) value);
        else return String.format(java.util.Locale.US, "%s", value);
    }
}
