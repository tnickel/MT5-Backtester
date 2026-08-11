package com.backtester.ui.javafx;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;

/**
 * Info-button factories, header tooltips, and generic doc window used by DocHelper.
 */
public final class DocHelperButtons {
    private DocHelperButtons() {}

    public static javafx.scene.Node createHeaderWithTooltip(String title, String tooltipText) {
        return createHeaderWithTooltip(title, tooltipText, null);
    }

    public static javafx.scene.Node createHeaderWithTooltip(String title, String tooltipText, Runnable clickAction) {
        javafx.scene.layout.HBox hbox = new javafx.scene.layout.HBox(4);
        hbox.setAlignment(javafx.geometry.Pos.CENTER);

        javafx.scene.control.Label label = new javafx.scene.control.Label(title);
        if (title.equals("Score")) {
            label.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold;");
        } else {
            label.setStyle("-fx-text-fill: #e6e9f0;");
        }

        javafx.scene.control.Label infoLabel = new javafx.scene.control.Label("ⓘ");
        infoLabel.setStyle("-fx-text-fill: #7e889a; -fx-cursor: hand; -fx-font-size: 11px;");

        if (clickAction != null) {
            infoLabel.setOnMouseClicked(e -> {
                clickAction.run();
                e.consume(); // Prevents triggering column sorting
            });
        }

        javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(tooltipText);
        tooltip.setShowDelay(javafx.util.Duration.millis(100));
        tooltip.setHideDelay(javafx.util.Duration.millis(5000));
        tooltip.setMaxWidth(350);
        tooltip.setWrapText(true);
        tooltip.setStyle("-fx-font-size: 12px; -fx-background-color: #1e293b; -fx-text-fill: #f8fafc; -fx-border-color: #475569; -fx-border-width: 1px; -fx-border-radius: 4px;");
        javafx.scene.control.Tooltip.install(infoLabel, tooltip);

        hbox.getChildren().addAll(label, infoLabel);
        return hbox;
    }

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

    public static Button createSmallInfoButton(Runnable action) {
        Button infoBtn = new Button("ℹ");
        String normalStyle = "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 50%; -fx-min-width: 18px; -fx-min-height: 18px; -fx-max-width: 18px; -fx-max-height: 18px; -fx-text-fill: #00e5ff; -fx-background-color: transparent; -fx-border-color: #00e5ff; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0;";
        String hoverStyle = "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 50%; -fx-min-width: 18px; -fx-min-height: 18px; -fx-max-width: 18px; -fx-max-height: 18px; -fx-text-fill: #111111; -fx-background-color: #00e5ff; -fx-border-color: #00e5ff; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0;";

        infoBtn.setStyle(normalStyle);
        infoBtn.setOnAction(e -> action.run());

        infoBtn.setOnMouseEntered(e -> infoBtn.setStyle(hoverStyle));
        infoBtn.setOnMouseExited(e -> infoBtn.setStyle(normalStyle));

        return infoBtn;
    }

    public static Button createSmallInfoButton(String tooltip, Runnable action) {
        Button btn = createSmallInfoButton(action);
        btn.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        return btn;
    }

    public static Button createThickCircularInfoButton(String tooltip, Runnable action) {
        Button infoBtn = new Button("ℹ");
        String normalStyle = "-fx-font-size: 13px; -fx-font-weight: 900; -fx-background-radius: 50%; -fx-min-width: 22px; -fx-min-height: 22px; -fx-max-width: 22px; -fx-max-height: 22px; -fx-text-fill: #ffd740; -fx-background-color: transparent; -fx-border-color: #ffd740; -fx-border-width: 2px; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0; -fx-alignment: center;";
        String hoverStyle = "-fx-font-size: 13px; -fx-font-weight: 900; -fx-background-radius: 50%; -fx-min-width: 22px; -fx-min-height: 22px; -fx-max-width: 22px; -fx-max-height: 22px; -fx-text-fill: #111111; -fx-background-color: #ffd740; -fx-border-color: #ffd740; -fx-border-width: 2px; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0; -fx-alignment: center;";

        infoBtn.setStyle(normalStyle);
        infoBtn.setOnAction(e -> action.run());
        infoBtn.setTooltip(new javafx.scene.control.Tooltip(tooltip));

        infoBtn.setOnMouseEntered(e -> infoBtn.setStyle(hoverStyle));
        infoBtn.setOnMouseExited(e -> infoBtn.setStyle(normalStyle));

        return infoBtn;
    }

    public static Button createThickCircularCyanInfoButton(String tooltip, Runnable action) {
        Button infoBtn = new Button("ℹ");
        String normalStyle = "-fx-font-size: 13px; -fx-font-weight: 900; -fx-background-radius: 50%; -fx-min-width: 22px; -fx-min-height: 22px; -fx-max-width: 22px; -fx-max-height: 22px; -fx-text-fill: #00e5ff; -fx-background-color: transparent; -fx-border-color: #00e5ff; -fx-border-width: 2px; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0; -fx-alignment: center;";
        String hoverStyle = "-fx-font-size: 13px; -fx-font-weight: 900; -fx-background-radius: 50%; -fx-min-width: 22px; -fx-min-height: 22px; -fx-max-width: 22px; -fx-max-height: 22px; -fx-text-fill: #111111; -fx-background-color: #00e5ff; -fx-border-color: #00e5ff; -fx-border-width: 2px; -fx-border-radius: 50%; -fx-cursor: hand; -fx-padding: 0; -fx-alignment: center;";

        infoBtn.setStyle(normalStyle);
        infoBtn.setOnAction(e -> action.run());
        infoBtn.setTooltip(new javafx.scene.control.Tooltip(tooltip));

        infoBtn.setOnMouseEntered(e -> infoBtn.setStyle(hoverStyle));
        infoBtn.setOnMouseExited(e -> infoBtn.setStyle(normalStyle));

        return infoBtn;
    }
}
