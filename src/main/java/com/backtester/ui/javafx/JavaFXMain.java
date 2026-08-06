package com.backtester.ui.javafx;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.net.URL;

public class JavaFXMain extends Application {

    private MainView mainView;

    @Override
    public void start(Stage primaryStage) {
        mainView = new MainView();
        Scene scene = new Scene(mainView.getView(), 1700, 1050);

        URL cssUrl = getClass().getResource("/css/antigravity.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        primaryStage.setTitle("MT5 Backtester — Antigravity Protocol Suite v1.3.0");
        
        // Try to set app icon
        try {
            URL iconUrl = getClass().getResource("/images/quantum_singularity.png");
            if (iconUrl != null) {
                primaryStage.getIcons().add(new Image(iconUrl.toExternalForm()));
            }
        } catch (Exception e) {}

        primaryStage.setScene(scene);
        primaryStage.show();

        // Ensure HTTP server for HTML report interaction is running
        com.backtester.server.LocalBacktestHttpServer.getInstance();
    }

    @Override
    public void stop() {
        if (mainView != null) mainView.shutdown();
        com.backtester.server.LocalBacktestHttpServer.getInstance().stop();
    }
}
