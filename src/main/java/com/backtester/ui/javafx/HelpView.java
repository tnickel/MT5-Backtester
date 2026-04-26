package com.backtester.ui.javafx;

import com.backtester.config.AppConfig;
import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HelpView {

    private final BorderPane root;

    public HelpView() {
        root = new BorderPane();
        root.setPadding(new Insets(15));
        
        WebView webView = new WebView();
        root.setCenter(webView);

        loadManual(webView);
    }

    private void loadManual(WebView webView) {
        try {
            Path docPath = AppConfig.getInstance().getBasePath().resolve("doc").resolve("user_manual.md");
            String markdownContent;
            
            if (Files.exists(docPath)) {
                markdownContent = Files.readString(docPath);
            } else {
                markdownContent = "## 📖 User Manual Not Found\n\nCould not locate the `doc/user_manual.md` file.\n\nSearched at:\n- `" + docPath.toAbsolutePath() + "`";
            }

            Parser parser = Parser.builder().build();
            Node document = parser.parse(markdownContent);
            HtmlRenderer renderer = HtmlRenderer.builder().build();
            String rawHtml = renderer.render(document);

            String css = "<style>"
                    + "body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #1a1e28; color: #e6e9f0; padding: 20px; line-height: 1.6; }"
                    + "h1, h2, h3 { color: #00e5ff; border-bottom: 1px solid #3e4555; padding-bottom: 5px; }"
                    + "a { color: #50d278; text-decoration: none; }"
                    + "code { background-color: #14161c; padding: 2px 5px; border-radius: 3px; font-family: Consolas, monospace; border: 1px solid #3e4555; }"
                    + "pre { background-color: #14161c; padding: 15px; border-radius: 5px; overflow-x: auto; border: 1px solid #3e4555; }"
                    + "blockquote { border-left: 4px solid #00e5ff; margin-left: 0; padding-left: 15px; color: #b4bac8; }"
                    + "table { border-collapse: collapse; width: 100%; margin-bottom: 20px; }"
                    + "th, td { border: 1px solid #3e4555; padding: 8px 12px; text-align: left; }"
                    + "th { background-color: #14161c; }"
                    + "tr:nth-child(even) { background-color: #14161c; }"
                    + "</style>";

            String fullHtml = "<html><head>" + css + "</head><body>" + rawHtml + "</body></html>";
            
            webView.getEngine().loadContent(fullHtml);
            
        } catch (Exception e) {
            webView.getEngine().loadContent("<html><body style='background-color:#1a1e28; color:red; font-family:sans-serif;'><h2>Error loading manual</h2><p>" + e.getMessage() + "</p></body></html>");
        }
    }

    public BorderPane getView() {
        return root;
    }
}
