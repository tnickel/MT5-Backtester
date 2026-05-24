package com.backtester.tools;

import com.backtester.database.DatabaseManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Diagnostics helper: dumps the saved sensitivity analysis state from the
 * SQLite database and recomputes mean / stddev / CV per parameter so the
 * stored values can be cross-checked.
 */
public class DumpOptState {
    public static void main(String[] args) {
        String[] state = DatabaseManager.getInstance().getOptimizationState();
        if (state == null) {
            System.out.println("No optimization state stored.");
            return;
        }

        if (state[2] == null || state[2].isEmpty()) {
            System.out.println("No sensitivity data stored.");
            return;
        }

        JsonArray arr = JsonParser.parseString(state[2]).getAsJsonArray();
        Gson pretty = new GsonBuilder().setPrettyPrinting().create();

        for (JsonElement el : arr) {
            JsonObject sen = el.getAsJsonObject();
            JsonObject orig = sen.getAsJsonObject("originalPass");
            JsonObject bt = orig.getAsJsonObject("backtestPass");

            int passNo = orig.get("passNumber").getAsInt();
            double baseProfit = bt.get("profit").getAsDouble();

            System.out.println("============================================================");
            System.out.printf("Pass %d  |  Base Net Profit (BT only) = %.2f%n", passNo, baseProfit);
            System.out.println("------------------------------------------------------------");

            dumpSection("BT", sen.getAsJsonObject("parameterCurves"), sen.getAsJsonObject("parameterCVs"),
                    sen.has("overallCV") ? sen.get("overallCV").getAsDouble() : Double.NaN);

            if (sen.has("parameterCurvesFw")) {
                System.out.println();
                dumpSection("FW", sen.getAsJsonObject("parameterCurvesFw"),
                        sen.getAsJsonObject("parameterCVsFw"),
                        sen.has("overallCVFw") ? sen.get("overallCVFw").getAsDouble() : Double.NaN);
            }
        }

        if (args.length > 0 && "--full".equals(args[0])) {
            System.out.println();
            System.out.println("=== FULL JSON ===");
            System.out.println(pretty.toJson(JsonParser.parseString(state[2])));
        }
    }

    private static void dumpSection(String label, JsonObject curves, JsonObject storedCVs, double storedOverall) {
        if (curves == null || curves.entrySet().isEmpty()) {
            System.out.printf("  [%s] no data%n", label);
            return;
        }
        System.out.printf("  [%s] period sweeps:%n", label);
        double maxCV = 0.0;
        for (String paramName : curves.keySet()) {
            JsonArray curve = curves.getAsJsonArray(paramName);
            int count = curve.size();
            double sum = 0.0;
            for (JsonElement p : curve) {
                sum += p.getAsJsonObject().get("profit").getAsDouble();
            }
            double mean = sum / count;

            double varSum = 0.0;
            for (JsonElement p : curve) {
                double profit = p.getAsJsonObject().get("profit").getAsDouble();
                varSum += Math.pow(profit - mean, 2);
            }
            double stdDev = Math.sqrt(varSum / count);

            double absMean = Math.abs(mean);
            double cv = absMean < 1e-9
                    ? (stdDev < 1e-9 ? 0.0 : 1000.0)
                    : (stdDev / absMean) * 100.0;

            double stored = storedCVs != null && storedCVs.has(paramName)
                    ? storedCVs.get(paramName).getAsDouble() : Double.NaN;

            System.out.printf("    %-12s  n=%d  mean=%9.2f  stdDev=%8.2f  CV=%6.3f%%   (stored=%.3f%%)%n",
                    paramName, count, mean, stdDev, cv, stored);

            if (cv > maxCV) maxCV = cv;
        }
        System.out.printf("    -> Overall CV [%s] worst=%.3f%%   stored=%.3f%%%n", label, maxCV, storedOverall);
    }
}
