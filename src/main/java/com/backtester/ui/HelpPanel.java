package com.backtester.ui;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.backtester.config.AppConfig;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Panel to display the Markdown user manual in a formatted HTML view.
 */
public class HelpPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(HelpPanel.class);
    private final JEditorPane editorPane;
    private final LogPanel logPanel;

    public HelpPanel(LogPanel logPanel) {
        this.logPanel = logPanel;
        setLayout(new BorderLayout());
        setBackground(new Color(30, 33, 40));

        editorPane = new JEditorPane();
        editorPane.setEditable(false);
        editorPane.setContentType("text/html");
        
        // Ensure standard HTML rendering behavior
        editorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        
        // Add hyperlink support
        editorPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                if (e.getURL() != null) {
                    try {
                        Desktop.getDesktop().browse(e.getURL().toURI());
                    } catch (Exception ex) {
                        log.error("Could not open link: {}", e.getURL(), ex);
                    }
                } else if (e.getDescription() != null && e.getDescription().startsWith("#")) {
                    // Internal anchor link handling
                    editorPane.scrollToReference(e.getDescription().substring(1));
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);

        // Load content
        loadManual();
    }

    private void loadManual() {
        try {
            // Resolve robustly based on the actual JAR location
            Path jarDir;
            try {
                Path p = Paths.get(HelpPanel.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                jarDir = p.getParent();
            } catch (Exception ex) {
                jarDir = Paths.get(System.getProperty("user.dir"));
            }

            // jpackage puts the jar in an "app" folder. The "doc" folder is parallel to "app".
            Path externalDoc;
            if (jarDir != null && jarDir.getFileName() != null && jarDir.getFileName().toString().equalsIgnoreCase("app")) {
                externalDoc = jarDir.getParent().resolve("doc").resolve("user_manual.md");
            } else {
                externalDoc = jarDir == null ? Paths.get("doc", "user_manual.md") : jarDir.resolve("doc").resolve("user_manual.md");
            }

            // Fallback to AppConfig just in case it's run from IDE
            if (!Files.exists(externalDoc)) {
                externalDoc = AppConfig.getInstance().getBasePath().resolve("doc").resolve("user_manual.md");
            }

            String markdownContent;
            
            if (Files.exists(externalDoc)) {
                markdownContent = Files.readString(externalDoc);
            } else {
                markdownContent = "## 📖 User Manual Not Found\n\nCould not locate the `doc/user_manual.md` file.\n\nSearched at:\n- `" + externalDoc.toAbsolutePath() + "`";
                logPanel.log("WARN", "Help file not found at " + externalDoc.toAbsolutePath());
            }

            // Convert Markdown to HTML
            Parser parser = Parser.builder().build();
            Node document = parser.parse(markdownContent);
            HtmlRenderer renderer = HtmlRenderer.builder().build();
            String rawHtml = renderer.render(document);

            // Wrap in styled HTML
            String styledHtml = buildStyledHtml(rawHtml);
            editorPane.setText(styledHtml);
            
            // Scroll to top
            SwingUtilities.invokeLater(() -> editorPane.setCaretPosition(0));

        } catch (Exception e) {
            log.error("Error loading help panel content", e);
            editorPane.setText("<html><body style='color:#E0E0E0; font-family:sans-serif;'><h3>Error loading manual</h3><p>" + e.getMessage() + "</p></body></html>");
        }
    }

    private String buildStyledHtml(String body) {
        return "<html>" +
               "<head>" +
               "<style>" +
               "body {" +
               "   font-family: 'Segoe UI', Tahoma, Verdana, sans-serif;" +
               "   font-size: 13px;" +
               "   color: #C8CCD4;" +
               "   background-color: #1E2128;" +
               "   margin: 20px 40px;" +
               "   line-height: 1.5;" +
               "}" +
               "h1 {" +
               "   color: #4E9AF1;" +
               "   font-size: 24px;" +
               "   border-bottom: 1px solid #3C414B;" +
               "   padding-bottom: 5px;" +
               "   margin-top: 10px;" +
               "}" +
               "h2 {" +
               "   color: #F1B24E;" +
               "   font-size: 18px;" +
               "   border-bottom: 1px solid #3C414B;" +
               "   padding-bottom: 4px;" +
               "   margin-top: 25px;" +
               "}" +
               "h3 {" +
               "   color: #A0A5AF;" +
               "   font-size: 15px;" +
               "   margin-top: 20px;" +
               "}" +
               "a {" +
               "   color: #4E9AF1;" +
               "   text-decoration: none;" +
               "}" +
               "hr {" +
               "   border: 0;" +
               "   border-top: 1px solid #3C414B;" +
               "   margin: 25px 0;" +
               "}" +
               "ul, ol {" +
               "   margin-top: 5px;" +
               "   margin-bottom: 15px;" +
               "}" +
               "li {" +
               "   margin-bottom: 5px;" +
               "}" +
               "strong, b {" +
               "   color: #E6E8ED;" +
               "}" +
               "code {" +
               "   font-family: Consolas, monospace;" +
               "   background-color: #2F333D;" +
               "   color: #A3BE8C;" +
               "   padding: 2px 4px;" +
               "   border-radius: 3px;" +
               "}" +
               "</style>" +
               "</head>" +
               "<body>" + body + "</body>" +
               "</html>";
    }
}
