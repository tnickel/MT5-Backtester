package com.backtester.ui.javafx;

import com.backtester.report.OptimizationResult.CombinedPass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Static evaluation / robustness helpers used by {@link StrategyEvaluatorDialog}
 * and other UI surfaces. Prefer calling via the thin forwarders on
 * {@link StrategyEvaluatorDialog} so existing call sites stay stable.
 */
public final class StrategyEvaluatorMetrics {

    private StrategyEvaluatorMetrics() {}

    public static double calculateRobustnessIndex(CombinedPass cp) {
        return calculateRobustnessIndex(cp, 80);
    }

    public static double calculateRobustnessIndex(CombinedPass cp, int referenceTrades) {
        double rfBt = cp.getBtRecovery();
        if (Double.isNaN(rfBt) || rfBt <= 0) {
            rfBt = 0.0;
        }

        // 1. Trade-Anzahl Faktor (Je mehr Trades, desto besser. Sigmoid/Exponential-Annäherung)
        int tradesBt = cp.getBtTrades();
        double wTrades = 1.0 - Math.exp(-tradesBt / (double) referenceTrades);

        // 2. Konsistenz-Faktor (Backtest vs Forward)
        double wConsistency = 1.0;
        if (cp.getForwardPass() != null) {
            double rfFw = cp.getFwRecovery();
            if (Double.isNaN(rfFw) || rfFw <= 0) {
                wConsistency = 0.0; // Verlust im Forward ist ein K.O.-Kriterium
            } else {
                double ratio = rfFw / rfBt;
                if (ratio >= 0.95) {
                    wConsistency = 1.0; // Forward ist ungefähr gleich oder besser -> Kein Abzug
                } else {
                    wConsistency = ratio; // Linearer Abzug bei schlechterem Forward
                }
            }
        } else {
            // Kein Forward-Test vorhanden -> milder Abzug, da unbestätigt
            wConsistency = 0.7;
        }

        // Berechne finalen Score: Recovery-Factor * Trades-Gewicht * Konsistenz-Gewicht
        double ri = rfBt * wTrades * wConsistency;
        if (Double.isNaN(ri) || Double.isInfinite(ri) || ri < 0) {
            ri = 0.0;
        }
        return ri;
    }

    public static StrategyEvaluatorDialog.Evaluation evaluatePass(CombinedPass cp) {
        return evaluatePass(cp, 80, 500.0);
    }

    public static StrategyEvaluatorDialog.Evaluation evaluatePass(CombinedPass cp, int referenceTrades) {
        return evaluatePass(cp, referenceTrades, 500.0);
    }

