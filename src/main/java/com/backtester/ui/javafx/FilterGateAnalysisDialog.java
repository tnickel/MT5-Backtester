package com.backtester.ui.javafx;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.workflow.FilterGateAnalysisService;
import com.backtester.workflow.FilterGateAnalysisService.CohortStats;
import com.backtester.workflow.FilterGateAnalysisService.DataSource;
import com.backtester.workflow.FilterGateAnalysisService.FilterGateAnalysis;
import com.backtester.workflow.FilterGateAnalysisService.Verdict;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Visualizes filter-on vs filter-off cohorts for one optimizer workflow tile.
 */
public final class FilterGateAnalysisDialog {

    private FilterGateAnalysisDialog() {
    }

    public static void show(Window owner,
                            String taskName,
                            FilterGateAnalysis analysis,
                            List<CombinedPass> allPasses,
                            Consumer<CombinedPass> onInspectPass) {
        if (analysis == null) return;

        Stage stage = new Stage();
        stage.setTitle("Filter an/aus — " + (taskName != null ? taskName : "Optimizer"));
        stage.initModality(Modality.NONE);
        if (owner != null) stage.initOwner(owner);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: #0b0d13;");
        root.setPrefSize(1100, 820);

        VBox top = new VBox(10);
        Label title = new Label("Filter-Nutzen-Analyse");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#00e5ff"));

        Label taskLabel = new Label("Task: " + nullToDash(taskName)
                + "   ·   Gate: " + nullToDash(analysis.getGateParameter()));
        taskLabel.setStyle("-fx-text-fill: #e6e9f0; -fx-font-size: 13px;");

        Label counts = new Label(formatCounts(analysis));
        counts.setStyle("-fx-text-fill: #c5cae9; -fx-font-size: 13px; -fx-font-weight: bold;");

        VBox sourceBanner = createSourceBanner(analysis);
        top.getChildren().addAll(title, taskLabel, sourceBanner, counts);

        if (analysis.getVerdict() == Verdict.GATE_MISSING) {
            top.getChildren().add(createNoGateBanner(analysis));
        } else {
            HBox gateRow = new HBox(10);
            gateRow.setAlignment(Pos.CENTER_LEFT);
            Label gateChooserLabel = new Label("Gate-Parameter:");
            gateChooserLabel.setStyle("-fx-text-fill: #9aa4b5;");
            ComboBox<String> gateCombo = new ComboBox<>(FXCollections.observableArrayList(
                    analysis.getCandidateGateParameters().isEmpty()
                            ? List.of(analysis.getGateParameter())
                            : analysis.getCandidateGateParameters()));
            gateCombo.setValue(analysis.getGateParameter());
            gateCombo.setDisable(analysis.getCandidateGateParameters().size() <= 1);
            gateRow.getChildren().addAll(gateChooserLabel, gateCombo);
            top.getChildren().add(gateRow);

            gateCombo.setOnAction(e -> {
                String selected = gateCombo.getValue();
                if (selected == null || selected.equals(analysis.getGateParameter())) return;
                FilterGateAnalysis refreshed = FilterGateAnalysisService.analyze(
                        allPasses, selected, analysis.getDataSource(),
                        analysis.getSourcePath(), analysis.getDatabankName(),
                        FilterGateAnalysisService.DEFAULT_MIN_COHORT_SIZE,
                        FilterGateAnalysisService.DEFAULT_TOP_N,
                        FilterGateAnalysisService.DEFAULT_SCORE_MARGIN,
                        analysis.getCandidateGateParameters(),
                        analysis.getOptimizedParameterNames());
                stage.close();
                show(owner, taskName, refreshed, allPasses, onInspectPass);
            });
        }

        root.setTop(top);

        VBox center = new VBox(14);
        center.setPadding(new Insets(12, 0, 12, 0));

        Label kpiTag = new Label(analysis.isFallback() ? "[Fallback: Databank]" : "[Quelle: Optimizer-Report]");
        kpiTag.setStyle(analysis.isFallback()
                ? "-fx-text-fill: #ffab40; -fx-font-weight: bold;"
                : "-fx-text-fill: #69f0ae; -fx-font-weight: bold;");

        GridPane kpiGrid = createKpiGrid(analysis);
        HBox chartAndDecision = createChartAndDecisionRow(analysis);

        HBox tables = new HBox(12);
        tables.setFillHeight(true);
        VBox onTable = createTopTable("Filter AN — Top Passes", analysis.getOnStats(), onInspectPass);
        VBox offTable = createTopTable("Filter AUS — Top Passes", analysis.getOffStats(), onInspectPass);
        HBox.setHgrow(onTable, Priority.ALWAYS);
        HBox.setHgrow(offTable, Priority.ALWAYS);
        tables.getChildren().addAll(onTable, offTable);

        Label verdict = new Label(formatVerdict(analysis));
        verdict.setWrapText(true);
        verdict.setStyle(verdictStyle(analysis.getVerdict()));
        verdict.setPadding(new Insets(10));
        verdict.setMaxWidth(Double.MAX_VALUE);

        if (analysis.getVerdict() == Verdict.GATE_MISSING) {
            center.getChildren().addAll(kpiTag, verdict);
        } else {
            center.getChildren().addAll(kpiTag, kpiGrid, new Separator(), chartAndDecision, tables, verdict);
        }

        ScrollPane scroll = new ScrollPane(center);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #0b0d13; -fx-background-color: #0b0d13;");
        root.setCenter(scroll);

        HBox bottom = new HBox();
        bottom.setAlignment(Pos.CENTER_RIGHT);
        bottom.setPadding(new Insets(12, 0, 0, 0));
        Button close = new Button("Schließen");
        close.getStyleClass().add("button-cancel");
        close.setOnAction(e -> stage.close());
        bottom.getChildren().add(close);
        root.setBottom(bottom);

        Scene scene = new Scene(root);
        if (owner != null && owner.getScene() != null && !owner.getScene().getStylesheets().isEmpty()) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        stage.setScene(scene);
        stage.show();
    }

