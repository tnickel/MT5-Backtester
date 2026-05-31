package com.backtester.ui.javafx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class MainView {
    
    private BorderPane root;
    private TabPane tabPane;

    public MainView() {
        root = new BorderPane();
        root.getStyleClass().add("root");

        // Set the brushed metal background (stretched to cover, no seams)
        root.setStyle("-fx-background-image: url('/images/brushed_metal.png'); -fx-background-repeat: no-repeat; -fx-background-size: cover; -fx-background-position: center;");

        // Header
        HBox header = new HBox(15);
        header.setPadding(new Insets(15, 20, 15, 20));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: linear-gradient(to bottom, rgba(11, 13, 19, 0.95), rgba(11, 13, 19, 0.7));");

        Label title = new Label("MT5 Backtester");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
        title.setTextFill(Color.web("#00e5ff"));

        Label subtitle = new Label("— Antigravity Protocol Suite v1.2.6");
        subtitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        subtitle.setTextFill(Color.web("#7e889a"));

        header.getChildren().addAll(title, subtitle);
        root.setTop(header);

        // TabPane
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Create LogView first so it can be passed to other views
        LogView logView = new LogView();
        
        BacktestView backtestView = new BacktestView(logView);
        Tab backtestTab = new Tab("Backtest", backtestView.getView());
        tabPane.getTabs().add(backtestTab);
        backtestView.bindTab(backtestTab);
        
        MultiBacktestView multiBacktestView = new MultiBacktestView(logView);
        Tab multiBacktestTab = new Tab("Multi-Backtester", multiBacktestView.getView());
        tabPane.getTabs().add(multiBacktestTab);
        multiBacktestView.bindTab(multiBacktestTab);
        
        // Integration of new OptimizationView
        OptimizationView optimizationView = new OptimizationView(logView);
        Tab optimizerTab = new Tab("Optimizer", optimizationView.getView());
        tabPane.getTabs().add(optimizerTab);
        tabPane.getSelectionModel().select(optimizerTab);
        optimizationView.bindTab(optimizerTab);
        
        RobustnessView robustnessView = new RobustnessView(logView, optimizationView);
        Tab robustnessTab = new Tab("Robustness", robustnessView.getView());
        tabPane.getTabs().add(robustnessTab);
        robustnessView.bindTab(robustnessTab);
        
        // Integration of new WorkflowAutomator
        WorkflowView workflowView = new WorkflowView(logView);
        Tab workflowTab = new Tab("🔄 Workflow Automator", workflowView.getView());
        tabPane.getTabs().add(workflowTab);
        tabPane.getSelectionModel().select(workflowTab);
        workflowView.bindTab(workflowTab);
        
        // Controlling View
        ControllingView controllingView = new ControllingView(logView, workflowView);
        Tab controllingTab = new Tab("📊 Controlling", controllingView.getView());
        tabPane.getTabs().add(controllingTab);
        controllingView.bindTab(controllingTab);
        
        HistoryView historyView = new HistoryView(workflowView);
        Tab databaseTab = new Tab("Database", historyView.getView());
        tabPane.getTabs().add(databaseTab);
        historyView.bindTab(databaseTab);
        
        // Auto-refresh tabs when selected
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == databaseTab) {
                historyView.reloadTree();
            } else if (newTab == controllingTab) {
                controllingView.refreshResults();
            }
        });
        
        DukascopyView dukascopyView = new DukascopyView(logView);
        tabPane.getTabs().add(new Tab("Dukascopy Data", dukascopyView.getView()));
        
        SettingsView settingsView = new SettingsView();
        tabPane.getTabs().add(new Tab("Settings", settingsView.getView()));
        
        tabPane.getTabs().add(new Tab("Log", logView.getView()));
        logView.log("INFO", "MT5 Backtester v1.2.6 started (JavaFX Engine)");
        logView.log("INFO", "Antigravity Protocol Suite initialized.");
        logView.log("INFO", "Ready.");
        
        HelpView helpView = new HelpView();
        tabPane.getTabs().add(new Tab("Manual", helpView.getView()));

        root.setCenter(tabPane);
    }

    private Region createDummyPane(String text) {
        VBox box = new VBox();
        box.setAlignment(Pos.CENTER);
        Label l = new Label(text);
        l.setTextFill(Color.WHITE);
        l.setFont(Font.font(20));
        box.getChildren().add(l);
        return box;
    }

    public BorderPane getView() {
        return root;
    }
}
