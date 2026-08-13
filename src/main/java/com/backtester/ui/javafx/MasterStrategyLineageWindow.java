package com.backtester.ui.javafx;

import com.backtester.report.MasterStrategyLineageReportGenerator;
import com.backtester.workflow.CustomProject;
import com.backtester.workflow.MasterStrategyEntry;
import com.backtester.workflow.MasterStrategyLineageService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Non-modal companion window showing the master strategy's measured history:
 * every hand-pick re-tested under identical reference conditions, so a chain of
 * "better" scores can be checked against actual out-of-stage results.
 *
 * <p>Left: the picks. Middle: the equity curve of the selected pick. Right: its
 * numbers with the delta against the best previous entry.
 */
public final class MasterStrategyLineageWindow {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final Stage stage;
    private final Label subtitleLabel;
    private final Label verdictBanner;
    private final CheckBox autoBacktestToggle;
    private final TableView<MasterStrategyEntry> table;
    private final ObservableList<MasterStrategyEntry> items = FXCollections.observableArrayList();
    private final LineChart<Number, Number> trendChart;
    private final VBox detailBox;
    private final VBox equityBox;

    private CustomProject project;
    private Consumer<Boolean> autoBacktestListener;
    private int confirmedMasterSequence = -1;

    private MasterStrategyLineageWindow(Window owner) {
        stage = new Stage();
        stage.setTitle("Master-Strategie-Verlauf");
        stage.initModality(Modality.NONE);
        if (owner != null) stage.initOwner(owner);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));
        root.setStyle("-fx-background-color: #0b0d13;");
        root.setPrefSize(1500, 820);

        Label title = new Label("Master-Strategie-Verlauf");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
        title.setTextFill(Color.web("#00e5ff"));

        subtitleLabel = new Label();
        subtitleLabel.setWrapText(true);
        subtitleLabel.setStyle("-fx-text-fill: #9aa4b5; -fx-font-size: 12px;");

        verdictBanner = new Label();
        verdictBanner.setWrapText(true);
        verdictBanner.setStyle("-fx-text-fill: #9aa4b5; -fx-font-size: 12px;");

        autoBacktestToggle = new CheckBox("Referenz-Backtest nach jedem Pick");
        autoBacktestToggle.setStyle("-fx-text-fill: #9aa4b5;");
        autoBacktestToggle.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (project != null) project.setReferenceBacktestEnabled(newValue);
            if (autoBacktestListener != null) autoBacktestListener.accept(newValue);
        });

        Button reportBtn = new Button("📄 Abschlussbericht");
        reportBtn.setStyle("-fx-background-color: #00e5ff; -fx-text-fill: #0b0d13; "
                + "-fx-font-weight: bold; -fx-background-radius: 5; -fx-cursor: hand;");
        reportBtn.setOnAction(e -> generateAndOpenReport());

        HBox headerRow = new HBox(16, title, autoBacktestToggle, reportBtn);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        root.setTop(new VBox(8, headerRow, subtitleLabel, verdictBanner));

        table = new TableView<>(items);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setStyle("-fx-background-color: #141822; -fx-control-inner-background: #141822; "
                + "-fx-table-cell-border-color: #232a3b; -fx-text-fill: #e6e9f0;");
        table.setPlaceholder(new Label("Noch kein Referenz-Backtest vorhanden"));
        buildColumns();
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> showEntry(n));

        trendChart = createTrendChart();
        VBox leftBox = new VBox(10, table, trendChart);
        VBox.setVgrow(table, Priority.ALWAYS);
        leftBox.setPadding(new Insets(10, 8, 0, 0));

        equityBox = new VBox(10);
        equityBox.setPadding(new Insets(10, 8, 0, 8));
        ScrollPane equityScroll = new ScrollPane(equityBox);
        equityScroll.setFitToWidth(true);
        equityScroll.setStyle("-fx-background: #0b0d13; -fx-background-color: #0b0d13;");

        detailBox = new VBox(10);
        detailBox.setPadding(new Insets(10, 0, 0, 8));
        ScrollPane detailScroll = new ScrollPane(detailBox);
        detailScroll.setFitToWidth(true);
        detailScroll.setStyle("-fx-background: #0b0d13; -fx-background-color: #0b0d13;");

        SplitPane split = new SplitPane(leftBox, equityScroll, detailScroll);
        split.setDividerPositions(0.30, 0.68);
        split.setStyle("-fx-background-color: #0b0d13;");
        root.setCenter(split);

        Scene scene = new Scene(root);
        if (owner != null && owner.getScene() != null && !owner.getScene().getStylesheets().isEmpty()) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        stage.setScene(scene);
    }

    public static MasterStrategyLineageWindow showOrRefresh(MasterStrategyLineageWindow existing,
                                                            Window owner,
                                                            CustomProject project,
                                                            Consumer<Boolean> autoBacktestListener) {
        MasterStrategyLineageWindow window = existing;
        if (window == null || window.stage == null || !window.stage.isShowing()) {
            window = new MasterStrategyLineageWindow(owner);
        }
        window.autoBacktestListener = autoBacktestListener;
        window.refresh(project);
        if (!window.stage.isShowing()) window.stage.show();
        window.stage.toFront();
        return window;
    }

    /** Reloads the list without forcing the window open. */
    public static MasterStrategyLineageWindow refreshIfOpen(MasterStrategyLineageWindow existing,
                                                            CustomProject project) {
        if (existing == null || existing.stage == null || !existing.stage.isShowing()) return existing;
        Platform.runLater(() -> existing.refresh(project));
        return existing;
    }

    public void refresh(CustomProject project) {
        this.project = project;
        List<MasterStrategyEntry> lineage = project != null
                ? project.getMasterStrategyLineage() : List.of();
        confirmedMasterSequence = project != null
                ? MasterStrategyLineageService.confirmedMasterEntry(project)
                        .map(MasterStrategyEntry::getSequence).orElse(-1)
                : -1;

        MasterStrategyEntry previouslySelected = table.getSelectionModel().getSelectedItem();
        items.setAll(lineage);
        autoBacktestToggle.setSelected(project == null || project.isReferenceBacktestEnabled());

        subtitleLabel.setText(lineage.isEmpty()
                ? "Jeder Hand-Pick wird unter festen Referenzbedingungen nachgetestet ("
                        + MasterStrategyLineageService.REFERENCE_FROM + " bis "
                        + MasterStrategyLineageService.REFERENCE_TO + ", 1 minute OHLC). "
                        + "Sobald ein Pass übernommen wurde, erscheint hier der erste Messpunkt."
                : lineage.size() + " Messpunkte · Referenzzeitraum "
                        + MasterStrategyLineageService.REFERENCE_FROM + " bis "
                        + MasterStrategyLineageService.REFERENCE_TO
                        + " · Bewertung nach Profit/Drawdown, weil Stage-Scores untereinander nicht vergleichbar sind.");

        updateVerdictBanner(lineage);
        updateTrendChart(lineage);
        table.refresh();

        if (!items.isEmpty()) {
            int index = previouslySelected != null ? indexOfSequence(previouslySelected.getSequence()) : -1;
            table.getSelectionModel().select(index >= 0 ? index : items.size() - 1);
        } else {
            showEntry(null);
        }
    }

    public boolean isShowing() {
        return stage != null && stage.isShowing();
    }

    private int indexOfSequence(int sequence) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getSequence() == sequence) return i;
        }
        return -1;
    }

    private void buildColumns() {
        TableColumn<MasterStrategyEntry, String> seqCol = new TableColumn<>("#");
        seqCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getSequence() == confirmedMasterSequence
                        ? "#" + c.getValue().getSequence() + " · MASTER"
                        : "#" + c.getValue().getSequence()));
        seqCol.setMinWidth(105);
        seqCol.setMaxWidth(125);

        TableColumn<MasterStrategyEntry, String> stageCol = new TableColumn<>("Pick / Stage");
        stageCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getStageTaskName().isBlank()
                        ? c.getValue().getSourceDatabank() : c.getValue().getStageTaskName()));

        TableColumn<MasterStrategyEntry, String> profitCol = new TableColumn<>("Profit");
        profitCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().isBacktestSucceeded() ? num(c.getValue().getProfit()) : "—"));

        TableColumn<MasterStrategyEntry, String> rddCol = new TableColumn<>("Profit/DD");
        rddCol.setCellValueFactory(c -> new SimpleStringProperty(num(c.getValue().getReturnToDrawdown())));

        TableColumn<MasterStrategyEntry, String> verdictCol = new TableColumn<>("Bewertung");
        verdictCol.setCellValueFactory(c -> new SimpleStringProperty(verdictLabel(c.getValue())));

        table.getColumns().setAll(List.of(seqCol, stageCol, profitCol, rddCol, verdictCol));

        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(MasterStrategyEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) {
                    setStyle("");
                    return;
                }
                String verdictStyle = switch (entry.getVerdict()) {
                    case BESSER -> "-fx-background-color: rgba(0, 230, 118, 0.18);";
                    case SCHLECHTER -> "-fx-background-color: rgba(255, 82, 82, 0.18);";
                    case NEUTRAL -> "-fx-background-color: rgba(255, 215, 64, 0.14);";
                    case UNBEKANNT -> "";
                };
                if (entry.getSequence() == confirmedMasterSequence) {
                    verdictStyle += "-fx-border-color: #00e5ff; -fx-border-width: 2 0 2 4;";
                }
                setStyle(verdictStyle);
            }
        });
    }

    private LineChart<Number, Number> createTrendChart() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Pick #");
        xAxis.setTickLabelFill(Color.web("#7e889a"));
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Profit / Drawdown");
        yAxis.setTickLabelFill(Color.web("#7e889a"));

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Entwicklung der Master-Strategie");
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setPrefHeight(240);
        chart.setMinHeight(200);
        return chart;
    }

    private void updateTrendChart(List<MasterStrategyEntry> lineage) {
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Profit/DD");
        String context = currentContextKey(lineage);
        int otherContexts = 0;
        for (MasterStrategyEntry entry : lineage) {
            if (entry == null || !entry.isBacktestSucceeded()) continue;
            if (!context.equals(entry.contextKey())) {
                otherContexts++;
                continue;
            }
            double value = entry.getReturnToDrawdown();
            if (!Double.isFinite(value)) continue;
            series.getData().add(new XYChart.Data<>(entry.getSequence(), value));
        }
        trendChart.setTitle(otherContexts == 0
                ? "Entwicklung der Master-Strategie"
                : "Entwicklung der Master-Strategie (" + otherContexts
                        + " Messpunkte anderer Referenzbedingungen ausgeblendet)");
        trendChart.getData().setAll(List.of(series));
    }

    /** Reference conditions of the newest measurement; older ones are not comparable. */
    private static String currentContextKey(List<MasterStrategyEntry> lineage) {
        return lineage.isEmpty() ? "" : lineage.get(lineage.size() - 1).contextKey();
    }

    private void updateVerdictBanner(List<MasterStrategyEntry> lineage) {
        if (lineage.isEmpty()) {
            verdictBanner.setText("");
            return;
        }
        verdictBanner.setText(masterStatusText(lineage, confirmedMasterSequence));
        verdictBanner.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: "
                + (confirmedMasterSequence > 0 ? "#00e5ff;" : "#9aa4b5;"));
    }

    static String masterStatusText(List<MasterStrategyEntry> lineage, int confirmedSequence) {
        if (lineage == null || lineage.isEmpty()) return "";
        MasterStrategyEntry latest = lineage.get(lineage.size() - 1);
        MasterStrategyEntry confirmed = lineage.stream()
                .filter(entry -> entry != null && entry.getSequence() == confirmedSequence)
                .findFirst().orElse(null);
        String masterText = confirmed != null
                ? "BESTÄTIGTER MASTER: #" + confirmed.getSequence() + " ("
                        + confirmed.getStageTaskName() + ", Profit/DD "
                        + num(confirmed.getReturnToDrawdown()) + ")"
                : "NOCH KEIN MASTER BESTÄTIGT";
        String latestText = "Letzte Messung: #" + latest.getSequence() + " · " + verdictLabel(latest);
        if (confirmed != null && latest.getSequence() != confirmed.getSequence()) {
            latestText += " · nicht übernommen";
        } else if (confirmed != null) {
            latestText += " · bestätigt";
        }
        return masterText + "  ·  " + latestText;
    }

    private void showEntry(MasterStrategyEntry entry) {
        equityBox.getChildren().clear();
        detailBox.getChildren().clear();
        if (entry == null) {
            equityBox.getChildren().add(hint("Kein Messpunkt ausgewählt."));
            return;
        }

        detailBox.getChildren().add(sectionTitle(entryStatusText(entry, confirmedMasterSequence)));
        equityBox.getChildren().add(sectionTitle("Equitykurve — " + entry.getShortLabel()));
        if (!entry.getEquityCurve().isEmpty()) {
            equityBox.getChildren().add(createEquityChart(entry));
        }
        Path png = entry.getEquityImagePath().isBlank() ? null : Path.of(entry.getEquityImagePath());
        if (png != null && Files.isRegularFile(png)) {
            ImageView view = new ImageView(new Image(png.toUri().toString(), 900, 0, true, true));
            view.setPreserveRatio(true);
            view.setFitWidth(900);
            equityBox.getChildren().addAll(sectionTitle("MetaTrader-Grafik"), view);
        } else if (entry.getEquityCurve().isEmpty()) {
            equityBox.getChildren().add(hint(entry.isBacktestSucceeded()
                    ? "Für diesen Messpunkt liegen keine Equity-Daten vor."
                    : "Referenz-Backtest ohne Ergebnis: " + entry.getFailureMessage()));
        }

        detailBox.getChildren().add(sectionTitle("Kennzahlen"));
        detailBox.getChildren().add(metricsGrid(entry));
        detailBox.getChildren().add(sectionTitle("Herkunft"));
        detailBox.getChildren().add(originGrid(entry));
        addOptimizedParameters(entry);
        addAdditionalChanges(entry);
        addNextStageTargets(entry);
    }

    static String entryStatusText(MasterStrategyEntry entry, int confirmedSequence) {
        if (entry == null) return "";
        if (entry.getSequence() == confirmedSequence) return "BESTÄTIGTER MASTER #" + entry.getSequence();
        if (confirmedSequence > 0) {
            return "Messpunkt #" + entry.getSequence() + " · nicht übernommen (Master #"
                    + confirmedSequence + ")";
        }
        return "Messpunkt #" + entry.getSequence() + " · kein Master bestätigt";
    }

    /**
     * The point of the pane: which parameters the stage before this pick varied, and
     * which value each of them replaced. Unchanged ones stay visible — "optimized and
     * kept" is a result, not a gap.
     */
    private void addOptimizedParameters(MasterStrategyEntry entry) {
        String stage = entry.getOptimizedStageName();
        detailBox.getChildren().add(sectionTitle("Optimierte Parameter"
                + (stage.isBlank() ? "" : " — " + stage)));

        List<MasterStrategyEntry.ParameterChange> optimized = entry.getOptimizedParameters();
        if (!optimized.isEmpty()) {
            int changed = 0;
            for (MasterStrategyEntry.ParameterChange change : optimized) {
                if (change != null && change.isChanged()) changed++;
            }
            detailBox.getChildren().add(hint(changed + " von " + optimized.size()
                    + " optimierten Parametern haben einen neuen Wert."));
            detailBox.getChildren().add(changeGrid(optimized));
            return;
        }

        // Entries measured before this pane existed only carry the preformatted lines.
        if (!entry.getAdoptedChanges().isEmpty()) {
            VBox changes = new VBox(3);
            for (String line : entry.getAdoptedChanges()) {
                Label label = new Label("• " + line);
                label.setWrapText(true);
                label.setStyle("-fx-text-fill: #e6e9f0; -fx-font-size: 12px;");
                changes.getChildren().add(label);
            }
            detailBox.getChildren().add(changes);
            return;
        }
        detailBox.getChildren().add(hint("Für diesen Messpunkt wurde nicht aufgezeichnet, "
                + "welche Parameter die vorherige Stufe optimiert hat."));
    }

    private void addAdditionalChanges(MasterStrategyEntry entry) {
        List<MasterStrategyEntry.ParameterChange> additional = entry.getAdditionalChanges();
        if (additional.isEmpty()) return;
        detailBox.getChildren().add(sectionTitle("Weitere übernommene Werte"));
        detailBox.getChildren().add(hint("Aus dem Lauf-Preset mitübernommen, "
                + "kein Ziel der optimierten Stufe."));
        detailBox.getChildren().add(changeGrid(additional));
    }

    private void addNextStageTargets(MasterStrategyEntry entry) {
        List<MasterStrategyEntry.OptimizationTarget> targets = entry.getNextStageTargets();
        if (targets.isEmpty()) return;
        detailBox.getChildren().add(sectionTitle("Nächste Stufe variiert"
                + (entry.getStageTaskName().isBlank() ? "" : " — " + entry.getStageTaskName())));

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(4);
        addHeaderCell(grid, 0, 0, "Parameter");
        addHeaderCell(grid, 1, 0, "Startwert");
        addHeaderCell(grid, 2, 0, "Suchraum");
        int row = 1;
        for (MasterStrategyEntry.OptimizationTarget target : targets) {
            if (target == null) continue;
            addValueCell(grid, 0, row, target.getName(), "#e6e9f0", false);
            addValueCell(grid, 1, row, target.getCurrentValue(), "#e6e9f0", true);
            String range = target.describeRange();
            addValueCell(grid, 2, row, range.isEmpty() ? "—" : range, "#9aa4b5", false);
            row++;
        }
        detailBox.getChildren().add(grid);
    }

    /** Parameter · alter Wert · neuer Wert; unchanged rows are dimmed instead of hidden. */
    private GridPane changeGrid(List<MasterStrategyEntry.ParameterChange> changes) {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(4);
        addHeaderCell(grid, 0, 0, "Parameter");
        addHeaderCell(grid, 1, 0, "vorher");
        addHeaderCell(grid, 2, 0, "nachher");

        int row = 1;
        for (MasterStrategyEntry.ParameterChange change : changes) {
            if (change == null) continue;
            boolean changed = change.isChanged();
            String nameColor = changed ? "#e6e9f0" : "#7e889a";
            addValueCell(grid, 0, row, change.getName(), nameColor, false);
            addValueCell(grid, 1, row, blankToDash(change.getOldValue()), "#9aa4b5", true);
            addValueCell(grid, 2, row, blankToDash(change.getNewValue()),
                    changed ? "#00e676" : "#7e889a", true);
            row++;
        }
        return grid;
    }

    private void addHeaderCell(GridPane grid, int column, int row, String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 11px; -fx-font-weight: bold;");
        grid.add(label, column, row);
    }

    private void addValueCell(GridPane grid, int column, int row, String text,
                              String color, boolean bold) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px;"
                + (bold ? " -fx-font-weight: bold;" : ""));
        grid.add(label, column, row);
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private LineChart<Number, Number> createEquityChart(MasterStrategyEntry entry) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Trades");
        xAxis.setTickLabelFill(Color.web("#7e889a"));
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Kapital");
        yAxis.setTickLabelFill(Color.web("#7e889a"));

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setPrefHeight(360);

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        for (double[] point : entry.getEquityCurve()) {
            if (point != null && point.length >= 2) {
                series.getData().add(new XYChart.Data<>(point[0], point[1]));
            }
        }
        chart.getData().add(series);
        return chart;
    }

    private GridPane metricsGrid(MasterStrategyEntry entry) {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(6);
        int row = 0;
        addMetric(grid, row++, "Profit", num(entry.getProfit()), entry.getDeltaProfit(), true);
        addMetric(grid, row++, "Profit/Drawdown", num(entry.getReturnToDrawdown()),
                entry.getDeltaReturnToDrawdown(), true);
        addMetric(grid, row++, "Profit-Faktor", num(entry.getProfitFactor()), Double.NaN, true);
        addMetric(grid, row++, "Max. Drawdown %", num(entry.getMaxDrawdownPercent()),
                entry.getDeltaMaxDrawdownPercent(), false);
        addMetric(grid, row++, "Max. Drawdown abs.", num(entry.getMaxDrawdownAbsolute()), Double.NaN, false);
        addMetric(grid, row++, "Trades", String.valueOf(entry.getTotalTrades()), Double.NaN, true);
        addMetric(grid, row++, "Recovery-Faktor", num(entry.getRecoveryFactor()), Double.NaN, true);
        addMetric(grid, row++, "Sharpe", num(entry.getSharpeRatio()), Double.NaN, true);
        addMetric(grid, row++, "Erwarteter Gewinn", num(entry.getExpectedPayoff()), Double.NaN, true);
        addMetric(grid, row, "Endkapital", num(entry.getFinalBalance()), Double.NaN, true);
        return grid;
    }

    private GridPane originGrid(MasterStrategyEntry entry) {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(6);
        int row = 0;
        addText(grid, row++, "Zeitpunkt", TIMESTAMP.format(Instant.ofEpochMilli(entry.getCreatedAt())));
        addText(grid, row++, "Stage", entry.getStageTaskName());
        addText(grid, row++, "Databank", entry.getSourceDatabank());
        addText(grid, row++, "Pass", entry.getSourcePassNumber() > 0
                ? "#" + entry.getSourcePassNumber() : "Master weitergetragen");
        addText(grid, row++, "Markt", entry.getSymbol() + " " + entry.getPeriod());
        addText(grid, row++, "Zeitraum", entry.getFromDate() + " bis " + entry.getToDate());
        addText(grid, row++, "Modell", entry.getTickModel());
        addText(grid, row++, "Startkapital", entry.getDeposit() + " " + entry.getCurrency()
                + " · Hebel " + entry.getLeverage());
        addText(grid, row++, "Vergleich", entry.getComparedToSequence() > 0
                ? "gegen bestätigten Master #" + entry.getComparedToSequence()
                : "ohne bestätigten Vergleichsanker");
        addText(grid, row, "Report", entry.getReportDirectory().isBlank() ? "—" : entry.getReportDirectory());
        return grid;
    }

    private void addMetric(GridPane grid, int row, String name, String value, double delta, boolean higherIsBetter) {
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-text-fill: #9aa4b5; -fx-font-size: 12px;");
        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-text-fill: #e6e9f0; -fx-font-size: 13px; -fx-font-weight: bold;");
        grid.add(nameLabel, 0, row);
        grid.add(valueLabel, 1, row);

        if (Double.isFinite(delta) && Math.abs(delta) > 1e-9) {
            boolean good = higherIsBetter ? delta > 0 : delta < 0;
            Label deltaLabel = new Label((delta > 0 ? "▲ +" : "▼ ")
                    + String.format(Locale.US, "%.2f", delta));
            deltaLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (good ? "#00e676;" : "#ff5252;"));
            grid.add(deltaLabel, 2, row);
        }
    }

    private void addText(GridPane grid, int row, String name, String value) {
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-text-fill: #9aa4b5; -fx-font-size: 12px;");
        Label valueLabel = new Label(value == null || value.isBlank() ? "—" : value);
        valueLabel.setWrapText(true);
        valueLabel.setStyle("-fx-text-fill: #e6e9f0; -fx-font-size: 12px;");
        grid.add(nameLabel, 0, row);
        grid.add(valueLabel, 1, row);
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #00e5ff; -fx-font-size: 13px; -fx-font-weight: bold;");
        return label;
    }

    private Label hint(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: #9aa4b5; -fx-font-size: 12px;");
        return label;
    }

    private static String verdictLabel(MasterStrategyEntry entry) {
        if (entry == null) return "—";
        if (!entry.isBacktestSucceeded()) return "kein Ergebnis";
        return switch (entry.getVerdict()) {
            case BESSER -> "besser";
            case SCHLECHTER -> "schlechter";
            case NEUTRAL -> "unverändert";
            case UNBEKANNT -> "Referenz";
        };
    }

    private static String num(double value) {
        if (!Double.isFinite(value)) return "—";
        return String.format(Locale.US, "%.2f", value);
    }

    private void generateAndOpenReport() {
        if (project == null) return;
        try {
            Path reportPath = MasterStrategyLineageReportGenerator.generateReport(project);
            MasterStrategyLineageReportGenerator.openInBrowser(reportPath);
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Fehler");
            alert.setHeaderText("Abschlussbericht konnte nicht generiert werden");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
        }
    }
}