    public static StrategyEvaluatorDialog.Evaluation evaluatePass(CombinedPass cp, int referenceTrades, double referenceProfit) {
        double btProfit = cp.getBtProfit();
        double fwProfit = cp.getFwProfit();
        int btTrades = cp.getBtTrades();
        int fwTrades = cp.getFwTrades();
        double btDd = cp.getBtDd();
        double fwDd = cp.getFwDd();
        double consistency = cp.getConsistency();
        double btPf = cp.getBtPf();
        double fwPf = cp.getFwPf();

        double ri = calculateRobustnessIndex(cp, referenceTrades);

        // 1. Statistische Relevanz prüfen (Geringe Tradezahl)
        if (btTrades < 10 || fwTrades < 10) {
            return new StrategyEvaluatorDialog.Evaluation("BAD", String.format(Locale.US, "❌ Statistische Irrelevanz (RI: %.2f - zu wenig Trades)", ri), "#ff5252");
        }

        // 2. Extremes Risiko prüfen (Drawdown)
        if (btDd > 50.0 || fwDd > 50.0) {
            return new StrategyEvaluatorDialog.Evaluation("BAD", String.format(Locale.US, "❌ Klippen-Risiko (RI: %.2f): Extrem hoher Drawdown (>50%%)", ri), "#ff5252");
        }

        // 3. Verlustreiche Läufe
        if (btProfit <= 0 || fwProfit <= 0) {
            return new StrategyEvaluatorDialog.Evaluation("BAD", String.format(Locale.US, "❌ Nicht profitabel im Back- oder Forward (RI: %.2f)", ri), "#ff5252");
        }

        // 4. Warnung bei mäßiger Tradeanzahl
        if (btTrades < 40 || fwTrades < 40) {
            if (btDd <= 10.0 && fwDd <= 10.0 && consistency >= 1.0) {
                return new StrategyEvaluatorDialog.Evaluation("WARNING", String.format(Locale.US, "⚠️ Gute Konsistenz, aber geringe Tradeanzahl (RI: %.2f)", ri), "#ffd740");
            }
            return new StrategyEvaluatorDialog.Evaluation("WARNING", String.format(Locale.US, "⚠️ Geringe statistische Breite (RI: %.2f)", ri), "#ffd740");
        }

        // 5. Leistungseinbruch im Forward
        if (consistency < 0.6) {
            return new StrategyEvaluatorDialog.Evaluation("WARNING", String.format(Locale.US, "⚠️ Deutlicher Leistungseinbruch im Forward (RI: %.2f)", ri), "#ffd740");
        }

        // 6. Exzellente Kandidaten (Stabilität + hoher Profit)
        if (consistency >= 1.0 && btTrades >= 80 && btDd <= 15.0 && fwDd <= 15.0 && btPf >= 1.5 && fwPf >= 1.5 && btProfit >= referenceProfit) {
            return new StrategyEvaluatorDialog.Evaluation("EXCELLENT", String.format(Locale.US, "💎 Exzellent! Stabil, geringer Drawdown & konsistent (RI: %.2f)", ri), "#00e676");
        }

        // 7. Solide Kandidaten (Stabilität + hoher Profit)
        if (consistency >= 0.8 && btTrades >= 50 && btDd <= 20.0 && fwDd <= 20.0 && btProfit >= referenceProfit * 0.6) {
            return new StrategyEvaluatorDialog.Evaluation("GOOD", String.format(Locale.US, "✅ Solide & robuste Strategie für Live-Tests (RI: %.2f)", ri), "#00e676");
        }

        // Falls zwar stabil, aber mäßiger Profit
        if (consistency >= 0.8 && btTrades >= 50 && btDd <= 20.0 && fwDd <= 20.0) {
            return new StrategyEvaluatorDialog.Evaluation("GOOD", String.format(Locale.US, "ℹ️ Stabil, aber mäßiger Profit (RI: %.2f)", ri), "#80d8ff");
        }

        return new StrategyEvaluatorDialog.Evaluation("GOOD", String.format(Locale.US, "ℹ️ Solide Performance (RI: %.2f, Detailprüfung empfohlen)", ri), "#80d8ff");
    }

    public static List<Double> generateSyntheticEquityCurve(double startBalance, double profit, int trades, double pf, int passNumber) {
        List<Double> curve = new ArrayList<>();
        curve.add(startBalance);
        if (trades <= 0) {
            return curve;
        }

        // Determine Gross Profit and Gross Loss
        double grossProfit;
        double grossLoss;
        double effectivePf = (Double.isNaN(pf) || pf <= 1.0) ? 1.5 : pf;

        if (effectivePf > 1.0) {
            grossLoss = profit / (effectivePf - 1.0);
            grossProfit = profit * effectivePf / (effectivePf - 1.0);
        } else {
            grossLoss = Math.abs(profit) * 2.0;
            grossProfit = grossLoss + profit;
        }

        // Assume a win rate of around 55%
        double winRate = 0.55;
        int wins = (int) Math.round(trades * winRate);
        if (wins < 1 && profit > 0) wins = 1;
        int losses = trades - wins;
        if (losses < 1 && profit < 0) losses = 1;
        if (wins + losses != trades) {
            losses = trades - wins;
        }

        double avgWin = wins > 0 ? grossProfit / wins : 0;
        double avgLoss = losses > 0 ? grossLoss / losses : 0;

        List<Double> tradeOutputs = new ArrayList<>();
        for (int i = 0; i < wins; i++) {
            tradeOutputs.add(avgWin);
        }
        for (int i = 0; i < losses; i++) {
            tradeOutputs.add(-avgLoss);
        }

        // Shuffle deterministically based on passNumber seed
        Random rand = new Random(passNumber * 1337L);
        Collections.shuffle(tradeOutputs, rand);

        double current = startBalance;
        for (double trade : tradeOutputs) {
            current += trade;
            curve.add(current);
        }

        // Adjust the last point slightly to make the final sum match the exact net profit
        double targetEnd = startBalance + profit;
        double currentEnd = curve.get(curve.size() - 1);
        double difference = targetEnd - currentEnd;

        if (curve.size() > 1 && Math.abs(difference) > 1e-5) {
            double stepDiff = difference / (curve.size() - 1);
            double cumulative = 0;
            for (int i = 1; i < curve.size(); i++) {
                cumulative += stepDiff;
                curve.set(i, curve.get(i) + cumulative);
            }
        }

        return curve;
    }

