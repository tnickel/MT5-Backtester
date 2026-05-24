package com.backtester.ui.javafx;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class DocHelper {
    public static Button createInfoButton(String tabName, String overview, String details) {
        Button infoBtn = new Button("ℹ");
        String normalStyle = "-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-radius: 50%; -fx-min-width: 28px; -fx-min-height: 28px; -fx-text-fill: #00e5ff; -fx-background-color: transparent; -fx-border-color: #00e5ff; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0;";
        String hoverStyle = "-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-radius: 50%; -fx-min-width: 28px; -fx-min-height: 28px; -fx-text-fill: #111111; -fx-background-color: #00e5ff; -fx-border-color: #00e5ff; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0;";
        
        infoBtn.setStyle(normalStyle);
        infoBtn.setOnAction(e -> showDoc(tabName, overview, details));
        
        infoBtn.setOnMouseEntered(e -> infoBtn.setStyle(hoverStyle));
        infoBtn.setOnMouseExited(e -> infoBtn.setStyle(normalStyle));

        return infoBtn;
    }

    public static Button createSmallInfoButton(String tabName, String overview, String details) {
        Button infoBtn = new Button("ℹ");
        String normalStyle = "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 50%; -fx-min-width: 18px; -fx-min-height: 18px; -fx-max-width: 18px; -fx-max-height: 18px; -fx-text-fill: #00e5ff; -fx-background-color: transparent; -fx-border-color: #00e5ff; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0;";
        String hoverStyle = "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 50%; -fx-min-width: 18px; -fx-min-height: 18px; -fx-max-width: 18px; -fx-max-height: 18px; -fx-text-fill: #111111; -fx-background-color: #00e5ff; -fx-border-color: #00e5ff; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0;";
        
        infoBtn.setStyle(normalStyle);
        infoBtn.setOnAction(e -> showDoc(tabName, overview, details));
        
        infoBtn.setOnMouseEntered(e -> infoBtn.setStyle(hoverStyle));
        infoBtn.setOnMouseExited(e -> infoBtn.setStyle(normalStyle));

        return infoBtn;
    }

    public static void showDoc(String tabName, String overview, String details) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle(tabName + " - Dokumentation");
        stage.initModality(javafx.stage.Modality.NONE); // Allow interaction with main window
        stage.setMinWidth(600);
        stage.setMinHeight(400);
        
        Label overviewTitle = new Label(tabName + " - Übersicht");
        overviewTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 0 0 10 0; -fx-text-fill: white;");
        
        Label overviewLabel = new Label(overview);
        overviewLabel.setWrapText(true);
        overviewLabel.setStyle("-fx-font-size: 14px; -fx-padding: 0 0 15 0; -fx-text-fill: #e2e8f0;");
        
        Label detailLabel = new Label("Details:");
        detailLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-padding: 5 0 5 0; -fx-text-fill: white;");
        
        TextArea textArea = new TextArea(details);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setStyle("-fx-font-size: 14px; -fx-font-family: 'Segoe UI', sans-serif;");
        javafx.scene.layout.VBox.setVgrow(textArea, Priority.ALWAYS);

        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(5);
        root.setPadding(new javafx.geometry.Insets(20));
        root.setStyle("-fx-background-color: #1a1d27;");
        root.getChildren().addAll(overviewTitle, overviewLabel, detailLabel, textArea);
        
        javafx.scene.Scene scene = new javafx.scene.Scene(root, 900, 700);
        try {
            java.net.URL css = DocHelper.class.getResource("/css/antigravity.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            } else {
                java.net.URL css2 = DocHelper.class.getResource("/style.css");
                if (css2 != null) scene.getStylesheets().add(css2.toExternalForm());
            }
        } catch(Exception ignored) {}

        stage.setScene(scene);
        stage.show();
    }
}
