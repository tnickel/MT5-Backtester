package com.backtester.ui.javafx;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogView {

    private final VBox root;
    private final ListView<TextFlow> listView;
    private final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public LogView() {
        root = new VBox(10);
        root.setPadding(new Insets(15));

        // Toolbar
        HBox toolbar = new HBox(15);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Application Log");
        title.getStyleClass().add("sci-fi-panel-title");

        Button clearBtn = new Button("Clear Log");
        clearBtn.getStyleClass().add("button");
        clearBtn.setOnAction(e -> clearLog());

        Button copyBtn = new Button("Copy to Clipboard");
        copyBtn.getStyleClass().add("button");
        copyBtn.setOnAction(e -> copyToClipboard());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolbar.getChildren().addAll(title, spacer, clearBtn, copyBtn);

        // List View for logs
        listView = new ListView<>();
        listView.setStyle("-fx-background-color: transparent; -fx-control-inner-background: #14161c; -fx-background-insets: 0;");
        listView.getStyleClass().add("sci-fi-panel");
        VBox.setVgrow(listView, Priority.ALWAYS);

        root.getChildren().addAll(toolbar, listView);
    }

    public void log(String level, String message) {
        Platform.runLater(() -> {
            TextFlow flow = new TextFlow();

            // Timestamp
            String timestamp = LocalDateTime.now().format(timeFormat);
            Text timeText = new Text(timestamp + " ");
            timeText.setFill(Color.web("#646978")); // timestamp color
            timeText.setFont(Font.font("Consolas", 12));

            // Level Text
            String lvlStr = String.format("[%-5s] ", level.toUpperCase());
            Text lvlText = new Text(lvlStr);
            lvlText.setFont(Font.font("Consolas", 12));

            Color lvlColor;
            switch (level.toUpperCase()) {
                case "WARN":
                case "WARNING":
                    lvlColor = Color.web("#f0c850");
                    break;
                case "ERROR":
                    lvlColor = Color.web("#f06464");
                    break;
                case "DEBUG":
                    lvlColor = Color.web("#8c91a0");
                    break;
                case "MT5":
                    lvlColor = Color.web("#4e9af1");
                    break;
                default:
                    lvlColor = Color.web("#78c882");
                    break;
            }
            lvlText.setFill(lvlColor);

            // Message Text
            Text msgText = new Text(message);
            msgText.setFont(Font.font("Consolas", 12));
            msgText.setFill(message.startsWith("[MT5]") ? Color.web("#4e9af1") : lvlColor);

            flow.getChildren().addAll(timeText, lvlText, msgText);

            listView.getItems().add(flow);
            listView.scrollTo(listView.getItems().size() - 1);

            if (listView.getItems().size() > 5000) {
                listView.getItems().remove(0, 500);
            }
        });
    }

    public void logMessage(String message) {
        String level = "INFO";
        if (message.contains("ERROR")) level = "ERROR";
        else if (message.contains("WARNING") || message.contains("WARN")) level = "WARN";
        else if (message.contains("[MT5]")) level = "MT5";
        log(level, message);
    }

    public void clearLog() {
        Platform.runLater(() -> listView.getItems().clear());
    }

    private void copyToClipboard() {
        StringBuilder sb = new StringBuilder();
        for (TextFlow flow : listView.getItems()) {
            for (javafx.scene.Node node : flow.getChildren()) {
                if (node instanceof Text) {
                    sb.append(((Text) node).getText());
                }
            }
            sb.append("\n");
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    public VBox getView() {
        return root;
    }
}