    public static void showRobustnessScoreExplanation(javafx.stage.Window owner) {
        Stage infoStage = new Stage();
        if (owner != null) {
            infoStage.initOwner(owner);
        }
        infoStage.initModality(Modality.APPLICATION_MODAL);
        infoStage.setTitle("Was ist der Robustness Score?");

        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: #11141d; -fx-border-color: #ffd740; -fx-border-width: 1px; -fx-border-radius: 5px;");

        Label title = new Label("Robustness Score (0-100) - Erklärung");
        title.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#ffd740"));

        Label descText = new Label(
            "Der Robustness Score ist ein von 0 bis 100 skalierter Gesamtwert, der auf 6 wesentlichen Säulen der Strategiequalität basiert. " +
            "Er prüft detailliert, ob eine Strategie robust ist oder Anzeichen von Überoptimierung (Curve-Fitting) aufweist."
        );
        descText.setWrapText(true);
        descText.setTextFill(Color.web("#e6e9f0"));

        Label comparisonNote = new Label(
            "⚠️ WICHTIGER UNTERSCHIED ZUM GESAMT-SCORE:\n" +
            "Der normale Gesamt-Score bewertet ausschließlich die endgültigen Kennzahlen am Schluss (Gewinn, Drawdown, etc.). " +
            "Der Robustness Score gewichtet zusätzlich Konsistenz (FW/BT), Sharpe Ratio und Stichprobengröße, " +
            "um Glückstreffer oder instabile Strategien aufzudecken."
        );
        comparisonNote.setWrapText(true);
        comparisonNote.setTextFill(Color.web("#ffd740"));
        comparisonNote.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 12));

        Label pillarsTitle = new Label("Die Säulen der Robustheit (nur echte Messdaten):");
        pillarsTitle.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 13));
        pillarsTitle.setTextFill(Color.web("#00e5ff"));

        Label pillarsText = new Label(
            "• 1. Profitabilität (BT + FW): Bewertet ROI und Profit Factor in beiden Phasen.\n" +
            "• 2. Konsistenz (FW/BT): Reproduzierbarkeit der Ergebnisse im Forward-Test.\n" +
            "• 3. Risiko-Verhältnis (Risk/Reward): Bewertet Calmar Ratio und Recovery Factor.\n" +
            "• 4. Sharpe Ratio: Von MT5 gemessene Ertragsgleichmäßigkeit (BT + FW).\n" +
            "• 5. Stichprobengröße (Sample Size): Trades und reale Testjahre.\n" +
            "• 6. FW Trade Count: Statistische Belastbarkeit der Forward-Phase."
        );
        pillarsText.setWrapText(true);
        pillarsText.setTextFill(Color.web("#e6e9f0"));

        Label evaluationTitle = new Label("Bewertungsskala:");
        evaluationTitle.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 13));
        evaluationTitle.setTextFill(Color.web("#00e5ff"));

        Label evaluationText = new Label(
            "• >= 70 (Klasse A / B): Hervorragende Robustheit, sehr gut geeignet für Live-Tests.\n" +
            "• 55 - 69 (Klasse C): Grenzwertig robuste Performance mit Schwächen.\n" +
            "• < 55 (Klasse D / F): Mangelnde Robustheit, hohe Wahrscheinlichkeit von Curve-Fitting."
        );
        evaluationText.setWrapText(true);
        evaluationText.setTextFill(Color.web("#e6e9f0"));

        Button closeBtn = new Button("Verstanden");
        closeBtn.getStyleClass().add("button");
        closeBtn.setOnAction(e -> infoStage.close());

        HBox btnBox = new HBox(closeBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        layout.getChildren().addAll(title, descText, comparisonNote, pillarsTitle, pillarsText, evaluationTitle, evaluationText, btnBox);

        ScrollPane scrollPane = new ScrollPane(layout);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: #11141d; -fx-border-color: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Scene scene = new Scene(scrollPane, 580, 580);
        try {
            scene.getStylesheets().add(StrategyEvaluatorMetrics.class.getResource("/css/antigravity.css").toExternalForm());
        } catch (Exception e) {
            // Ignore
        }
        infoStage.setScene(scene);
        infoStage.showAndWait();
    }
}
