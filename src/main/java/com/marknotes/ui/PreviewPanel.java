package com.marknotes.ui;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class PreviewPanel extends JPanel {
    private final JEditorPane htmlPane;
    private final Parser parser;
    private final HtmlRenderer renderer;
    private boolean dark = false;
    private int fontSize = 12;
    private String lastMarkdown = "";

    private static final String LIGHT_CSS = """
            body {
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
                font-size: %dpx;
                line-height: 1.6;
                padding: 16px;
                color: #333;
                background-color: #ffffff;
            }
            h1, h2, h3, h4, h5, h6 { margin-top: 12px; margin-bottom: 8px; font-weight: 600; }
            h1 { font-size: 1.6em; }
            h2 { font-size: 1.5em; border-bottom: 1px solid #eee; padding-bottom: 0.3em; }
            h3 { font-size: 1.25em; }
            code {
                background-color: #f5f5f5;
                padding: 2px 6px;
                border-radius: 3px;
                font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
                font-size: 100%%;
            }
            pre {
                background-color: #f5f5f5;
                padding: 16px;
                border-radius: 6px;
                overflow-x: auto;
            }
            pre code { padding: 0; background-color: #f5f5f5; font-size: inherit; }
            blockquote {
                margin: 0;
                padding: 0 16px;
                color: #666;
                border-left: 4px solid #ddd;
            }
            table { border-collapse: collapse; width: 100%%; }
            th, td { border: 1px solid #ddd; padding: 8px 12px; text-align: left; }
            th { background-color: #f5f5f5; font-weight: 600; }
            img { max-width: 100%%; }
            a { color: #0366d6; }
            hr { border: none; border-top: 1px solid #eee; margin: 24px 0; }
            ul, ol { padding-left: 2em; }
            li { margin: 4px 0; }
            """;

    private static final String DARK_CSS = """
            body {
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
                font-size: %dpx;
                line-height: 1.6;
                padding: 16px;
                color: #ddd;
                background-color: #2b2b2b;
            }
            h1, h2, h3, h4, h5, h6 { margin-top: 12px; margin-bottom: 8px; font-weight: 600; color: #e6e6e6; }
            h1 { font-size: 1.6em; }
            h2 { font-size: 1.5em; border-bottom: 1px solid #444; padding-bottom: 0.3em; }
            h3 { font-size: 1.25em; }
            code {
                background-color: #3c3c3c;
                color: #e8b86d;
                padding: 2px 6px;
                border-radius: 3px;
                font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
                font-size: 100%%;
            }
            pre {
                background-color: #1e1e1e;
                padding: 16px;
                border-radius: 6px;
                overflow-x: auto;
            }
            pre code { padding: 0; background-color: #1e1e1e; color: #ddd; font-size: inherit; }
            blockquote {
                margin: 0;
                padding: 0 16px;
                color: #aaa;
                border-left: 4px solid #555;
            }
            table { border-collapse: collapse; width: 100%%; }
            th, td { border: 1px solid #555; padding: 8px 12px; text-align: left; }
            th { background-color: #3c3c3c; font-weight: 600; }
            img { max-width: 100%%; }
            a { color: #6baaec; }
            hr { border: none; border-top: 1px solid #444; margin: 24px 0; }
            ul, ol { padding-left: 2em; }
            li { margin: 4px 0; }
            """;

    public PreviewPanel() {
        setLayout(new BorderLayout());

        List<Extension> extensions = List.of(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListItemsExtension.create()
        );
        parser = Parser.builder().extensions(extensions).build();
        renderer = HtmlRenderer.builder().extensions(extensions).build();

        htmlPane = new JEditorPane();
        htmlPane.setContentType("text/html");
        htmlPane.setEditable(false);

        JScrollPane scrollPane = new JScrollPane(htmlPane);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void setDark(boolean dark) {
        this.dark = dark;
        if (!lastMarkdown.isEmpty()) {
            updatePreview(lastMarkdown);
        }
    }

    public void setFontSize(int size) {
        this.fontSize = size;
        if (!lastMarkdown.isEmpty()) {
            updatePreview(lastMarkdown);
        }
    }

    public void updatePreview(String markdown) {
        lastMarkdown = markdown;
        if (markdown == null || markdown.isEmpty()) {
            htmlPane.setText("");
            return;
        }
        String css = String.format(dark ? DARK_CSS : LIGHT_CSS, fontSize);
        String html = renderer.render(parser.parse(markdown));
        String fullHtml = "<html><head><style>" + css + "</style></head><body>" + html + "</body></html>";
        htmlPane.setText(fullHtml);
        htmlPane.setCaretPosition(0);
    }
}
