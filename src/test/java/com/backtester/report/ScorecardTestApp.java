package com.backtester.report;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScorecardTestApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(ScorecardTestApp.class);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        log.info("Starting ScorecardTestApp...");
        stage.setTitle("🛡️ Robustness Scorecard Test App");

        // Mock pass
        Pass bt = new Pass();
        bt.setPassNumber(3751);
        bt.setProfit(7.16);
        bt.setTotalTrades(5);
        bt.setProfitFactor(24.87);
        bt.setDrawdownPercent(0.04);
        bt.setExpectedPayoff(1.49);
        bt.setRecoveryFactor(1.71);
        bt.setSharpeRatio(1.10);

        Pass fw = new Pass();
        fw.setPassNumber(3751);
        fw.setProfit(5.0);
        fw.setTotalTrades(3);
        fw.setProfitFactor(12.0);
        fw.setDrawdownPercent(0.02);
        fw.setExpectedPayoff(1.2);
        fw.setRecoveryFactor(1.5);
        fw.setSharpeRatio(1.0);

        CombinedPass cp = new CombinedPass(bt, fw, 34.80, 0.25, "");

        String htmlContent = RobustnessScorecardGenerator.generateHtml(
            cp, "CC_ADR_Stoch_Grid", "EURUSD", "H1", "2026-01-01", "2026-04-26"
        );

        log.info("HTML content generated, size: {} characters", htmlContent.length());

        WebView webView = new WebView();
        webView.getEngine().setOnAlert(event -> log.info("JS ALERT: {}", event.getData()));
        
        webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            log.info("LoadWorker State: {} -> {}", oldState, newState);
            if (newState == javafx.concurrent.Worker.State.RUNNING || newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                try {
                    netscape.javascript.JSObject window = (netscape.javascript.JSObject) webView.getEngine().executeScript("window");
                    window.setMember("consoleBridge", new ConsoleLoggerBridge());
                    log.info("Bound consoleBridge to JS window");
                } catch (Exception e) {
                    log.error("Failed to bind bridge: ", e);
                }
            }
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                try {
                    String appHtml = (String) webView.getEngine().executeScript("document.getElementById('app').innerHTML");
                    log.info("Rendered #app innerHTML:\n{}", appHtml);
                } catch (Exception ex) {
                    log.error("Failed to inspect #app innerHTML: ", ex);
                }
            }
        });

        webView.getEngine().getLoadWorker().exceptionProperty().addListener((obs, oldExc, newExc) -> {
            if (newExc != null) {
                log.error("WebView Load Exception: ", newExc);
            }
        });

        webView.getEngine().loadContent(htmlContent);

        VBox box = new VBox(webView);
        VBox.setVgrow(webView, Priority.ALWAYS);

        Scene scene = new Scene(box, 750, 750);
        stage.setScene(scene);
        stage.show();
    }

    public static class ConsoleLoggerBridge {
        public void log(String text) { log.info("JS CONSOLE: {}", text); }
        public void error(String text) { log.error("JS ERROR: {}", text); }
    }
}
