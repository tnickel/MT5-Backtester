package com.backtester.ui.javafx;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.net.URL;

public class JavaFXMain extends Application {

    @Override
    public void start(Stage primaryStage) {
        MainView mainView = new MainView();
        Scene scene = new Scene(mainView.getView(), 1300, 1050);

        URL cssUrl = getClass().getResource("/css/antigravity.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        primaryStage.setTitle("MT5 Backtester — Antigravity Protocol Suite");
        
        // Try to set app icon
        try {
            URL iconUrl = getClass().getResource("/images/quantum_singularity.png");
            if (iconUrl != null) {
                primaryStage.getIcons().add(new Image(iconUrl.toExternalForm()));
            }
        } catch (Exception e) {}

        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