    private static VBox createNoGateBanner(FilterGateAnalysis analysis) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(12));
        box.setStyle(
                "-fx-background-color: rgba(33, 150, 243, 0.15); -fx-border-color: #42a5f5; "
                        + "-fx-border-width: 2; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label head = new Label("Kein An/Aus-Schalter — nur Parameter-Optimierung");
        head.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        head.setTextFill(Color.web("#64b5f6"));

        Label body = new Label(analysis.getVerdictMessage());
        body.setWrapText(true);
        body.setStyle("-fx-text-fill: #bbdefb; -fx-font-size: 12px;");

        Label note = new Label(
                "AN/AUS-Vergleich entfällt. Die Passes unten (falls sichtbar) bleiben „Unbekannt“, "
                        + "weil es nichts zum Ein-/Ausschalten gab.");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill: #90caf9; -fx-font-size: 12px;");

        box.getChildren().addAll(head, body, note);
        return box;
    }

    private static VBox createSourceBanner(FilterGateAnalysis analysis) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(10, 12, 10, 12));
        if (analysis.getDataSource() == DataSource.DATABANK_FALLBACK) {
            box.setStyle(
                    "-fx-background-color: rgba(255, 171, 64, 0.18); -fx-border-color: #ffab40; "
                            + "-fx-border-width: 2; -fx-border-radius: 6; -fx-background-radius: 6;");
            Label head = new Label("FALLBACK: Databank");
            head.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
            head.setTextFill(Color.web("#ffab40"));
            String db = analysis.getDatabankName() == null || analysis.getDatabankName().isBlank()
                    ? "—" : analysis.getDatabankName();
            Label body = new Label(
                    "Kein vollständiger Optimizer-Report gefunden — Auswertung auf Ziel-Databank „"
                            + db + "“ (möglicherweise gefiltert). Ergebnis kann verzerrt sein.");
            body.setWrapText(true);
            body.setStyle("-fx-text-fill: #ffe0b2; -fx-font-size: 12px;");
            box.getChildren().addAll(head, body);
        } else {
            box.setStyle(
                    "-fx-background-color: rgba(0, 230, 118, 0.08); -fx-border-color: #00e676; "
                            + "-fx-border-width: 1.5; -fx-border-radius: 6; -fx-background-radius: 6;");
            Label head = new Label("Quelle: Optimizer-Report");
            head.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
            head.setTextFill(Color.web("#00e676"));
            Label body = new Label(nullToDash(analysis.getSourcePath()));
            body.setWrapText(true);
            body.setStyle("-fx-text-fill: #c8e6c9; -fx-font-size: 12px;");
            box.getChildren().addAll(head, body);
        }
        return box;
    }

    private static GridPane createKpiGrid(FilterGateAnalysis analysis) {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(8);
        grid.setPadding(new Insets(8));
        grid.setStyle("-fx-background-color: rgba(26, 30, 40, 0.85); -fx-border-color: #2e3545; -fx-border-radius: 6;");

        addHeader(grid, 0, "");
        addHeader(grid, 1, "Filter AN");
        addHeader(grid, 2, "Filter AUS");
        addHeader(grid, 3, "Delta (AN−AUS)");

        CohortStats on = analysis.getOnStats();
        CohortStats off = analysis.getOffStats();
        int row = 1;
        row = addMetricRow(grid, row, "Anzahl",
                Integer.toString(on.getCount()), Integer.toString(off.getCount()),
                Integer.toString(on.getCount() - off.getCount()));
        row = addMetricRow(grid, row, "Median Score",
                fmt(on.getMedianScore()), fmt(off.getMedianScore()),
                fmt(delta(on.getMedianScore(), off.getMedianScore())));
        row = addMetricRow(grid, row, "Median Profit",
                fmt(on.getMedianProfit()), fmt(off.getMedianProfit()),
                fmt(delta(on.getMedianProfit(), off.getMedianProfit())));
        row = addMetricRow(grid, row, "Median MaxDD %",
                fmt(on.getMedianDrawdownPct()), fmt(off.getMedianDrawdownPct()),
                fmt(delta(on.getMedianDrawdownPct(), off.getMedianDrawdownPct())));
        addMetricRow(grid, row, "Median Trades",
                fmt(on.getMedianTrades()), fmt(off.getMedianTrades()),
                fmt(delta(on.getMedianTrades(), off.getMedianTrades())));
        return grid;
    }

    /** Shared row for the standalone Filter-Nutzen dialog (wide layout). */
    static HBox createChartAndDecisionRow(FilterGateAnalysis analysis) {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setFillHeight(true);

        BarChart<String, Number> chart = createScoreChart(analysis);
        chart.setMinWidth(420);
        chart.setPrefWidth(480);
        HBox.setHgrow(chart, Priority.ALWAYS);

        VBox decision = createNextStepDecisionPanel(analysis);
        decision.setMinWidth(420);
        decision.setPrefWidth(480);
        HBox.setHgrow(decision, Priority.ALWAYS);

        row.getChildren().addAll(chart, decision);
        return row;
    }

    /** Vertical stack for embedding in the Optimizer-Setting companion (right pane). */
    static VBox createChartAndDecisionColumn(FilterGateAnalysis analysis) {
        VBox col = new VBox(12);
        col.setPadding(new Insets(4));
        BarChart<String, Number> chart = createScoreChart(analysis);
        chart.setMinWidth(280);
        chart.setPrefWidth(420);
        chart.setMaxWidth(Double.MAX_VALUE);
        VBox decision = createNextStepDecisionPanel(analysis);
        decision.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(decision, Priority.ALWAYS);
        col.getChildren().addAll(chart, decision);
        return col;
    }

    private static VBox createNextStepDecisionPanel(FilterGateAnalysis analysis) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        box.setStyle(
                "-fx-background-color: rgba(26, 30, 40, 0.95); -fx-border-color: #2e3545; "
                        + "-fx-border-width: 1.5; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label title = new Label("Extrakt & Entscheidung für nächsten Step");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        title.setTextFill(Color.web("#00e5ff"));

        Label gate = new Label("Gate: " + nullToDash(analysis.getGateParameter()));
        gate.setStyle("-fx-text-fill: #c5cae9; -fx-font-size: 12px;");

        CohortStats on = analysis.getOnStats();
        CohortStats off = analysis.getOffStats();
        Label extract = new Label(String.format(Locale.ROOT,
                "AN %d Passes · Median-Score %.2f · Profit %.2f · DD %.2f%%%n"
                        + "AUS %d Passes · Median-Score %.2f · Profit %.2f · DD %.2f%%%n"
                        + "Top %d: %d mit Filter an",
                on.getCount(), on.getMedianScore(), on.getMedianProfit(), on.getMedianDrawdownPct(),
                off.getCount(), off.getMedianScore(), off.getMedianProfit(), off.getMedianDrawdownPct(),
                analysis.getTopNTotal(), analysis.getTopNOnCount()));
        extract.setWrapText(true);
        extract.setStyle("-fx-text-fill: #e6e9f0; -fx-font-size: 12px;");

        NextStepDecision decision = decideNextStepFilter(analysis);

        Label decisionBadge = new Label(decision.badgeText());
        decisionBadge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        decisionBadge.setTextFill(Color.web(decision.color()));
        decisionBadge.setPadding(new Insets(10, 14, 10, 14));
        decisionBadge.setMaxWidth(Double.MAX_VALUE);
        decisionBadge.setAlignment(Pos.CENTER);
        decisionBadge.setStyle(
                "-fx-background-color: " + decision.background() + "; -fx-border-color: " + decision.color()
                        + "; -fx-border-width: 2; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label advice = new Label(decision.adviceText(analysis.getGateParameter()));
        advice.setWrapText(true);
        advice.setStyle("-fx-text-fill: #cfd8dc; -fx-font-size: 12px;");

        box.getChildren().addAll(title, gate, extract, decisionBadge, advice);
        VBox.setVgrow(advice, Priority.ALWAYS);
        return box;
    }

    /**
     * Concrete recommendation for the next guided step: fix the Use_* gate on or off.
     */
    static NextStepDecision decideNextStepFilter(FilterGateAnalysis analysis) {
        if (analysis == null || analysis.getVerdict() == null) {
            return NextStepDecision.unclear("Keine Analyse vorhanden.");
        }
        return switch (analysis.getVerdict()) {
            case FILTER_ON_BETTER -> NextStepDecision.on(
                    "Median-Score mit Filter an liegt klar über Filter aus. "
                            + "Für den nächsten Step den Gate-Parameter auf true setzen.");
            case FILTER_OFF_BETTER -> NextStepDecision.off(
                    "Median-Score mit Filter aus liegt klar über Filter an. "
                            + "Für den nächsten Step den Gate-Parameter auf false setzen.");
            case INSUFFICIENT_DATA -> NextStepDecision.unclear(
                    "Zu wenig Passes je Kohorte — keine belastbare An/Aus-Vorgabe für den nächsten Step.");
            case GATE_MISSING -> NextStepDecision.unclear(
                    "Kein Use_*-Schalter in dieser Stufe — keine An/Aus-Entscheidung nötig.");
            case UNCLEAR -> {
                // Soft lean by median score when margins are small, still marked as unclear.
                double on = analysis.getOnStats().getMedianScore();
                double off = analysis.getOffStats().getMedianScore();
                if (Double.isFinite(on) && Double.isFinite(off) && on != off) {
                    String lean = on > off
                            ? "Leichte Tendenz zu Filter AN (nicht signifikant)."
                            : "Leichte Tendenz zu Filter AUS (nicht signifikant).";
                    yield NextStepDecision.unclear(
                            "Kein klarer Vorteil. " + lean
                                    + " Gate vorerst unverändert lassen oder per Hand-Pick entscheiden.");
                }
                yield NextStepDecision.unclear(
                        "Kein klarer Vorteil. Gate vorerst unverändert lassen oder per Hand-Pick entscheiden.");
            }
        };
    }

    record NextStepDecision(String badgeText, String color, String background, String detail) {
        static NextStepDecision on(String detail) {
            return new NextStepDecision("NÄCHSTER STEP: FILTER AN (true)",
                    "#00e676", "rgba(0, 230, 118, 0.12)", detail);
        }

        static NextStepDecision off(String detail) {
            return new NextStepDecision("NÄCHSTER STEP: FILTER AUS (false)",
                    "#ffab40", "rgba(255, 171, 64, 0.14)", detail);
        }

        static NextStepDecision unclear(String detail) {
            return new NextStepDecision("NÄCHSTER STEP: UNKLAR — kein Zwang",
                    "#90a4ae", "rgba(144, 164, 174, 0.12)", detail);
        }

        String adviceText(String gateParameter) {
            String gate = gateParameter == null || gateParameter.isBlank() ? "Use_*-Gate" : gateParameter;
            return detail + "\nParameter: " + gate;
        }
    }

    private static BarChart<String, Number> createScoreChart(FilterGateAnalysis analysis) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setLabel("Median Score");
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setTitle("Median-Score: Filter an vs aus");
        chart.setLegendVisible(false);
        chart.setMinHeight(180);
        chart.setPrefHeight(200);
        chart.setMaxHeight(220);
        chart.setStyle("-fx-background-color: transparent;");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().add(new XYChart.Data<>("Filter AN",
                finiteOrZero(analysis.getOnStats().getMedianScore())));
        series.getData().add(new XYChart.Data<>("Filter AUS",
                finiteOrZero(analysis.getOffStats().getMedianScore())));
        chart.getData().add(series);
        return chart;
    }

    private static VBox createTopTable(String title,
                                       CohortStats stats,
                                       Consumer<CombinedPass> onInspectPass) {
        VBox box = new VBox(6);
        Label label = new Label(title);
        label.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold;");

        List<CombinedPass> topPasses = stats != null ? stats.getTopByScore() : List.of();
        ObservableList<PassRow> rows = FXCollections.observableArrayList();
        for (CombinedPass pass : topPasses) {
            if (pass == null) continue;
            rows.add(new PassRow(
                    Integer.toString(pass.getPassNumber()),
                    fmt(pass.getScore()),
                    fmt(pass.getBtProfit()),
                    pass));
        }

        TableView<PassRow> table = new TableView<>(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setMinHeight(220);
        table.setPrefHeight(260);
        table.setFixedCellSize(28);
        table.setPlaceholder(new Label(rows.isEmpty() ? "Keine Top-Passes in dieser Kohorte" : ""));
        table.setStyle("-fx-background-color: #121722; -fx-control-inner-background: #121722; -fx-text-fill: #e6e9f0;");

        TableColumn<PassRow, String> passCol = new TableColumn<>("Pass");
        passCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().passNumber()));
        TableColumn<PassRow, String> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().score()));
        TableColumn<PassRow, String> profitCol = new TableColumn<>("Profit");
        profitCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().profit()));
        table.getColumns().setAll(passCol, scoreCol, profitCol);

        table.setRowFactory(tv -> {
            TableRow<PassRow> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty() && onInspectPass != null
                        && row.getItem() != null && row.getItem().pass() != null) {
                    onInspectPass.accept(row.getItem().pass());
                }
            });
            return row;
        });

        Label hint = new Label(rows.isEmpty()
                ? "—"
                : rows.size() + " Einträge · Doppelklick → Details");
        hint.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 11px;");

        box.getChildren().addAll(label, table, hint);
        return box;
    }

    private record PassRow(String passNumber, String score, String profit, CombinedPass pass) {
    }

    private static void addHeader(GridPane grid, int col, String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold;");
        grid.add(label, col, 0);
    }

    private static int addMetricRow(GridPane grid, int row, String name,
                                    String on, String off, String delta) {
        Label n = new Label(name);
        n.setStyle("-fx-text-fill: #9aa4b5;");
        Label a = new Label(on);
        a.setStyle("-fx-text-fill: #e6e9f0;");
        Label b = new Label(off);
        b.setStyle("-fx-text-fill: #e6e9f0;");
        Label d = new Label(delta);
        d.setStyle("-fx-text-fill: #ffd740; -fx-font-weight: bold;");
        grid.add(n, 0, row);
        grid.add(a, 1, row);
        grid.add(b, 2, row);
        grid.add(d, 3, row);
        return row + 1;
    }

    private static String formatCounts(FilterGateAnalysis analysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("Passes gesamt: ").append(analysis.getTotalPassCount());
        sb.append("   ·   AN: ").append(analysis.getOnStats().getCount());
        sb.append("   ·   AUS: ").append(analysis.getOffStats().getCount());
        sb.append("   ·   Unbekannt: ").append(analysis.getUnknownCount());
        if (analysis.getTopNTotal() > 0) {
            sb.append("   ·   Top ").append(analysis.getTopNTotal())
                    .append(" mit Filter an: ").append(analysis.getTopNOnCount());
        }
        if (analysis.isFallback()) {
            sb.append("   ·   [Fallback: Databank]");
        }
        return sb.toString();
    }

    private static String formatVerdict(FilterGateAnalysis analysis) {
        if (analysis.getVerdict() == Verdict.GATE_MISSING) {
            return "Urteil: Kein An/Aus-Vergleich möglich — in dieser Stufe wurde nur optimiert, "
                    + "kein Filter-Schalter (Use_*) vorhanden.";
        }
        String prefix = switch (analysis.getVerdict()) {
            case FILTER_ON_BETTER -> "Urteil: Filter AN besser. ";
            case FILTER_OFF_BETTER -> "Urteil: Filter AUS besser. ";
            case INSUFFICIENT_DATA -> "Urteil: Daten unzureichend. ";
            case GATE_MISSING -> "Urteil: Gate fehlt. ";
            default -> "Urteil: unklar. ";
        };
        return prefix + analysis.getVerdictMessage();
    }

    private static String verdictStyle(Verdict verdict) {
        String color = switch (verdict) {
            case FILTER_ON_BETTER -> "#00e676";
            case FILTER_OFF_BETTER -> "#ffab40";
            case INSUFFICIENT_DATA, GATE_MISSING -> "#ff5252";
            default -> "#90a4ae";
        };
        return "-fx-background-color: rgba(26,30,40,0.9); -fx-border-color: " + color
                + "; -fx-border-width: 2; -fx-border-radius: 6; -fx-background-radius: 6;"
                + " -fx-text-fill: " + color + "; -fx-font-size: 13px; -fx-font-weight: bold;";
    }

    private static String fmt(double value) {
        if (!Double.isFinite(value)) return "—";
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static double delta(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b)) return Double.NaN;
        return a - b;
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
