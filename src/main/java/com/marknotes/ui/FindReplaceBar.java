package com.marknotes.ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class FindReplaceBar extends JPanel {
    private final RSyntaxTextArea textArea;
    private final JTextField findField;
    private final JTextField replaceField;
    private final JLabel resultLabel;
    private final JCheckBox matchCaseBox;
    private final JCheckBox regexBox;
    private final JCheckBox wholeWordBox;
    private final JPanel replaceRow;
    private boolean replaceVisible = false;

    public FindReplaceBar(RSyntaxTextArea textArea) {
        this.textArea = textArea;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));

        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));

        // Find row
        JPanel findRow = new JPanel(new BorderLayout(4, 0));
        findRow.setBorder(BorderFactory.createEmptyBorder(4, 8, 2, 8));

        findField = new JTextField();
        findField.setColumns(20);
        findField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (e.isShiftDown()) findPrevious(); else findNext();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    close();
                }
            }
        });

        JPanel findButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        JButton prevBtn = new JButton("▲");
        prevBtn.setToolTipText("Previous (Shift+Enter)");
        prevBtn.setMargin(new Insets(2, 4, 2, 4));
        prevBtn.addActionListener(e -> findPrevious());

        JButton nextBtn = new JButton("▼");
        nextBtn.setToolTipText("Next (Enter)");
        nextBtn.setMargin(new Insets(2, 4, 2, 4));
        nextBtn.addActionListener(e -> findNext());

        resultLabel = new JLabel("");
        resultLabel.setForeground(Color.GRAY);

        findButtons.add(prevBtn);
        findButtons.add(nextBtn);
        findButtons.add(resultLabel);

        findRow.add(new JLabel("Find:"), BorderLayout.WEST);
        findRow.add(findField, BorderLayout.CENTER);
        findRow.add(findButtons, BorderLayout.EAST);

        // Replace row
        replaceRow = new JPanel(new BorderLayout(4, 0));
        replaceRow.setBorder(BorderFactory.createEmptyBorder(2, 8, 4, 8));

        replaceField = new JTextField();
        replaceField.setColumns(20);
        replaceField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    close();
                }
            }
        });

        JPanel replaceButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        JButton replaceBtn = new JButton("Replace");
        replaceBtn.setMargin(new Insets(2, 6, 2, 6));
        replaceBtn.addActionListener(e -> replaceCurrent());

        JButton replaceAllBtn = new JButton("All");
        replaceAllBtn.setMargin(new Insets(2, 6, 2, 6));
        replaceAllBtn.addActionListener(e -> replaceAll());

        replaceButtons.add(replaceBtn);
        replaceButtons.add(replaceAllBtn);

        replaceRow.add(new JLabel("Replace:"), BorderLayout.WEST);
        replaceRow.add(replaceField, BorderLayout.CENTER);
        replaceRow.add(replaceButtons, BorderLayout.EAST);
        replaceRow.setVisible(false);

        // Options row
        JPanel optionsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        optionsRow.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
        matchCaseBox = new JCheckBox("Match case");
        regexBox = new JCheckBox("Regex");
        wholeWordBox = new JCheckBox("Whole word");
        optionsRow.add(matchCaseBox);
        optionsRow.add(wholeWordBox);
        optionsRow.add(regexBox);

        // Close button
        JPanel closePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        JButton closeBtn = new JButton("×");
        closeBtn.setMargin(new Insets(0, 4, 0, 4));
        closeBtn.setContentAreaFilled(false);
        closeBtn.addActionListener(e -> close());
        closePanel.add(closeBtn);

        rows.add(findRow);
        rows.add(replaceRow);
        rows.add(optionsRow);

        add(rows, BorderLayout.CENTER);
        add(closePanel, BorderLayout.EAST);
    }

    public void showFind() {
        replaceVisible = false;
        replaceRow.setVisible(false);
        setVisible(true);
        findField.requestFocusInWindow();
        String selected = textArea.getSelectedText();
        if (selected != null && !selected.contains("\n")) {
            findField.setText(selected);
        }
        findField.selectAll();
        revalidate();
    }

    public void showFindReplace() {
        replaceVisible = true;
        replaceRow.setVisible(true);
        setVisible(true);
        findField.requestFocusInWindow();
        String selected = textArea.getSelectedText();
        if (selected != null && !selected.contains("\n")) {
            findField.setText(selected);
        }
        findField.selectAll();
        revalidate();
    }

    private void close() {
        setVisible(false);
        textArea.requestFocusInWindow();
    }

    private SearchContext createContext(boolean forward) {
        SearchContext ctx = new SearchContext();
        ctx.setSearchFor(findField.getText());
        ctx.setMatchCase(matchCaseBox.isSelected());
        ctx.setRegularExpression(regexBox.isSelected());
        ctx.setWholeWord(wholeWordBox.isSelected());
        ctx.setSearchForward(forward);
        return ctx;
    }

    private void findNext() {
        SearchContext ctx = createContext(true);
        SearchResult result = SearchEngine.find(textArea, ctx);
        updateResultLabel(result);
    }

    private void findPrevious() {
        SearchContext ctx = createContext(false);
        SearchResult result = SearchEngine.find(textArea, ctx);
        updateResultLabel(result);
    }

    private void replaceCurrent() {
        SearchContext ctx = createContext(true);
        ctx.setReplaceWith(replaceField.getText());
        SearchResult result = SearchEngine.replace(textArea, ctx);
        updateResultLabel(result);
    }

    private void replaceAll() {
        SearchContext ctx = createContext(true);
        ctx.setReplaceWith(replaceField.getText());
        SearchResult result = SearchEngine.replaceAll(textArea, ctx);
        resultLabel.setText(result.getCount() + " replaced");
    }

    private void updateResultLabel(SearchResult result) {
        if (findField.getText().isEmpty()) {
            resultLabel.setText("");
        } else if (!result.wasFound()) {
            resultLabel.setText("No match");
            resultLabel.setForeground(new Color(200, 50, 50));
        } else {
            resultLabel.setText("");
            resultLabel.setForeground(Color.GRAY);
        }
    }
}
